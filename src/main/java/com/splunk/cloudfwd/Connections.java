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

import com.splunk.cloudfwd.impl.ConnectionImpl;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Factory for getting a Connection.
 *
 * @author ghendrey
 */
public class Connections {

    private Connections() {

    }

    public static Connection create(ConnectionCallbacks c) {
        return create(c, new Properties());
    }

    /**
     * When creating a connection, an attempt is made to check the server-side configurations of every channel managed by the 
     * load balancer. If any of the channels finds misconfigurations, such as HEC acknowldegements disabled, or invalid HEC
     * token, then an exception is thrown from Connections.create. This is 'fail fast' behavior designed to quickly expose 
     * serverside configuration issues.
     * @param c The ConnectionCallbacks 
     * @param p Properties that customize the Connection
     * @return
     */
    public static Connection create(ConnectionCallbacks c, Properties p) {
        return new ConnectionImpl(c, p);
    }
    
    /**
     * Creates a Connection with DefaultConnectionCallbacks
     * @param p Properties that customize the Connection
     * @return
     */
    public static Connection create(Properties p) {
        return new ConnectionImpl(new DefaultConnectionCallbacks(), p);
    }    
    
     /**
     * Creates a Connection with DefaultConnectionCallbacks and default settings loaded from cloudfwd.properties
     * @param pProperties that customize the Connection
     * @return
     */
    public static Connection create() throws IOException {
        Properties p = new Properties();
        try(InputStream is = Connection.class.getResourceAsStream("cloudfwd.properties");){
            p.load(is);
            return new ConnectionImpl(new DefaultConnectionCallbacks(), p);
        }
    }    
    
   
}
