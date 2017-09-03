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

import com.splunk.cloudfwd.http.HecIOManager;
import com.splunk.cloudfwd.http.HttpSender;
import java.util.logging.Logger;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import com.splunk.cloudfwd.http.HttpPostable;

/**
 * EventBatch can be used if the caller wants a high degree of control over
 * which Events will get sent to HEC in a single HTTP post. Most of the time,
 * there is no need to use an EventBatch, as it is used inside the connection to
 * gather events together when buffering is enabled on the Connection.
 *
 * @author ghendrey
 */
public class EventBatch implements HttpPostable {

  private static final Logger LOG = Logger.getLogger(EventBatch.class.getName());
  private Comparable id; //will be set to the id of the last (most recent) Event added to the batch
  private Long ackId; //Will be null until we receive ackId for this batch from HEC
  //private final List<Event> eventsBatch = new ArrayList();
  //private HttpSender sender;
  private final StringBuilder stringBuilder = new StringBuilder();
  private boolean flushed = false;
  private boolean acknowledged;
  private final long creationTime = System.currentTimeMillis();
  private int numEvents;

  public EventBatch() {

  }

  public synchronized void prepareToResend() {
    this.flushed = false;
    //this.sender = null;
    this.acknowledged = false;
  }

  public boolean isTimedOut(long timeout) {
    long flightTime = System.currentTimeMillis() - creationTime;
    System.out.println("Flight time " + flightTime);
    return flightTime >= timeout;
  }

  public synchronized void add(Event event) {
    if (flushed) {
      throw new IllegalStateException(
              "Events cannot be added to a flushed EventBatch");
    }

    this.stringBuilder.append(event);
    this.id = event.getId();
  }

  protected synchronized boolean isFlushable(int charBufferLen) {
    //technically the serialized size that we compate to maxEventsBatchSize should take into account
    //the character encoding. it's very difficult to compute statically. We use the stringBuilder length
    //which is a character count. Will be same as serialized size only for single-byte encodings like
    //US-ASCII of ISO-8859-1    
    return !flushed && (stringBuilder.length() > charBufferLen);
  }

  @Override
  public synchronized void post(HecIOManager ioManager) {
    if (!this.flushed && this.stringBuilder.length() > 0) {
      //endpoints are either real (via the Sender) or simulated 
      ioManager.postEvents(this);
      flushed = true;
    }
  }

  @Override
  public HttpEntity getEntity() {
    StringEntity e = new StringEntity(stringBuilder.toString(), "UTF-8");
    e.setContentType("application/json; profile=urn:splunk:event:1.0; charset=utf-8"); //FIX ME application/json not all the time
    return e;
  }

  @Override
  public String toString() {
    return this.stringBuilder.toString();
  }

  public int getNumEvents() {
    return numEvents;
  }

  public int getCharCount() {
    return stringBuilder.length();
  }

  /**
   * @return the id
   */
  public Comparable getId() {
    return id;
  }

  /**
   * @return the ackId
   */
  public Long getAckId() {
    return ackId;
  }

  /**
   * @param ackId the ackId to set
   */
  public void setAckId(Long ackId) {
    this.ackId = ackId;
  }

  /*
  public void setSender(HttpSender sender) {
    if (null != this.sender) {
      String msg = "attempt to change the value of sender. Channel was " + this.sender.
              getChannel() + ", and attempt to change to " + sender.getChannel();
      LOG.severe(msg);
      throw new IllegalStateException(msg);
    }
    this.sender = sender;
  }*/

  /**
   * @return the acknowledged
   */
  public boolean isAcknowledged() {
    return acknowledged;
  }

  /**
   * @param acknowledged the acknowledged to set
   */
  public void setAcknowledged(boolean acknowledged) {
    this.acknowledged = acknowledged;
  }

  /**
   * @return the flushed
   */
  @Override
  public boolean isFlushed() {
    return flushed;
  }

//  /**
//   * @return the sender
//   */
//  public HttpSender getSender() {
//    return sender;
//  }
//
//  /**
//   * @param sender the sender to set
//   */
//  @Override
//  public void setSender(HttpSender sender) {
//    this.sender = sender;
//  }


}
