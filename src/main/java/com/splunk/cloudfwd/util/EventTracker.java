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

import com.splunk.cloudfwd.EventBatch;

/**
 *
 * @author ghendrey
 */
public interface EventTracker {

  /**
   * An "internal" EventTracker can be canceled an EventBatch without affecting whether or not the EventBatch will timeout.
   * Internal EventTracker like AcknowledgementTracker can cancel an EventBatch in preparation for resending. We need a way
   * to differentiate internal from external trackers so that when we resend an event in DeadChannelDetector, we don't also
   * cancel it's timeout.
   */
  public boolean isInternal();

  /**
   * Causes the EventBatch to stop being tracked (and frees references to the EventBatch e)
   * @param e the EventBatch to cancel tracking of
   */
  public void cancel(EventBatch e);       
  
  
}
