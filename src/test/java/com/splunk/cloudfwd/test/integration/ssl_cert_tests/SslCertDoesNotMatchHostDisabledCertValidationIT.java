package com.splunk.cloudfwd.test.integration.ssl_cert_tests;/*
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

import com.splunk.cloudfwd.EventBatch;
import com.splunk.cloudfwd.PropertyKeys;
import com.splunk.cloudfwd.error.HecConnectionTimeoutException;
import com.splunk.cloudfwd.test.util.AbstractConnectionTest;
import com.splunk.cloudfwd.test.util.BasicCallbacks;
import org.junit.Test;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.splunk.cloudfwd.PropertyKeys.*;

/**
 * This test attempts to connect to ELB configured with a splunkcloud.com cert by 
 * AWS generated DNS name in amazonaws.com. Java ssl framework should fail to 
 * validate SSL connection, as provided hostname 
 * (kinesis1-indexers-229328170.us-east-1.elb.amazonaws.com) is not in the same 
 * domain as splunkcloud.com SSL certificate.
 * 
 * @author ssergeev
 */
public class SslCertDoesNotMatchHostDisabledCertValidationIT extends AbstractConnectionTest {
  
  @Test
  /**
   * This test makes sure that send doesn't throw an exception if Cert Validation is disabled
   */
  public void sendEventsSuccessfully() throws InterruptedException, HecConnectionTimeoutException {
    super.sendEvents(true, true);
  }
  
  @Override
  protected Properties getProps() {
    Properties props = new Properties();
    props.put(COLLECTOR_URI, "https://kinesis1-indexers-229328170.us-east-1.elb.amazonaws.com:443");
    props.put(TOKEN, "DB22D948-5A1D-4E73-8626-0AB3143BEE47");
    props.put(DISABLE_CERT_VALIDATION, "true");
    props.put(MOCK_HTTP_KEY, "false");
    props.put(PropertyKeys.EVENT_BATCH_SIZE, "0");
    return props;
  }
  
  @Override
  protected int getNumEventsToSend() {
    return 1;
  }
  
  @Override
  protected BasicCallbacks getCallbacks() {
    return new BasicCallbacks(getNumEventsToSend()) {
      
      @Override
      public void failed(EventBatch events, Exception ex) {
        // Expected to get a failed exception on connection.closeNow(), 
        // as we do not wait for the event to ack
        LOG.debug("expected failed callback: events: " + events + ", ex: " + ex);
      }
      @Override
      public void await(long timeout, TimeUnit u) throws InterruptedException {
        // don't need to wait for anything since we don't get a failed callback
      }
    };
  }
  
}
