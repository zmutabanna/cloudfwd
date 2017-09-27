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

import com.splunk.cloudfwd.LifecycleEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
*
 * @author eprokop
 */


public class HecServerErrorResponseException extends Exception {
    private static Set<Integer> nonRecoverableErrors = new HashSet<>(Arrays.asList(3, 10, 11));
    private static Set<Integer> recoverableConfigErrors = new HashSet<>(Arrays.asList(1, 2, 4, 7, 14));
    private static Set<Integer> recoverableDataErrors = new HashSet<>(Arrays.asList(5, 6, 12, 13));
    private static Set<Integer> recoverableServerErrors = new HashSet<>(Arrays.asList(8, 9));
    
    private String text;
    private int code;
    private String serverReply;
    private LifecycleEvent.Type type;
    private String url;
    private Type errorType;
    private String context;




    public enum Type { NON_RECOVERABLE_ERROR, RECOVERABLE_CONFIG_ERROR, RECOVERABLE_DATA_ERROR, RECOVERABLE_SERVER_ERROR };


    public HecServerErrorResponseException(String text, int hecCode, String serverReply, LifecycleEvent.Type type, String url) {
        this.text = text;
        this.code = hecCode;
        this.serverReply = serverReply;
        this.type = type;        
        this.url = url;
        setErrorType(hecCode);
    }
    
    @Override
    public String toString() {
        return "HecServerErrorResponseException{" + "text=" + text + ", code=" + code + ", serverReply=" 
                + serverReply + ", type=" + type + ", url=" + url + ", errorType=" + errorType + ", context=" + context + '}';
    }
    
    
    /**
     * @return the serverReply
     */
    public String getServerReply() {
        return serverReply;
    }

    /**
     * @return the type
     */
    public LifecycleEvent.Type getType() {
        return type;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return toString();
    }

    /**
     * @return the context
     */
    public String getContext() {
        return context;
    }

    /**
     * @param context the context to set
     */
    public void setContext(String context) {
        this.context = context;
    }
    

    

    public void setCode(int code) {
        this.code = code;
    }

    public void setUrl(String url) {
        this.url = url;
    }


    public int getCode() {
        return code;
    }

    public String getUrl() {
        return url;
    }

    public Type getErrorType() { return errorType; };

    private void setErrorType(Integer hecCode) {
        if (nonRecoverableErrors.contains(hecCode)) {
            errorType = Type.NON_RECOVERABLE_ERROR;
        } else if (recoverableConfigErrors.contains(hecCode)) {
            errorType =  Type.RECOVERABLE_CONFIG_ERROR;
        } else if (recoverableDataErrors.contains(hecCode)) {
            errorType =  Type.RECOVERABLE_DATA_ERROR;
        } else if (recoverableServerErrors.contains(hecCode)) {
            errorType =  Type.RECOVERABLE_SERVER_ERROR;
        }
    }
}