package com.splunk.cloudfwd.test.mock;

import com.splunk.cloudfwd.error.HecChannelDeathException;
import com.splunk.cloudfwd.error.HecConnectionTimeoutException;
import com.splunk.cloudfwd.error.HecMaxRetriesException;
import com.splunk.cloudfwd.test.util.AbstractConnectionTest;
import com.splunk.cloudfwd.test.util.BasicCallbacks;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import com.splunk.cloudfwd.EventBatch;
import com.splunk.cloudfwd.PropertyKeys;

/**
 * Created by meemax by 10/24/2017
 */

public class SlideHighwaterOnFailTest extends AbstractConnectionTest {
    private static final Logger LOG = LoggerFactory.getLogger(SlideHighwaterOnFailTest.class.getName());

    @Override
    protected Properties getProps() {
      Properties props = new Properties();
      //A realistic value of BLOCKING_TIMEOUT_MS would be 1 or more MINUTES, but let's not
      //make this test run too slowly. The point is, we want to SEE the HecConnectionTimeout
      //happen repeatedly, until the message goes through
      props.put(PropertyKeys.BLOCKING_TIMEOUT_MS, "100"); //block for 100 ms before HecConnectionTimeout
      //install an endpoint that takes 10 seconds to return an ack
      props.put(PropertyKeys.MOCK_HTTP_CLASSNAME,
              "com.splunk.cloudfwd.impl.sim.errorgen.slow.SlowEndpoints");
      props.put(PropertyKeys.MAX_UNACKED_EVENT_BATCHES_PER_CHANNEL, "1");
      props.put(PropertyKeys.MAX_TOTAL_CHANNELS, "1");
      props.put(PropertyKeys.ACK_TIMEOUT_MS, "60000"); //we don't want the ack timout kicking in
      props.put(PropertyKeys.ACK_POLL_MS, "250");
      props.put(PropertyKeys.RETRIES, "2");
      props.put(PropertyKeys.UNRESPONSIVE_MS, "100"); //for this test, lets QUICKLY determine the channel is dead
      props.put(PropertyKeys.ENABLE_CHECKPOINTS, "true");
      return props;
    }
    

    @Override
    protected void sendEvents() throws InterruptedException {
      if (getNumEventsToSend() > 1) {
        throw new RuntimeException(
                "This test uses close(), not closeNow(), so don't jam it up with more than one Batch to test on "
                + "a jammed up channel. It will take too long to be practical.");
      }
      LOG.trace(
              "SENDING EVENTS WITH CLASS GUID: " + AbstractConnectionTest.TEST_CLASS_INSTANCE_GUID
              + "And test method GUID " + testMethodGUID);
      try {
        connection.send(nextEvent(1));
      } catch (HecConnectionTimeoutException ex) {
        Assert.fail(
                "The first message should send - but we got an HecConnectionTimeoutException");
      }
      //Thread.sleep(10000);//this is  a total hack - we have a race condition where ChannelDeathDetector is racing against Connection.close()
      connection.close();
      this.callbacks.await(1, TimeUnit.MINUTES);
    }

    @Override
    protected int getNumEventsToSend() {
      return 1;
    }

    @Test
    public void testShouldCheckpointOnFail() throws InterruptedException {
      sendEvents();
    }
    
    @Override
    public BasicCallbacks getCallbacks(){
      return new BasicCallbacks(0){
         @Override
         protected boolean isExpectedFailureType(Exception e){
           return e instanceof HecMaxRetriesException;
         }    
         
         @Override
           public boolean shouldFail(){
              return true;
            }
           
         @Override
          protected boolean isExpectedWarningType(Exception e){
            return e instanceof HecChannelDeathException;
          }

         @Override
          public boolean shouldWarn(){
              return true; //expect HecChannelDeathException warning
          }         

         @Override
         public void acknowledged(EventBatch events) {
           Assert.fail(
               "Acknowledgement should not have been received for event id="
               + events.getId());
         }

         @Override
         public void failed(EventBatch events, Exception ex) {
           exception = ex;
           if(!isExpectedFailureType(ex)){
             LOG.error(ex.getMessage());
           } else {
             LOG.info("Got expected failed exception: " + ex);
           }
           // failed happens first before releasing checkpoint
           // do not latch down so we can check checkpoint
         }
         
         @Override
         public void checkpoint(EventBatch events) {
           LOG.info("FAILURE CHECKPOINT " + events.getId());
           // if we got here, failed has been called and triggered release
           failLatch.countDown();
           latch.countDown();
         }
         
      };
    }
}