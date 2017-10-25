package com.splunk.cloudfwd.test.mock;

import com.splunk.cloudfwd.ConnectionCallbacks;
import com.splunk.cloudfwd.Connections;
import com.splunk.cloudfwd.impl.sim.errorgen.indexer.RollingRestartEndpoints;
import com.splunk.cloudfwd.test.util.AbstractConnectionTest;
import com.splunk.cloudfwd.impl.util.PropertiesFileHelper;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * Created by mhora on 10/4/17.
 */
public class DownIndexersRollingRestartTest extends AbstractConnectionTest {
    protected int getNumEventsToSend() {
        return 1000;
    }

    @Override
    public void setUp() {
        this.testMethodGUID = java.util.UUID.randomUUID().toString();
        this.events = new ArrayList<>();
    }

    @Override
    protected void setProps(PropertiesFileHelper settings) {
        // mocking 4 indexers with 1 channel each
        // although no guarantee which channel goes to which indexer by LoadBalancer
        // but simulate anyway
        settings.setUrlString("https://127.0.0.1:8088,https://127.0.1.1:8088,https://127.0.2.1:8088,https://127.0.3.1:8088");
        settings.setMockHttpClassname("com.splunk.cloudfwd.impl.sim.errorgen.indexer.RollingRestartEndpoints");
        settings.setMaxTotalChannels(4);
        settings.setMaxUnackedEventBatchPerChannel(2);
        settings.setBlockingTimeoutMS(10000);
        RollingRestartEndpoints.init(4, 1);

        settings.setHealthPollMS(1000);
        settings.setAckTimeoutMS(60000);
        settings.setUnresponsiveMS(-1); //no dead channel detection
        settings.setMockForceUrlMapToOne(true);
    }

    // Need to separate this logic out of setUp() so that each Test
    // can use different simulated endpoints
    private void createConnection() {
        this.callbacks = getCallbacks();

        PropertiesFileHelper settings = this.getTestProps();
        this.setProps(settings);
        this.connection = Connections.create((ConnectionCallbacks) callbacks, settings);
        configureConnection(connection);
    }

    @Override
    protected boolean isExpectedSendException(Exception e) {
        return false;
    }

    @Override
    protected boolean shouldSendThrowException() {
        //this.callbacks.latch.countDown(); // allow the test to finish
        return false;
    }

    @Test
    public void sendToIndexersInRollingRestart() throws InterruptedException {
        createConnection();
        super.sendEvents();
    }


}
