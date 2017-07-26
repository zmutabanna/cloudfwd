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

import com.splunk.logging.HttpEventCollectorSender;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator;

/**
 *
 * @author ghendrey
 */
public class ConfiguredObjectFactory {

  public static final String COLLECTOR_URI = "url";
  //public static final String ACK_POST_URL_KEY = "ackUrl";
  public static final String USE_ACKS_KEY = "ackl";
  public static final String TOKEN_KEY = "token";
  public static final String ENDPOINT = "endpoint";
  public static final String EVENT_TYPE = "eventtype";
  public static final String BATCH_COUNT_KEY = "batch_size_count";
  public static final String BATCH_BYTES_KEY = "batch_size_bytes";
  public static final String BATCH_INTERVAL_KEY = "batch_interval";
  public static final String DISABLE_CERT_VALIDATION_KEY = "disableCertificateValidation";
  public static final String SEND_MODE_KEY = "send_mode";
  
  Properties defaultProps = new Properties();

  public ConfiguredObjectFactory(Properties overrides) {
    this(); //setup all defaults by calling SenderFactory() empty constr
    this.defaultProps.putAll(overrides);
  }

  /**
   * create SenderFactory with default properties read from lb.properties file
   */
  public ConfiguredObjectFactory() {
    try {
      InputStream is = getClass().getResourceAsStream("/lb.properties");
      if (null == is) {
        throw new RuntimeException("can't find /lb.properties");
      }
      defaultProps.load(is);
    } catch (IOException ex) {
      Logger.getLogger(ConfiguredObjectFactory.class.getName()).
              log(Level.SEVERE, null, ex);
      throw new RuntimeException(ex.getMessage(), ex);
    }
  }

  public List<URL> getUrls() {
    List<URL> urls = new ArrayList<>();
    String[] splits = defaultProps.getProperty(COLLECTOR_URI).split(",");
    for (String urlString : splits) {
      try {
        URL url = new URL(urlString.trim());
        urls.add(url);
      } catch (MalformedURLException ex) {
        Logger.getLogger(IndexDiscoverer.class.getName()).
                log(Level.SEVERE, "Malformed URL: '" + urlString + "'");
        Logger.getLogger(ConfiguredObjectFactory.class.getName()).log(
                Level.SEVERE, null,
                ex);
      }
    }
    return urls;
  }

  public List<HttpEventCollectorSender> getSenders() {
    List<HttpEventCollectorSender> senders = new ArrayList<>();
    String[] splits = defaultProps.getProperty(COLLECTOR_URI).split(",");
    for (String urlString : splits) {
      try {
        URL url = new URL(urlString);
        senders.add(createSender(url));
      } catch (MalformedURLException ex) {
        Logger.getLogger(IndexDiscoverer.class.getName()).
                log(Level.SEVERE, "Malformed URL: '" + urlString + "'");
        Logger.getLogger(ConfiguredObjectFactory.class.getName()).log(
                Level.SEVERE, null,
                ex);
      }
    }
    return senders;
  }

  public HttpEventCollectorSender createSender(URL url) {
    Properties props = new Properties(defaultProps);
    props.put("url", url.toString());
    return createSender(props);
  }

  private HttpEventCollectorSender createSender(Properties props) {
    String url;
    String token;
    String endpoint;
    String eventtype;
    long batchInterval;
    long batchSize;
    long batchCount;
    boolean ack;
    String ackUrl;
    boolean disableCertificateValidation;
    String sendMode;

    try {
      endpoint = props.getProperty(ENDPOINT, "event").trim();
      if (endpoint.equals("event")) {
        url = props.getProperty(COLLECTOR_URI).trim() + "/services/collector/event";
      } else if (endpoint.equals("raw")) {
        url = props.getProperty(COLLECTOR_URI).trim() + "/services/collector/raw";
      } else {
        throw new IllegalArgumentException(" Invalid event type set in properties: " + endpoint +
                "\nEvent type can only be 'raw' or 'event'");
      }
      eventtype = props.getProperty(EVENT_TYPE, "json").trim();
      if (!(eventtype.equals("json") || eventtype.equals("blob"))) {
        throw new IllegalArgumentException(
                "Invalid setting for " + EVENT_TYPE + ": " + eventtype);
      }
      token = props.getProperty(TOKEN_KEY).trim();
      batchInterval = Long.parseLong(props.getProperty(BATCH_INTERVAL_KEY, "0").trim());
      batchSize = Long.parseLong(props.getProperty(BATCH_BYTES_KEY, "100").trim());
      batchCount = Long.parseLong(props.getProperty(BATCH_COUNT_KEY, "65536").trim()); //64k
      ack = Boolean.parseBoolean(props.getProperty(USE_ACKS_KEY, "true").trim()); //default is use acks
      ackUrl = props.getProperty(COLLECTOR_URI).trim() + "/services/collector/ack";
      disableCertificateValidation = Boolean.parseBoolean(props.getProperty(
              DISABLE_CERT_VALIDATION_KEY, "false").trim());
      sendMode = props.getProperty(SEND_MODE_KEY, "parallel").trim();
      if (!(sendMode.equals("sequential") || sendMode.equals("parallel"))) {
        throw new IllegalArgumentException(
                "Invalid setting for " + SEND_MODE_KEY + ": " + sendMode);
      }
    } catch (Exception e) {
      throw new RuntimeException("problem parsing lb.properties to create HttpEventCollectorSender", e);
    }

    HttpEventCollectorSender sender = new HttpEventCollectorSender(
            url,
            token,
            batchInterval,
            batchCount,
            batchSize,
            "parallel",
            ack,
            ackUrl, new HashMap());
    if (disableCertificateValidation) {
      sender.disableCertificateValidation();
    }
    return sender;
  }

  public HttpEventCollectorSender createSender() {
    return createSender(this.defaultProps);
  }


}
