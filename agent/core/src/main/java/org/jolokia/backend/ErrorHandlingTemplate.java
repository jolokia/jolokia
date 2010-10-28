/*
 * Copyright 2009-2010 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.backend;

import org.jolokia.JmxRequest;
import org.jolokia.LogHandler;
import org.json.simple.JSONObject;

import javax.management.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * A template which adds proper error conversions
 *
 * @author roland
 * @since 28.10.10
 */
public class ErrorHandlingTemplate {

    public JSONObject executeRequest(Callback callback,JmxRequest pRequest) {
        try {
            return callback.execute(pRequest);
        } catch (ReflectionException e) {
            return getErrorJSON(404,e);
        } catch (InstanceNotFoundException e) {
            return getErrorJSON(404,e);
        } catch (MBeanException e) {
            return getErrorJSON(500,e);
        } catch (AttributeNotFoundException e) {
            return getErrorJSON(404,e);
        } catch (UnsupportedOperationException e) {
            return getErrorJSON(500,e);
        } catch (IOException e) {
            return getErrorJSON(500,e);
        }
    }

        /**
     * Utility method for handling single runtime exceptions and errors.
     *
     * @param pThrowable exception to handle
     * @return its JSON representation
     */
    public JSONObject handleThrowable(Throwable pThrowable) {
        JSONObject json;
        Throwable exp = pThrowable;
        if (exp instanceof RuntimeMBeanException) {
            // Unwrap
            exp = exp.getCause();
        }
        if (exp instanceof IllegalArgumentException) {
            json = getErrorJSON(400,exp);
        } else if (exp instanceof IllegalStateException) {
            json = getErrorJSON(500,exp);
        } else if (exp instanceof SecurityException) {
            // Wipe out stacktrace
            json = getErrorJSON(403,new Exception(exp.getMessage()));
        } else {
            json = getErrorJSON(500,exp);
        }
        return json;
    }

    /**
     * Get the JSON representation for a an exception
     *
     * @param pErrorCode the HTTP error code to return
     * @param pExp the exception or error occured
     * @return the json representation
     */
    public JSONObject getErrorJSON(int pErrorCode, Throwable pExp) {
        JSONObject jsonObject = new JSONObject();
        Throwable unwrapped = unwrapException(pExp);
        jsonObject.put("status",pErrorCode);
        jsonObject.put("error",getExceptionMessage(unwrapped));
        StringWriter writer = new StringWriter();
        pExp.printStackTrace(new PrintWriter(writer));
        jsonObject.put("stacktrace",writer.toString());
        return jsonObject;
    }

    // Extract class and exception message for an error message
    private String getExceptionMessage(Throwable pException) {
        String message = pException.getLocalizedMessage();
        return pException.getClass().getName() + (message != null ? " : " + message : "");
    }

    // Unwrap an exception to get to the 'real' exception
    // stripping any boilerplate exceptions
    private Throwable unwrapException(Throwable pExp) {
        if (pExp instanceof MBeanException) {
            return ((MBeanException) pExp).getTargetException();
        }
        return pExp;
    }


    // Interface to implement for getting proper error handling
    public interface Callback {
        JSONObject execute(JmxRequest pRequest) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException;
    }
}
