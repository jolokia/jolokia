/*
 * Copyright 2009-2013  Roland Huss
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

package org.jolokia.jmx;

import java.io.IOException;
import java.util.Set;

import javax.management.*;

/**
 * An MBeanSever executor is responsible to perform actions on one or more MBeanServers. It encapsulates
 * completely all available MBeanServers.
 *
 * @author roland
 * @since 17.01.13
 */
public interface MBeanServerExecutor {

    /**
     * Iterate over all MBeanServers managed and call the handler for each MBean found on every server.
     * Please note, that the return value of the action is ignored and the action should collect their
     * the values on its own if required.
     *
     * @param pObjectName object name to lookup, which can be a pattern in which case a query is performed.
     * @param pMBeanAction the action to be called for each MBean found on every server
     * @param pExtraArgs any extra args given through to the action
     * @param <R> return type, which gets ignored.
     *
     * @throws IOException
     * @throws ReflectionException
     * @throws MBeanException
     */
    <R> void iterate(ObjectName pObjectName, MBeanAction<R> pMBeanAction, Object ... pExtraArgs)
            throws IOException, ReflectionException, MBeanException;

    /**
     * Call an action an the first MBeanServer on which the action does not throw an InstanceNotFoundException
     * will considered to be successful and this method returns with the return value of the succesful
     * action. If no action was succesful, an {@link IllegalArgumentException} is raised (containing the last
     * {@link InstanceNotFoundException} from the last tried server)
     *
     * @param pObjectName objectname given through to the action
     * @param pMBeanAction the action to call
     * @param pExtraArgs any extra args given also to the action
     * @param <R> type of the return value
     * @return the return value of the succesful action
     *
     * @throws IOException
     * @throws ReflectionException
     * @throws MBeanException if the JMX call causes an issue
     */
    <R> R callFirst(ObjectName pObjectName, MBeanAction<R> pMBeanAction, Object... pExtraArgs)
            throws IOException, ReflectionException, MBeanException;

    /**
     * Query all MBeanServer and return the union of all results
     *
     * @param pObjectName pattern to query for. If null, then all MBean of all MBeanServers are returned
     * @return the found MBeans
     * @throws IOException if called remotely and an IOError occured.
     */
    Set<ObjectName> queryNames(ObjectName pObjectName) throws IOException;

    /**
     * A MBeanAction represent a single action on a MBeanServer for a given object name. The action is free
     * to throw a {@see InstanceNotFoundException} if the object name is not contained in the give MBeanServer.
     * In this case the next MBeanServer is tried. How this is done depends on the method this action is used in
     *
     * @param <R> return type for the execute methode
     */
    public interface MBeanAction<R> {
        /**
         * Execute the action either via {@link #callFirst(ObjectName, MBeanAction, Object...)} or
         * {@link #iterate(ObjectName, MBeanAction, Object...)}.
         *
         * @param pConn MBeanServer on which the action should be performed
         * @param pName an objectname interpreted specifically by the action
         * @param extraArgs any extra args given as context from the outside
         * @return the return value
         *
         * @throws ReflectionException
         * @throws InstanceNotFoundException if the MBean does not exist. For {@link #callFirst(ObjectName, MBeanAction, Object...)} this
         *         implies to try the next MBeanServer.
         * @throws IOException
         * @throws MBeanException
         * @throws AttributeNotFoundException if an attribute is read, this exception indicates, that the attribute is not
         *         known to the MBean specified (although the MBean has been found in the MBeanServer)
         */
        R execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs)
                throws ReflectionException, InstanceNotFoundException, IOException, MBeanException, AttributeNotFoundException;
    }
}
