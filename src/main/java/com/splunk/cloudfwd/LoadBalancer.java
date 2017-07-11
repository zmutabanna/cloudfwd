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

import com.splunk.logging.HttpEventCollectorSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ghendrey
 */
public class LoadBalancer implements Observer {

  private final static long TIMEOUT = 60 * 1000; //FIXME TODO make configurable
  /* All channels that can be used to send messages. */
  private final List<LoggingChannel> channels = new ArrayList<>();
  /* The best channel to send a message to, according to the metrics */
  private LoggingChannel bestChannel;
  private final AtomicBoolean available = new AtomicBoolean(true);
  /* Registers a new channel with the load balancer.*/
  public synchronized void addChannel(LoggingChannel channel) {
    /* Remember this channel. */
    channels.add(channel);
    /* Be notified when the metrics of this channel changes. */
    if(channel.betterThan(bestChannel)){
      bestChannel = channel;
    }
   }

  @Override
  /* Called when metric of a channel changes. */
  public void update(Observable o, Object arg) {
    HttpEventCollectorSender sender = (HttpEventCollectorSender) arg;
    LoggingChannel channel = new LoggingChannel(sender);
    synchronized (this) {
      /* If the channel who’s metrics changed is better than the current bestChannel, use that one instead.*/
      if (channel.betterThan(bestChannel)) {
        bestChannel = channel;
      }
      //if best channel is not available set the AtomicBoolean to false
      //which will cause send() to block until send times out, or until this
      //update method is called again, and unblocks send() because the best
      //channel became available
      this.available.set(bestChannel.isAvalialable());
      if (this.available.get()) {
        notifyAll(); //unblock send()
      }

    }

  }

  public void send(String msg) throws TimeoutException {
    synchronized (channels) {
      if (channels.isEmpty()) {
        SenderFactory sFact = new SenderFactory();
        HttpEventCollectorSender sender = sFact.createSender();
        LoggingChannel lc = new LoggingChannel(sender);
        addChannel(lc);
      }
    }
    while (!available.get()) {
      try {
        long start = System.currentTimeMillis();
        synchronized (this) {
          wait(TIMEOUT);
        }
        if (start - System.currentTimeMillis() <= TIMEOUT) {
          break; //got an available channel
        } else {
          throw new TimeoutException(
                  "Unable to find available HEC logging channel in " + TIMEOUT + " ms.");
        }
      } catch (InterruptedException ex) {
        Logger.getLogger(LoadBalancer.class.getName()).log(Level.SEVERE, null,
                ex);
      }
    }//end while
    bestChannel.send(msg);
  }

}
