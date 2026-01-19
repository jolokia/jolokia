/*
 * Copyright 2009-2026 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.service.jmx.api;

import java.io.IOException;
import javax.management.JMException;
import javax.management.JMRuntimeException;
import javax.management.MBeanServerConnection;

import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

/**
 * Final abstraction layer for handling Jolokia requests - actual implementation of Jolokia <em>verbs</em>.
 * Methods may throw {@link JMException} or {@link JMRuntimeException} exceptions to be handled down the stack. JMX
 * exceptions are explicit here, because most of the commands are about interacting with MBeans.
 *
 * @author roland
 * @since 09.03.14
 */
public interface CommandHandler<R extends JolokiaRequest> {

    /**
     * The type of request which can be handled
     * @return the request type of this handler
     */
    RequestType getType();

    /**
     * Override this if you want all available MBeanServers to be passed to this command handler, e.g.,
     * to query each server on your own. By default, iteration over the available MBeanServers is done by
     * the {@link org.jolokia.server.core.service.request.RequestHandler} and this command handler
     * is called through {@link #handleSingleServerRequest(MBeanServerConnection, JolokiaRequest)}.
     *
     * @param pRequest request to decide on whether to handle all request at once
     * @return whether you want to have
     * {@link #handleSingleServerRequest(MBeanServerConnection, JolokiaRequest)}
     * (<code>false</code>) or
     * {@link #handleAllServerRequest(MBeanServerAccess, JolokiaRequest, Object)} (<code>true</code>) called.
     */
    boolean handleAllServersAtOnce(R pRequest);

    /**
     * Handle a request for a single {@link MBeanServerConnection}. Coordination of the servers and
     * other request handlers is taken care of externally.
     *
     * @param pServer server to send the request to
     * @param pRequest request to process
     * @return the result of sending a request to the MBean server
     * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection}
     * @throws JMException JMX checked exception, because most requests are handled by dealing with MBeans
     * @throws JMRuntimeException JMX unchecked exception, because most requests are handled by dealing with MBeans
     * @throws NotChangedException when (according to request parameters) user wants HTTP 304 if nothing has changed
     * @throws BadRequestException because some commands do more user input parsing in addition to what was checked when the {@link JolokiaRequest} was created
     * @throws EmptyResponseException if the response should not be closed (expecting further async/stream data)
     */
    Object handleSingleServerRequest(MBeanServerConnection pServer, R pRequest)
            throws IOException, JMException, NotChangedException, BadRequestException, EmptyResponseException;

    /**
     * Override this if you want to iterate over all available servers and potentially use a result from a previous
     * {@link org.jolokia.server.core.service.request.RequestHandler}at once for processing the request
     * (like need for merging info as for a <code>list</code> command). This method
     * is only called when {@link #handleAllServersAtOnce(JolokiaRequest)} returns <code>true</code>
     *
     * @param pServerManager server manager holding all MBeans servers detected
     * @param request request to process
     * @param pPreviousResult a previous result which for merging requests can be used to merge files
     * @return the object found
     * @throws IOException
     * @throws JMException for any JMX exception
     * @throws NotChangedException for Jolokia handling of 304 (not changed)
     * @throws BadRequestException when processing of user input (like path in LIST command) fails
     * @throws EmptyResponseException for Jolokia notification stream handling
     */
    Object handleAllServerRequest(MBeanServerAccess pServerManager, R request, Object pPreviousResult)
            throws IOException, JMException, NotChangedException, BadRequestException, EmptyResponseException;

    /**
     * Lifecycle method in order to initialize the handler
     *
     * @param pContext the jolokia context
     * @param pProvider provider to use for returned names. Handlers can use this  for returning meta
     *               data with the proper provider prefixed.
     */
    void init(JolokiaContext pContext, String pProvider);

   /**
     * Lifecycle method called when agent goes down. Should be overridden by
     * a handler if required.
     */
    void destroy() throws JMException;

}
