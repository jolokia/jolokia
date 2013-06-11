package org.jolokia.backend;

import javax.management.*;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.detector.ServerHandle;
import org.jolokia.handler.CommandHandler;
import org.jolokia.request.JmxRequest;

/**
 * Handler class for accessing all local MBeanServers and dispatching
 * a given JmxRequest to a certain Jolokia Command
 *
 * @author roland
 * @since 11.06.13
 */
public interface MBeanServerHandler {

    /**
     * Dispatch a request to the MBeanServer which can handle it
     *
     * @param pRequestHandler request handler to be called with an MBeanServer
     * @param pJmxReq the request to dispatch
     * @return the result of the request
     */
    Object dispatch(CommandHandler pRequestHandler, JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, NotChangedException;

    /**
     * Register a MBean under a certain name to the platform MBeanServer
     *
     * @param pMBean MBean to register
     * @param pOptionalName optional name under which the bean should be registered. If not provided,
     * it depends on whether the MBean to register implements {@link javax.management.MBeanRegistration} or
     * not.
     *
     * @return the name under which the MBean is registered.
     */
    ObjectName registerMBean(Object pMBean, String... pOptionalName)
                    throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException;

    /**
     * Unregister all previously registered MBean. This is tried for all previously
     * registered MBeans
     *
     * @throws JMException if an exception occurs during unregistration
     */
    void destroy() throws JMException;

    /**
     * Get information about the detected server this agent is running on.
     *
     * @return the server info if detected or <code>null</code> if no server
     *          could be detected.
     */
    ServerHandle getServerHandle();
}
