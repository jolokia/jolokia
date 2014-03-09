package org.jolokia.service.jmx.handler;

import java.io.IOException;

import javax.management.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.service.jmx.api.CommandHandler;

/*
 * Copyright 2009-2013 Roland Huss
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


/**
 * A handler for dealing with a certain Jolokia command
 *
 * @author roland
 * @since Jun 12, 2009
 */
public abstract class AbstractCommandHandler<R extends JolokiaRequest> implements CommandHandler<R> {

    // Overall context, mostly used for checking restrictions
    protected JolokiaContext context;

    // Realm used for this handler
    protected String realm;

    public void init(JolokiaContext pContext, String pRealm) {
        context = pContext;
        realm = pRealm;
    }

    /** {@inheritDoc} */
    public boolean handleAllServersAtOnce(R pRequest) {
        return false;
    }

    /** {@inheritDoc} */
    public Object handleSingleServerRequest(MBeanServerConnection pServer, R pRequest)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        checkForRestriction(pRequest);
        checkHttpMethod(pRequest);
        return doHandleSingleServerRequest(pServer, pRequest);
    }

    /** {@inheritDoc} */
    public Object handleAllServerRequest(MBeanServerAccess pServerManager, R pRequest, Object pPreviousResult)
            throws ReflectionException, InstanceNotFoundException, MBeanException, AttributeNotFoundException, IOException, NotChangedException {
        checkForRestriction(pRequest);
        checkHttpMethod(pRequest);
        return doHandleAllServerRequest(pServerManager, pRequest, pPreviousResult);
    }

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
            throw new SecurityException("Command type " +
                    getType() + " not allowed due to policy used");
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
     * Abstract method to be subclassed by a concrete handler for performing the
     * request.
     *
     *
     * @param server server to try
     * @param request request to process
     * @return the object result from the request
     *
     * @throws InstanceNotFoundException
     * @throws AttributeNotFoundException
     * @throws ReflectionException
     * @throws MBeanException
     * @throws IOException
     */
    protected abstract Object doHandleSingleServerRequest(MBeanServerConnection server, R request)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException;

    /**
     * Default implementation fo handling a request for multiple servers at once. A subclass, which returns,
     * <code>true</code> on {@link #handleAllServersAtOnce(JolokiaRequest)}, needs to override this method.
     *
     * @param serverManager all MBean servers found in this JVM
     * @param request the original request
     * @param pPreviousResult a previous result which for merging requests can be used to merge files
     * @return the result of the the request.
     * @throws IOException
     * @throws AttributeNotFoundException
     * @throws InstanceNotFoundException
     * @throws MBeanException
     * @throws ReflectionException
     */
    protected Object doHandleAllServerRequest(MBeanServerAccess serverManager, R request, Object pPreviousResult)
                throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        return null;
    }

    /** {@inheritDoc} */
    public void destroy() throws JMException {

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
