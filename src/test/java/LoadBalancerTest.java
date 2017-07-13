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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author ghendrey
 */
public class LoadBalancerTest {
  
  public LoadBalancerTest() {
  }
  
  @BeforeClass
  public static void setUpClass() {
  }
  
  @AfterClass
  public static void tearDownClass() {
  }
  
  @Before
  public void setUp() {
  }
  
  @After
  public void tearDown() {
  }


  @Test
  public void hello() throws InterruptedException, TimeoutException {
    
    try (com.splunk.cloudfwd.Connection c = new com.splunk.cloudfwd.Connection()){
      c.send("HELLO CHANNEL!");
    }
    
  }
  public static void main(String[] args) throws InterruptedException, TimeoutException{
    LoadBalancerTest lbt = new LoadBalancerTest();
    lbt.hello();
  }
  
}
