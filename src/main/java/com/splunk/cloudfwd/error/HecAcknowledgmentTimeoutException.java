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
package com.splunk.cloudfwd.error;

/**
 * This exception will be passed to ConnectionCallbacks.failed when an EventBatch has timed out.
 * @author ghendrey
 */
public class HecAcknowledgmentTimeoutException extends RuntimeException{

  public HecAcknowledgmentTimeoutException(String message) {
    super(message);
  }
  
}
