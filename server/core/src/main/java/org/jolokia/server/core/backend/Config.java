package org.jolokia.server.core.backend;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.server.core.util.DebugStore;

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
 * MBean for exporting various configuration tuning opportunities
 * to the outside world. 
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class Config implements ConfigMBean,MBeanRegistration {

    // Stores for various informations
    private DebugStore debugStore;

    // MBean Objectname under which this bean should be registered
    private String objectName;

    /**
     * Constructor with the configurable objects as parameters.
     *
     * @param pDebugStore debug store for holding debug messages
     * @param pOName object name under which to register this MBean
     */
    public Config(DebugStore pDebugStore, String pOName) {
        debugStore = pDebugStore;
        objectName = pOName;
    }

    /** {@inheritDoc} */
    public String debugInfo() {
        return debugStore.debugInfo();
    }

    /** {@inheritDoc} */
    public void resetDebugInfo() {
        debugStore.resetDebugInfo();
    }

    /** {@inheritDoc} */
    public boolean isDebug() {
        return debugStore.isDebug();
    }

    /** {@inheritDoc} */
    public void setDebug(boolean pSwitch) {
        debugStore.setDebug(pSwitch);
    }

    /** {@inheritDoc} */
    public int getMaxDebugEntries() {
        return debugStore.getMaxDebugEntries();
    }

    /** {@inheritDoc} */
    public void setMaxDebugEntries(int pNumber) {
        debugStore.setMaxDebugEntries(pNumber);
    }

    // ========================================================================

    // Provide our own name on registration
    /** {@inheritDoc} */
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws MalformedObjectNameException {
        return new ObjectName(objectName);
    }

    /** {@inheritDoc} */
    public void postRegister(Boolean registrationDone) {
    }

    /** {@inheritDoc} */
    public void preDeregister() {
    }

    /** {@inheritDoc} */
    public void postDeregister() {
    }
}
