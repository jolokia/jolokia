package org.jolokia.backend;

import org.jolokia.JmxRequest;
import org.jolokia.config.Config;
import org.jolokia.config.DebugStore;
import org.jolokia.config.Restrictor;
import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.handler.*;
import org.jolokia.history.HistoryStore;

import javax.management.*;

/**
 * Dispatcher which dispatches to one or more local {@link javax.management.MBeanServer}.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class LocalRequestDispatcher implements RequestDispatcher {

    // Handler for finding and merging the various MBeanHandler
    private MBeanServerHandler mBeanServerHandler;

    private RequestHandlerManager requestHandlerManager;

    // MBean of configuration MBean
    private ObjectName configMBeanName;

    // Name of the exposed MBeanServerHandler-MBean
    private ObjectName mbeanServerHandlerMBeanName;

    // An (optional) qualifier for registering MBeans.
    private String qualifier;

    /**
     * Create a new local dispatcher which accesses local MBeans.
     *
     * @param objectToJsonConverter a serializer to JSON
     * @param stringToObjectConverter a de-serializer for arguments
     * @param restrictor restrictor which checks the access for various operations
     * @param pQualifier optional qualifier for registering own MBean to allow for multiple J4P instances in the VM
     */
    public LocalRequestDispatcher(ObjectToJsonConverter objectToJsonConverter,
                                  StringToObjectConverter stringToObjectConverter,
                                  Restrictor restrictor, String pQualifier) {
        requestHandlerManager = new RequestHandlerManager(objectToJsonConverter,stringToObjectConverter,restrictor);
        // Get all MBean servers we can find. This is done by a dedicated
        // handler object
        mBeanServerHandler = new MBeanServerHandler(pQualifier);
        qualifier = pQualifier;
    }



    // Can handle any request
    public boolean canHandle(JmxRequest pJmxRequest) {
        return true;
    }

    public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
        JsonRequestHandler handler = requestHandlerManager.getRequestHandler(pJmxRequest.getType());
        return handler.useReturnValueWithPath();
    }

    public Object dispatchRequest(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
        JsonRequestHandler handler = requestHandlerManager.getRequestHandler(pJmxReq.getType());
        return mBeanServerHandler.dispatchRequest(handler, pJmxReq);
    }

    public void init(HistoryStore pHistoryStore, DebugStore pDebugStore)
            throws MalformedObjectNameException, MBeanRegistrationException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        Config config = new Config(pHistoryStore,pDebugStore,qualifier);
        configMBeanName = registerMBean(config,config.getObjectName());

        mbeanServerHandlerMBeanName = registerMBean(mBeanServerHandler,mBeanServerHandler.getObjectName());
    }

    public void destroy() throws MalformedObjectNameException, InstanceNotFoundException, MBeanRegistrationException {
        if (configMBeanName != null) {
            unregisterMBean(configMBeanName);
        }
        if (mbeanServerHandlerMBeanName != null) {
            unregisterMBean(mbeanServerHandlerMBeanName);
        }
    }


    public ObjectName registerMBean(Object pMbean,String pName)
            throws MBeanRegistrationException, NotCompliantMBeanException,
            MalformedObjectNameException, InstanceAlreadyExistsException {
        // Websphere adds extra parts to the object name if registered explicitely, but
        // we need a defined name on the client side. So we register it with 'null' in websphere
        // and let the bean define its name. On the other side, Resin throws an exception
        // if registering with a null name, so we have to do this explicite check.
        return mBeanServerHandler.registerMBean(
                pMbean,
                mBeanServerHandler.checkForClass("com.ibm.websphere.management.AdminServiceFactory") ?
                        null :
                        pName);
    }

    public void unregisterMBean(ObjectName pMBeanName)
            throws MBeanRegistrationException, InstanceNotFoundException,
            MalformedObjectNameException {
        mBeanServerHandler.unregisterMBean(pMBeanName);
    }


}
