package org.jolokia.backend;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import javax.management.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jolokia.JmxRequest;
import org.jolokia.handler.JsonRequestHandler;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * Handler for finding and merging various MBeanServers locally when used
 * as an agent.
 *
 * @author roland
 * @since Jun 15, 2009
 */
public class MBeanServerHandler implements MBeanServerHandlerMBean,MBeanRegistration {

    // The MBeanServers to use
    // TODO: Dont cache them here ! Important for OSGi usage ...
    private Set<MBeanServer> mBeanServers;
    private Set<MBeanServerConnection> mBeanServerConnections;


    // Whether we are running under JBoss
    private boolean isJBoss = checkForClass("org.jboss.mx.util.MBeanServerLocator");

    // Whether running under Websphere
    private boolean isWebsphere = checkForClass("com.ibm.websphere.management.AdminServiceFactory");
    private boolean isWebsphere7 = checkForClass("com.ibm.websphere.management.AdminContext");
    private boolean isWebsphere6 = isWebsphere && !isWebsphere7;

    // Optional domain for registering this handler as a mbean
    private String qualifier;

    public void init() throws MalformedObjectNameException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        registerMBean(this,getObjectName());
    }

    // Handle for remembering registered MBeans
    private class MBeanHandle {
        private ObjectName objectName;
        private MBeanServer server;

        MBeanHandle(MBeanServer pServer, ObjectName pRegisteredName) {
            server = pServer;
            objectName = pRegisteredName;
        }
    }
    // Handles remembered for unregistering
    private List<MBeanHandle> mBeanHandles = new ArrayList<MBeanHandle>();


    /**
     * Create a new MBeanServer with no qualifier
     */
    public MBeanServerHandler() {
        this(null);
    }

    /**
     * Create a new MBeanServer handler who is responsible for managing multiple intra VM {@link MBeanServer} at once
     *
     * @param pQualifier optional qualifier used for registering this object as an MBean (can be null)
     */
    public MBeanServerHandler(String pQualifier) {
        initMBeanServers();
        qualifier = pQualifier;
    }

    /**
     * Dispatch a request to the MBeanServer which can handle it
     *
     * @param pRequestHandler request handler to be called with an MBeanServer
     * @param pJmxReq the request to dispatch
     * @return the result of the request
     */
    public Object dispatchRequest(JsonRequestHandler pRequestHandler, JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
        if (pRequestHandler.handleAllServersAtOnce(pJmxReq)) {
            try {
                return pRequestHandler.handleRequest(mBeanServerConnections,pJmxReq);
            } catch (IOException e) {
                throw new IllegalStateException("Internal: IOException " + e + ". Shouldn't happen.",e);
            }
        } else {
            wokaroundJBossBug(pJmxReq);
            return handleRequest(pRequestHandler, pJmxReq);
        }
    }

    private Object handleRequest(JsonRequestHandler pRequestHandler, JmxRequest pJmxReq)
            throws ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
        AttributeNotFoundException attrException = null;
        InstanceNotFoundException objNotFoundException = null;
        for (MBeanServer s : mBeanServers) {
            try {
                return pRequestHandler.handleRequest(s, pJmxReq);
            } catch (InstanceNotFoundException exp) {
                // Remember exceptions for later use
                objNotFoundException = exp;
            } catch (AttributeNotFoundException exp) {
                attrException = exp;
            } catch (IOException exp) {
                throw new IllegalStateException("I/O Error while dispatching",exp);
            }
        }
        if (attrException != null) {
            throw attrException;
        }
        // Must be there, otherwise we would not have left the loop
        throw objNotFoundException;
    }


    /**
     * Register a MBean under a certain name to the first availabel MBeans server
     *
     * @param pMBean MBean to register
     * @param pOptionalName optional name under which the bean should be registered. If not provided,
     * it depends on whether the MBean to register implements {@link javax.management.MBeanRegistration} or
     * not.
     *
     * @return the name under which the MBean is registered.
     */
    public ObjectName registerMBean(Object pMBean,String ... pOptionalName)
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException {
        if (mBeanServers.size() > 0) {
            synchronized (mBeanHandles) {
                Exception lastExp = null;
                for (MBeanServer server : mBeanServers) {
                    try {
                        ObjectName registeredName = registerMBeanAtServer(server, pMBean, pOptionalName);
                        mBeanHandles.add(new MBeanHandle(server,registeredName));
                        return registeredName;
                    } catch (RuntimeException exp) {
                        lastExp = exp;
                    } catch (MBeanRegistrationException exp) {
                        lastExp = exp;
                    }
                }
                if (lastExp != null) {
                    throw new IllegalStateException("Could not register " + pMBean + ": " + lastExp,lastExp);
                }
            }
        }
        throw new IllegalStateException("No MBeanServer initialized yet");
    }

    private ObjectName registerMBeanAtServer(MBeanServer pServer, Object pMBean, String[] pName)
            throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        // Websphere adds extra parts to the object name if registered explicitly, but
        // we need a defined name on the client side. So we register it with 'null' in websphere
        // and let the bean define its name. On the other side, Resin throws an exception
        // if registering with a null name, so we have to do this explicit check on Websphere
        if (!isWebsphere6 &&  pName != null && pName.length > 0 && pName[0] != null) {
            ObjectName oName = new ObjectName(pName[0]);
            return pServer.registerMBean(pMBean,oName).getObjectName();
        } else {
            // Needs to implement MBeanRegistration interface
            return pServer.registerMBean(pMBean,null).getObjectName();
        }
    }

    /**
     * Unregister all previously registered MBean. This is tried for all previously
     * registered MBeans
     *
     * @throws JMException if an exception occurs during unregistration
     */
    public void unregisterMBeans() throws JMException {
        synchronized (mBeanHandles) {
            List<JMException> exceptions = new ArrayList<JMException>();
            List<MBeanHandle> unregistered = new ArrayList<MBeanHandle>();
            for (MBeanHandle handle : mBeanHandles) {
                try {
                    handle.server.unregisterMBean(handle.objectName);
                    unregistered.add(handle);
                } catch (InstanceNotFoundException e) {
                    exceptions.add(e);
                } catch (MBeanRegistrationException e) {
                    exceptions.add(e);
                }
            }
            // Remove all successfully unregistered handles
            mBeanHandles.removeAll(unregistered);

            // Throw error if any exception occured during unregistration
            if (exceptions.size() == 1) {
                throw exceptions.get(0);
            } else if (exceptions.size() > 1) {
                StringBuffer ret = new StringBuffer();
                for (JMException e : exceptions) {
                    ret.append(e.getMessage()).append("\n");
                }
                throw new JMException(ret.toString());
            }
        }
    }

    /**
     * Get the set of MBeanServers found
     *
     * @return set of mbean servers
     */
    public Set<MBeanServer> getMBeanServers() {
        return Collections.unmodifiableSet(mBeanServers);
    }

    // =================================================================================

    /**
     * Use various ways for getting to the MBeanServer which should be exposed via this
     * servlet.
     *
     * <ul>
     *   <li>If running in JBoss, use <code>org.jboss.mx.util.MBeanServerLocator</code>
     *   <li>Use {@link javax.management.MBeanServerFactory#findMBeanServer(String)} for
     *       registered MBeanServer and take the <b>first</b> one in the returned list
     *   <li>Finally, use the {@link java.lang.management.ManagementFactory#getPlatformMBeanServer()}
     * </ul>
     *
     * @return the MBeanServer found
     * @throws IllegalStateException if no MBeanServer could be found.
     */
    private void initMBeanServers() {

        // Check for JBoss MBeanServer via its utility class
        mBeanServers = new LinkedHashSet<MBeanServer>();
        addJBossMBeanServer(mBeanServers);
        addWebsphereMBeanServer(mBeanServers);
        addFromMBeanServerFactory(mBeanServers);
        addFromJndiContext(mBeanServers);
        mBeanServers.add(ManagementFactory.getPlatformMBeanServer());

        if (mBeanServers.size() == 0) {
			throw new IllegalStateException("Unable to locate any MBeanServer instance");
		}

        // Copy over servers into connection set. Required for proper generic usage
        mBeanServerConnections = new LinkedHashSet<MBeanServerConnection>();
        for (MBeanServer server : mBeanServers) {
            mBeanServerConnections.add(server);
        }
	}

    private void addFromJndiContext(Set<MBeanServer> servers) {
        // Weblogic stores the MBeanServer in a JNDI context
        InitialContext ctx;
        try {
            ctx = new InitialContext();
            MBeanServer server = (MBeanServer) ctx.lookup("java:comp/env/jmx/runtime");
            if (server != null) {
                servers.add(server);
            }
        } catch (NamingException e) {
            // expected and can happen on non-Weblogic platforms
        }
    }

    private void addWebsphereMBeanServer(Set<MBeanServer> servers) {
        try {
			/*
			 * this.mbeanServer = AdminServiceFactory.getMBeanFactory().getMBeanServer();
			 */
			Class adminServiceClass = getClass().getClassLoader().loadClass("com.ibm.websphere.management.AdminServiceFactory");
			Method getMBeanFactoryMethod = adminServiceClass.getMethod("getMBeanFactory", new Class[0]);
			Object mbeanFactory = getMBeanFactoryMethod.invoke(null);
			Method getMBeanServerMethod = mbeanFactory.getClass().getMethod("getMBeanServer", new Class[0]);
			servers.add((MBeanServer) getMBeanServerMethod.invoke(mbeanFactory));
		}
		catch (ClassNotFoundException ex) {
            // Expected if not running under WAS
		}
		catch (InvocationTargetException ex) {
            // CNFE should be earlier
            throw new IllegalArgumentException("Internal: Found AdminServiceFactory but can not call methods on it (wrong WAS version ?)",ex);
		} catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("Internal: Found AdminServiceFactory but can not call methods on it (wrong WAS version ?)",ex);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("Internal: Found AdminServiceFactory but can not call methods on it (wrong WAS version ?)",ex);
        }
    }

    // Special handling for JBoss
    private void addJBossMBeanServer(Set<MBeanServer> servers) {
        try {
            Class locatorClass = Class.forName("org.jboss.mx.util.MBeanServerLocator");
            Method method = locatorClass.getMethod("locateJBoss");
            servers.add((MBeanServer) method.invoke(null));
        }
        catch (ClassNotFoundException e) { /* Ok, its *not* JBoss, continue with search ... */ }
        catch (NoSuchMethodException e) { }
        catch (IllegalAccessException e) { }
        catch (InvocationTargetException e) { }
    }

    // Lookup from MBeanServerFactory
    private void addFromMBeanServerFactory(Set<MBeanServer> servers) {
        List<MBeanServer> beanServers = MBeanServerFactory.findMBeanServer(null);
        if (beanServers != null) {
            servers.addAll(beanServers);
        }
    }

    // =====================================================================================

    private void wokaroundJBossBug(JmxRequest pJmxReq) throws ReflectionException, InstanceNotFoundException {
        // if ((isJBoss || isWebsphere)
        // The workaround was enabled for websphere as well, but it seems
        // to work without it for WAS 7.0
        if (isJBoss && pJmxReq.getObjectName() != null &&
                "java.lang".equals(pJmxReq.getObjectName().getDomain())) {
            try {
                // invoking getMBeanInfo() works around a bug in getAttribute() that fails to
                // refetch the domains from the platform (JDK) bean server (e.g. for MXMBeans)
                for (MBeanServer s : mBeanServers) {
                    try {
                        s.getMBeanInfo(pJmxReq.getObjectName());
                        return;
                    } catch (InstanceNotFoundException exp) {
                        // Only one server can have the name. So, this exception
                        // is being expected to happen
                    }
                }
            } catch (IntrospectionException e) {
                throw new IllegalStateException("Workaround for JBoss failed for object " + pJmxReq.getObjectName() + ": " + e);
            }
        }
    }

    boolean checkForClass(String pClassName) {
        try {
            Class.forName(pClassName);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    public String mBeanServersInfo() {
        StringBuffer ret = new StringBuffer();
        Set<MBeanServer> servers = getMBeanServers();

        ret.append("Found ").append(servers.size()).append(" MBeanServers\n");
        for (MBeanServer s : servers) {
            ret.append("    ")
                    .append("++ ")
                    .append(s.toString())
                    .append(": default domain = ")
                    .append(s.getDefaultDomain())
                    .append(", ")
                    .append(s.getMBeanCount())
                        .append(" MBeans\n");

            ret.append("        Domains:\n");
            boolean javaLangFound = false;
            for (String d : s.getDomains()) {
                if ("java.lang".equals(d)) {
                    javaLangFound = true;
                }
                appendDomainInfo(ret, s, d);
            }
            if (!javaLangFound) {
                // JBoss fails to list java.lang in its domain list
                appendDomainInfo(ret,s,"java.lang");
            }
        }
        ret.append("\n");
        ret.append("Platform MBeanServer: ")
                .append(ManagementFactory.getPlatformMBeanServer())
                .append("\n");
        return ret.toString();
    }

    private void appendDomainInfo(StringBuffer pRet, MBeanServer pServer, String pDomain) {
        try {
            pRet.append("         == ").append(pDomain).append("\n");
            Set<ObjectInstance> beans = pServer.queryMBeans(new ObjectName(pDomain + ":*"),null);
            for (ObjectInstance o : beans) {
                String n = o.getObjectName().getCanonicalKeyPropertyListString();
                pRet.append("              ").append(n).append("\n");
            }
        } catch (MalformedObjectNameException e) {
            // Shouldnt happen
            pRet.append("              INTERNAL ERROR: ").append(e).append("\n");
        }
    }

    // ==============================================================================================
    // Needed for providing the name for our MBean
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws MalformedObjectNameException {
        return new ObjectName(getObjectName());
    }

    public String getObjectName() {
        return OBJECT_NAME + (qualifier != null ? "," + qualifier : "");
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() {
    }

    public void postDeregister() {
    }
}
