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
package com.splunk.cloudfwd.impl.sim;

import com.splunk.cloudfwd.impl.http.httpascync.HttpCallbacksAbstract;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;

/**
 *
 * @author ghendrey
 */
public class HealthEndpoint implements Endpoint{

  public void pollHealth(FutureCallback<HttpResponse> cb) {
    ((HttpCallbacksAbstract)cb).completed("If we care about the actual conent, this will break something.", 200);
  }

  @Override
  public void close() {
    //no-op for now
  }

  @Override
  public void start() {
    //no-op
  }
  
}
