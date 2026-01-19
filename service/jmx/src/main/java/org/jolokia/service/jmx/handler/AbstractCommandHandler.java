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
package org.jolokia.service.jmx.handler;

import java.io.IOException;
import javax.management.JMException;
import javax.management.JMRuntimeException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.service.jmx.api.CommandHandler;

/**
 * Common functionality of all Jolokia command handlers. This includes permission checking.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public abstract class AbstractCommandHandler<R extends JolokiaRequest> implements CommandHandler<R> {

    // Overall context, mostly used for checking restrictions
    protected JolokiaContext context;

    // Provider used for this handler
    protected String pProvider;

    @Override
    public void init(JolokiaContext pContext, String pProvider) {
        context = pContext;
        this.pProvider = pProvider;
    }

    @Override
    public boolean handleAllServersAtOnce(R pRequest) {
        // commands may still change this
        return false;
    }

    @Override
    public Object handleSingleServerRequest(MBeanServerConnection pServer, R pRequest)
            throws IOException, JMException, NotChangedException, BadRequestException, EmptyResponseException {

        // common checks for all the requests
        checkForRestriction(pRequest);
        checkHttpMethod(pRequest);

        return doHandleSingleServerRequest(pServer, pRequest);
    }

    @Override
    public Object handleAllServerRequest(MBeanServerAccess pServerManager, R pRequest, Object pPreviousResult)
            throws IOException, JMException, NotChangedException, BadRequestException, EmptyResponseException {

        // common checks for all the requests
        checkForRestriction(pRequest);
        checkHttpMethod(pRequest);

        return doHandleAllServerRequest(pServerManager, pRequest, pPreviousResult);
    }

    @Override
    public void destroy() throws JMException {
    }

    // ---- Enhanced "handle" methods

    /**
     * Abstract method to be subclassed by a concrete handler for performing the request on a single
     * {@link MBeanServerConnection} after the checks are performed. Subclasses should only implement
     * the command logic.
     *
     * @param server server to try
     * @param request request to process
     * @return the object result from the request
     * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection}
     * @throws JMException JMX checked exception, because most requests are handled by dealing with MBeans
     * @throws JMRuntimeException JMX unchecked exception, because most requests are handled by dealing with MBeans
     * @throws NotChangedException when (according to request parameters) user wants HTTP 304 if nothing has changed
     * @throws BadRequestException because some commands do more user input parsing in addition to what was checked when the {@link JolokiaRequest} was created
     * @throws EmptyResponseException if the response should not be closed (expecting further async/stream data)
     */
    protected abstract Object doHandleSingleServerRequest(MBeanServerConnection server, R request)
            throws IOException, JMException, NotChangedException, BadRequestException, EmptyResponseException;

    /**
     * Default implementation for handling a request for multiple servers at once. A subclass, which returns,
     * {@code true} from {@link #handleAllServersAtOnce(JolokiaRequest)}, needs to override this method. If
     * {@link #handleAllServersAtOnce(JolokiaRequest)} returns {@code false}, the iteration over available
     * {@link MBeanServerConnection connections} is performed by the caller
     * ({@link org.jolokia.server.core.service.request.RequestHandler}).
     *
     * @param serverManager all MBean servers found in this JVM
     * @param request the original request
     * @param pPreviousResult a previous result which for merging requests can be used to merge the data
     * @return the result of the the request.
     * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection}
     * @throws JMException JMX checked exception, because most requests are handled by dealing with MBeans
     * @throws JMRuntimeException JMX unchecked exception, because most requests are handled by dealing with MBeans
     * @throws NotChangedException when (according to request parameters) user wants HTTP 304 if nothing has changed
     * @throws BadRequestException because some commands do more user input parsing in addition to what was checked when the {@link JolokiaRequest} was created
     * @throws EmptyResponseException if the response should not be closed (expecting further async/stream data)
     */
    protected Object doHandleAllServerRequest(MBeanServerAccess serverManager, R request, Object pPreviousResult)
            throws IOException, JMException, NotChangedException, BadRequestException, EmptyResponseException {
        return null;
    }

    // ---- Helper methods to be used by specific command handlers

    /**
     * Check whether there is a restriction on the type to apply. This method should be overwritten
     * by specific handlers if they support a more sophisticated check than only for the type
     *
     * @param pRequest request to check
     */
    protected abstract void checkForRestriction(R pRequest);

    /**
     * Check whether a command of the given type is allowed
     */
    protected void checkType() {
        if (!context.isTypeAllowed(getType())) {
            throw new SecurityException("Command type " + getType() + " not allowed due to policy used");
        }
    }

    /**
     * Check whether the HTTP method with which the request was sent is allowed according to the policy
     * installed
     *
     * @param pRequest request to check
     */
    private void checkHttpMethod(R pRequest) {
        if (!context.isHttpMethodAllowed(pRequest.getHttpMethod())) {
            throw new SecurityException("HTTP method " + pRequest.getHttpMethod().getMethod() +
                    " is not allowed according to the installed security policy");
        }
    }

    /**
     * Checks whether an {@link ObjectName} should be removed (filtered out) from results of {@code list} or
     * {@code search} operations.
     * @param name
     * @return
     */
    protected boolean isObjectNameHidden(ObjectName name) {
        return context.isObjectNameHidden(name);
    }

    /**
     * Check, whether the set of MBeans for any managed MBeanServer has been change since the timestamp
     * provided in the given request
     * @param pServerManager manager for all MBeanServers
     * @param pRequest the request from where to fetch the timestamp
     * @throws NotChangedException if there has been no REGISTER/UNREGISTER notifications in the meantime
     */
    protected void checkForModifiedSince(MBeanServerAccess pServerManager, JolokiaRequest pRequest)
            throws NotChangedException {
        int ifModifiedSince = pRequest.getParameterAsInt(ConfigKey.IF_MODIFIED_SINCE);
        if (!pServerManager.hasMBeansListChangedSince(ifModifiedSince)) {
            throw new NotChangedException(pRequest);
        }
    }

}
