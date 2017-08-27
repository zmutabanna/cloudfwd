/*
 * Copyright 2017 Splunk, Inc..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.splunk.cloudfwd.util;

import com.splunk.cloudfwd.Connection;
import com.splunk.cloudfwd.EventBatch;
import com.splunk.cloudfwd.http.HttpSender;
import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ghendrey
 */
public class LoadBalancer implements Closeable {

  private int channelsPerDestination = 4;
  private static final Logger LOG = Logger.getLogger(LoadBalancer.class.
          getName());
  private final Map<String, HecChannel> channels = new ConcurrentHashMap<>();
  private PropertiesFileHelper propertiesFileHelper;
  private final CheckpointManager checkpointManager; //consolidate metrics across all channels
  private final IndexDiscoverer discoverer;
  private final IndexDiscoveryScheduler discoveryScheduler = new IndexDiscoveryScheduler();
  private int robin; //incremented (mod channels) to perform round robin
  private final Connection connection;
  private boolean closed;

  /* *********************** METRICS ************************ */
  private AtomicInteger postCount = new AtomicInteger(0);
  /* *********************** /METRICS ************************ */

  public LoadBalancer(Connection c) {
    this(c, new Properties());
  }

  public LoadBalancer(Connection c, Properties p) {
    this.connection = c;
    this.propertiesFileHelper = new PropertiesFileHelper(p);
    this.channelsPerDestination = this.propertiesFileHelper.
            getChannelsPerDestination();
    this.discoverer = new IndexDiscoverer(propertiesFileHelper);
    this.checkpointManager = new CheckpointManager(c);
    //this.discoverer.addObserver(this);
  }

  private void updateChannels(IndexDiscoverer.Change change) {
    System.out.println(change);
  }

  public synchronized void sendBatch(EventBatch events) {
    if (null == this.connection.getCallbacks()) {
      throw new IllegalStateException(
              "Connection FutureCallback has not been set.");
    }
    this.checkpointManager.registerInFlightEvents(events);
    if (channels.isEmpty()) {
      createChannels(discoverer.getAddrs());
    }
    if (events.getConnectionPostCount() == null) {
      events.setConnectionPostCount(postCount.incrementAndGet());
    }
    sendRoundRobin(events);
  }

  @Override
  public synchronized void close() {
    this.discoveryScheduler.stop();
    for (HecChannel c : this.channels.values()) {
      c.close();
    }
    this.closed = true;
  }

  public synchronized void closeNow() {
    this.discoveryScheduler.stop();
    //synchronized (channels) {
    for (HecChannel c : this.channels.values()) {
      c.forceClose();
    }
    //}
    this.closed = true;
  }

  public CheckpointManager getCheckpointManager() {
    return this.checkpointManager;
  }

  private synchronized void createChannels(List<InetSocketAddress> addrs) {
    for (InetSocketAddress s : addrs) {
      //add multiple channels for each InetSocketAddress
      for (int i = 0; i < channelsPerDestination; i++) {
        addChannel(s, false);
      }
      break;
    }
  }

  void addChannelFromRandomlyChosenHost() {
    InetSocketAddress addr = discoverer.randomlyChooseAddr();
    LOG.log(Level.INFO, "Adding channel to {0}", addr);
    addChannel(addr, true); //this will force the channel to be added, even if we are ac MAX_TOTAL_CHANNELS
  }

  //this method must not be synchronized it will cause deadlock
  private void addChannel(InetSocketAddress s, boolean force) {
    //sometimes we need to force add a channel. Specifically, when we are replacing a reaped channel
    //we must add a new one, before we remove the old one. If we did not have the force
    //argument, adding the new channel would get ignored if MAX_TOTAL_CHANNELS was set to 1,
    //and then the to-be-reaped channel would also be removed, leaving no channels, and
    //send will be stuck in a spin loop with no channels to send to
    if (!force && channels.size() >= propertiesFileHelper.getMaxTotalChannels()) {
      LOG.info(
              "Can't add channel (" + PropertiesFileHelper.MAX_TOTAL_CHANNELS + " set to " + propertiesFileHelper.
              getMaxTotalChannels() + ")");
      return;
    }
    URL url;
    String host;
    try {
      //URLS for channel must be based on IP address not hostname since we
      //have many-to-one relationship between IP address and hostname via DNS records
      url = new URL("https://" + s.getAddress().getHostAddress() + ":" + s.getPort());
      //We should provide a hostname for http client, so it can properly set Host header
      //this host is required for many proxy server and virtual servers implementations
      //https://tools.ietf.org/html/rfc7230#section-5.4
      host = s.getHostName() + ":" + s.getPort();

      HttpSender sender = this.propertiesFileHelper.
              createSender(url, host);

      HecChannel channel = new HecChannel(this, sender, this.connection);
      channel.getChannelMetrics().addObserver(this.checkpointManager);
      sender.setChannel(channel);
      LOG.log(Level.INFO, "Adding channel {0}", channel.getChannelId());
      channels.put(channel.getChannelId(), channel);
      //consolidated metrics (i.e. across all channels) are maintained in the checkpointManager

    } catch (MalformedURLException ex) {
      Logger.getLogger(LoadBalancer.class.getName()).log(Level.SEVERE, null,
              ex);
    }
  }

