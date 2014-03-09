package org.jolokia.service.jmx.api;

import java.io.IOException;

import javax.management.*;

import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

/**
 * @author roland
 * @since 09.03.14
 */
public interface CommandHandler<R extends JolokiaRequest> {
    /**
     * The type of request which can be served by this handler
     * @return the request typ of this handler
     */
    RequestType getType();

    /**
     * Override this if you want all servers as list in the argument, e.g.
     * to query each server on your own. By default, dispatching of the servers
     * are done for you
     *
     * @param pRequest request to decide on whether to handle all request at once
     * @return whether you want to have
     * {@link #handleSingleServerRequest(MBeanServerConnection, JolokiaRequest)}
     * (<code>false</code>) or
     * {@link #handleAllServerRequest(MBeanServerAccess, JolokiaRequest, Object)} (<code>true</code>) called.
     */
    boolean handleAllServersAtOnce(R pRequest);

    /**
     * Handle a request for a single server and throw an
     * {@link javax.management.InstanceNotFoundException}
     * if the request cannot be handle by the provided server.
     * Does a check for restrictions as well
     *
     * @param pServer server to try
     * @param pRequest request to process
     * @return the object result from the request
     *
     * @throws InstanceNotFoundException if the provided server cant handle the request
     * @throws AttributeNotFoundException
     * @throws ReflectionException
     * @throws MBeanException
     * @throws java.io.IOException
     */
    Object handleSingleServerRequest(MBeanServerConnection pServer, R pRequest)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException;

    /**
     * Override this if you want to have all servers at once for processing the request
     * (like need for merging info as for a <code>list</code> command). This method
     * is only called when {@link #handleAllServersAtOnce(JolokiaRequest)} returns <code>true</code>
     *
     * @param pServerManager server manager holding all MBeans servers detected
     * @param request request to process
     * @param pPreviousResult a previous result which for merging requests can be used to merge files
     * @return the object found
     * @throws IOException
     * @throws AttributeNotFoundException
     * @throws InstanceNotFoundException
     * @throws MBeanException
     * @throws ReflectionException
     */
    Object handleAllServerRequest(MBeanServerAccess pServerManager, R request, Object pPreviousResult)
            throws ReflectionException, InstanceNotFoundException, MBeanException, AttributeNotFoundException, IOException, NotChangedException;

    /**
     * Lifecycle method in order to initialize the handler
     *
     * @param pContext the jolokia context
     * @param pRealm realm to use for returned names. Handlers can use this  for returning meta
     *               data with the proper realm prefixed.
     */
    void init(JolokiaContext pContext, String pRealm);

   /**
     * Lifecycle method called when agent goes down. Should be overridden by
     * a handler if required.
     */
    void destroy() throws JMException;
}
