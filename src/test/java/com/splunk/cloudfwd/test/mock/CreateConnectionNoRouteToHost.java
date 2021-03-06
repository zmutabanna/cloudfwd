package com.splunk.cloudfwd.test.mock;

import com.splunk.cloudfwd.LifecycleEvent;
import com.splunk.cloudfwd.PropertyKeys;
import com.splunk.cloudfwd.error.HecMaxRetriesException;
import com.splunk.cloudfwd.error.HecServerErrorResponseException;
import com.splunk.cloudfwd.test.integration.AbstractReconciliationTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * Created by eprokop on 10/9/17.
 */
public class CreateConnectionNoRouteToHost extends AbstractReconciliationTest {
    // Scenario: Hostname can be resolved but not reached
    // Expected behavior: Connection fails to instantiate and throws proper exception
    @Test
    public void createConnectionUnreachableHost() {
    }

    @Override
    protected Properties getProps() {
        Properties p = super.getProps();
        p.put(PropertyKeys.MOCK_HTTP_KEY, "true");
        p.put(PropertyKeys.MOCK_HTTP_CLASSNAME, "com.splunk.cloudfwd.impl.sim.errorgen.splunkcheckfailure.NoRouteToHostEndpoints");
        return p;
    }

    @Override
    protected int getNumEventsToSend() {
        return 0;
    }

    @Override
    protected boolean connectionInstantiationShouldFail() {
        return true;
    }

    @Override
    protected boolean isExpectedConnInstantiationException(Exception e) {
        Assert.assertTrue("Exception should be the correct type.", e instanceof HecMaxRetriesException);
        return true;
    }
}