  //also must not be synchronized
  void removeChannel(String channelId, boolean force) {
    HecChannel c = this.channels.remove(channelId);
    /*
    if (c == null) {
      LOG.severe("attempt to remove unknown channel: " + channelId);
      throw new RuntimeException(
              "attempt to remove unknown channel: " + channelId);
    }
     */
    if (!force && !c.isEmpty()) {
      LOG.severe(
              "Attempt to remove non-empty channel: " + channelId + " containing " + c.
              getUnackedCount() + " unacked payloads");
      System.out.println(this.checkpointManager);
      throw new RuntimeException(
              "Attempt to remove non-empty channel: " + channelId + " containing " + c.
              getUnackedCount() + " unacked payloads");

    }

  }

  private synchronized void sendRoundRobin(EventBatch events) {
    sendRoundRobin(events, false);
  }

  synchronized void sendRoundRobin(EventBatch events, boolean forced) {
    try {
      if (channels.isEmpty()) {
        throw new IllegalStateException(
                "attempt to sendRoundRobin but no channel available.");
      }
      HecChannel tryMe = null;
      int tryCount = 0;
      //round robin until either A) we find an available channel
      long start = System.currentTimeMillis();
      int spinCount = 0;
      int yieldInterval = propertiesFileHelper.getMaxTotalChannels();
      while (!closed || forced) {
        //note: the channelsSnapshot must be refreshed each time through this loop
        //or newly added channels won't be seen, and eventually you will just have a list
        //consisting of closed channels. Also, it must be a snapshot, not use the live
        //list of channels. Because the channelIdx could wind up pointing past the end
        //of the live list, due to fact that this.removeChannel is not synchronized (and
        //must not be do avoid deadlocks).
        List<HecChannel> channelsSnapshot = new ArrayList<>(this.channels.
                values());
        if (channelsSnapshot.isEmpty()) {
          continue; //keep going until a channel is added
        }
        int channelIdx = this.robin++ % channelsSnapshot.size(); //increment modulo number of channels
        tryMe = channelsSnapshot.get(channelIdx);
        if (tryMe.send(events)) {
          System.out.println(
                  "sent EventBatch id=" + events.getId() + " on " + tryMe);
          break;
        }
        if (++spinCount % yieldInterval == 0) {
//          LOG.warning("No available channel to send on. Waiting...");
          Thread.yield(); //we are spinning waiting for available channel. Should yield CPU.
          Thread.sleep(100); //avoid hard-spin-lock looking for available channel
        }
        if (System.currentTimeMillis() - start > this.getConnection().
                getSendTimeout()) {

          System.out.println("TIMEOUT EXCEEDED");
          throw new TimeoutException("Send timeout exceeded.");
        }
      }
    } catch (TimeoutException e) {
      LOG.log(Level.SEVERE, e.getMessage(), e);
      this.getConnection().getCallbacks().failed(events, e);
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Exception caught in sendRountRobin: {0}", e.
              getMessage());
      throw new RuntimeException(e.getMessage(), e);
    }

  }

  /**
   * @return the channelsPerDestination
   */
  public int getChannelsPerDestination() {
    return channelsPerDestination;
  }

  /**
   * @param channelsPerDestination the channelsPerDestination to set
   */
  public void setChannelsPerDestination(int channelsPerDestination) {
    this.channelsPerDestination = channelsPerDestination;
  }

  /**
   * @return the connection
   */
  public Connection getConnection() {
    return connection;
  }

  /**
   * @return the propertiesFileHelper
   */
  public PropertiesFileHelper getPropertiesFileHelper() {
    return propertiesFileHelper;
  }

}