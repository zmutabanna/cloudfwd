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
package com.splunk.cloudfwd;

import com.splunk.logging.AckLifecycleState;
import com.splunk.logging.ChannelMetrics;
import com.splunk.logging.EventBatch;
import com.splunk.logging.HttpEventCollectorSender;
import java.io.Closeable;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ghendrey
 */
public class LoggingChannel implements Closeable, Observer {

  private static final Logger LOG = Logger.getLogger(LoggingChannel.class.
          getName());
  private final static long SEND_TIMEOUT = 60 * 1000; //FIXME TODO make configurable
  private final HttpEventCollectorSender sender;
  private static final int FULL = 500; //FIXME TODO set to reasonable value, configurable?
  private static final ScheduledExecutorService reaperScheduler = Executors.
          newScheduledThreadPool(1); //for scheduling self-removal/shutdown
  private static final long LIFESPAN = 5*60*1000; //5 min lifespan
  private volatile boolean closed;
  private volatile boolean quiesced;
  private final LoadBalancer loadBalancer;
  private AtomicInteger unackedCount = new AtomicInteger(0);

  public LoggingChannel(LoadBalancer b, HttpEventCollectorSender sender) {
    this.loadBalancer = b;
    this.sender = sender;
    getChannelMetrics().addObserver(this);
    //schedule the channel to be automatically quiesced at LIFESPAN, and closed and replaced when empty 
    reaperScheduler.schedule(() -> {
      closeAndReplace();
    }, LIFESPAN, TimeUnit.MILLISECONDS);

  }

  public synchronized boolean send(EventBatch events) throws TimeoutException {
    if (!isAvailable()) {
      return false;
    }
    if (this.closed) {
      LOG.severe("Attempt to send to closed channel");
      throw new IllegalStateException(
              "Attempt to send to quiesced/closed channel");
    }
    if (this.quiesced) {
      LOG.
              info("Send to quiesced channel (this should happen from time to time)");
    }
    System.out.println("Sending to channel: " + sender.getChannel());
    if (unackedCount.get() == FULL) {
      long start = System.currentTimeMillis();
      while (true) {
        try {
          System.out.println("---BLOCKING---");
          wait(SEND_TIMEOUT);
        } catch (InterruptedException ex) {
          Logger.getLogger(LoggingChannel.class.getName()).
                  log(Level.SEVERE, null, ex);
        }
        if (System.currentTimeMillis() - start > SEND_TIMEOUT) {
          System.out.println("TIMEOUT EXCEEDED");
          throw new TimeoutException("Send timeout exceeded.");
        } else {
          System.out.println("---UNBLOCKED--");
          break;
        }
      }
    }
    //essentially this is a "double check" since this channel could be closed while this
    //method was blocked. It happens.
    if (quiesced || closed) {
      return false;
    }
    int count = unackedCount.incrementAndGet();
    System.out.println("channel=" + getChannelId() + " unack-count=" + count);
    sender.sendBatch(events);
    return true;
  }

  @Override
  public synchronized void update(Observable o, Object arg) {
    AckLifecycleState s = (AckLifecycleState) arg;
    if (s.getCurrentState() == AckLifecycleState.State.ACK_POLL_OK) {
      int count = unackedCount.decrementAndGet();
      System.out.
              println("channel=" + getChannelId() + " unacked-count-post-decr=" + count + " seqno=" + s.
                      getEvents().getId() + " ackid= " + s.getEvents().
                      getAckId());
      if (count < 0) {
        String msg = "unacked count is illegal negative value: " + count + " on channel " + getChannelId();
        LOG.severe(msg);
        throw new RuntimeException(msg);
      } else if (count == 0) { //we only need to notify when we drop down from FULL. Tighter than syncing this whole method 
        if (quiesced) {
          try {
            close();
          } catch (IllegalStateException ex) {
            LOG.warning(
                    "unable to close channel " + getChannelId() + ": " + ex.getMessage());
          }
        }
      }

      System.out.println("UNBLOCK");
      notifyAll();      
    }
  }
  
  synchronized void closeAndReplace(){
    this.loadBalancer.addChannelFromRandomlyChosenHost(); //add a replacement
    quiesce(); //drain in-flight packets, and close+remove when empty 
  }

  /**
   * Removes channel from load balancer. Remaining data will be sent.
   *
   */
  protected synchronized void quiesce() {
    LOG.log(Level.INFO, "Quiescing channel: {0}", getChannelId());
    quiesced = true;   
  }

  @Override
  public synchronized void close() {
    if (closed) {
      LOG.severe("LoggingChannel already closed.");
      throw new IllegalStateException("LoggingChannel already closed.");
    }
    LOG.log(Level.INFO, "CLOSE {0}", getChannelId());
    if (!isEmpty()) {
      quiesce(); //this essentially tells the channel to close after it is empty
      return;
    }

    this.closed = true;
    this.sender.close();
    this.loadBalancer.removeChannel(getChannelId());
    System.out.println("TRYING TO UNBLOCK");
    notifyAll();    
    getChannelMetrics().deleteObserver(this);
    getChannelMetrics().deleteObserver(this.loadBalancer.getConnectionState());
  }

 
   
  /**
   * Returns true if this channels has no unacknowledged EventBatch
   *
   * @return true if ackwindow is empty
   */
  protected boolean isEmpty() {
    return this.unackedCount.get() == 0;
  }

  int getUnackedCount(){
    return this.unackedCount.get();
  }
   
  /**
   * @return the metrics
   */
  public final ChannelMetrics getChannelMetrics() {
    return sender.getChannelMetrics();
  }

  boolean isAvailable() {
    ChannelMetrics metrics = sender.getChannelMetrics();
    return !quiesced && !closed && metrics.getUnacknowledgedCount() < FULL; //FIXME TODO make configurable   
  }

  /*
  @Override
  public int compareTo(Object other) {
    if (null == other || !((LoggingChannel) other).isAvailable()) {
      return 1;
    }
    if (this.equals(other)) {
      return 0;
    }
    long myBirth = sender.getChannelMetrics().getOldestUnackedBirthtime();
    long otherBirth = ((LoggingChannel) other).getChannelMetrics().
            getOldestUnackedBirthtime();
    return (int) (myBirth - otherBirth); //channel with youngest unacked message is preferred
  }

*/
  @Override
  public int hashCode() {
    int hash = 3;
    hash = 19 * hash + Objects.hashCode(this.sender.getChannel());
    return hash;
  }

  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final LoggingChannel other = (LoggingChannel) obj;
    return Objects.equals(this.sender.getChannel(), other.sender.getChannel());
  }

  String getChannelId() {
    return sender.getChannel();
  }

}