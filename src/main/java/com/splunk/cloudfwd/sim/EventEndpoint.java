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
package com.splunk.cloudfwd.sim;

import com.splunk.cloudfwd.http.EventBatch;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.params.HttpParams;

/**
 *
 * @author ghendrey
 */
public class EventEndpoint {

  Random rand = new Random(System.currentTimeMillis());
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
  private final AckEndpoint ackEndpoint;

  public EventEndpoint(AckEndpoint ackEndpoint) {
    this.ackEndpoint = ackEndpoint;
  }

  public void post(EventBatch events, FutureCallback<HttpResponse> cb) {
    Runnable respond = () -> {
      cb.completed(new EventPostResponse(
              new AckIdRespEntity(ackEndpoint.nextAckId())
      ));
    };
    //return a single response with a delay uniformly distributed between  [0,300] ms
    executor.schedule(respond, (long) rand.nextInt(300), TimeUnit.MILLISECONDS);
  }

  private static HttpEntity nextAckRespEntity(final int ackId) {

    return new AckIdRespEntity(ackId);
  }

  private static class EventPostResponse extends CannedOKHttpResponse {

    public EventPostResponse(HttpEntity e) {
      super(e);
    }
  }

  private static class AckIdRespEntity extends CannedEntity {

    public AckIdRespEntity(int ackId) {
      super("{\"ackId\":" + ackId + "}");
    }
  }

}
