package org.jolokia.backend.executor;

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

import java.io.IOException;
import java.util.Set;

import javax.management.*;

/**
 * An MBeanSever executor is responsible to perform actions on one or more MBeanServers which involve
 * operations on one or more MBeans. It encapsulates completely all available MBeanServers, so no direct
 * access to the MBeanServers are required. This interface is also suitable for implementations with
 * remote {@link MBeanServerConnection}s, e.g. for implementing it with JSR-160 connections.
 *
 * A {@link MBeanServerExecutor} is used
 *
 * @author roland
 * @since 17.01.13
 */
public interface MBeanServerExecutor {

    /**
     * Iterate over all MBeanServers managed and call the handler via a callback.
     *
     * If {@param pObjectName} is null or a pattern, the MBean names are queried on each MBeanServer and
     * feed into the callback. If it is a full object name, then all MBeanServers are called with this object
     * name in turn.
     *
     * @param pObjectName object name to lookup, which can be a pattern in which case a query is performed.
     * @param pCallback the action to be called for each MBean found on every server
     *
     * @throws IOException
     * @throws ReflectionException
     * @throws MBeanException
     */
    void each(ObjectName pObjectName, MBeanEachCallback pCallback)
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
    <R> R call(ObjectName pObjectName, MBeanAction<R> pMBeanAction, Object... pExtraArgs)
            throws IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException;

    /**
     * Query all MBeanServer and return the union of all results
     *
     * @param pObjectName pattern to query for. If null, then all MBean of all MBeanServers are returned
     * @return the found MBeans
     * @throws IOException if called remotely and an IOError occured.
     */
    Set<ObjectName> queryNames(ObjectName pObjectName) throws IOException;

    /**
     * Destructor method. After this method has been called, this executor is out of service and
     * must not be used anymore
     */
    void destroy();

    /**
     * Check whether the set of MBeans in all managed MBeanServer has been changed
     * since the given time. The input is the epoch time in seconds, however, milliseconds
     * would be much more appropriate. However, the Jolokia responses contain
     * currently time measured in seconds. This should be changed in a future version,
     * but this implies a quite heavy API changed (and if this is changed, the key
     * "timestamp" should be changed to "time", too, in order to fail early in case of
     * problems).
     *
     * In order to avoid inconsistencies for sub-second updates, we are comparing
     * conservatively (so hasBeenUpdated might return "true" more often than required).
     *
     * @param pTimestamp seconds since 1.1.1970
     * @return true if the MBeans has been updated since this time, false otherwise
     */
    boolean hasMBeansListChangedSince(long pTimestamp);

    /**
     * This callback is used together with {@link #each(ObjectName, MBeanEachCallback)} for iterating over all
     * active MBeanServers. The callback is responsible on its own to collect the information queried.
     */
    interface MBeanEachCallback {
        /**
         * Callback call for a specific MBeanServer for a given object name.
         *
         * @param pConn MBeanServer
         * @param pName object name as given by the surrounding {@link #each(ObjectName, MBeanEachCallback)} call, which
         *              can be either a pattern or null (in which case the names are searched for before) or a direct name.
         * @throws ReflectionException
         * @throws InstanceNotFoundException if the provided full-ObjectName is not registered at the MBeanServer
         * @throws IOException
         * @throws MBeanException if an operation of an MBean fails
         */
        void callback(MBeanServerConnection pConn, ObjectName pName)
                throws ReflectionException, InstanceNotFoundException, IOException, MBeanException;
    }

    /**
     * A MBeanAction represent a single action on a MBeanServer for a given object name. The action is free
     * to throw a {@link InstanceNotFoundException} or {@link AttributeNotFoundException} if the object name or attribute
     * is not contained in the give MBeanServer. In this case the next MBeanServer is tried, otherwise the result
     * from the action is returned and no other MBeanServers are tried
     *
     * @param <R> return type for the execute method
     */
    interface MBeanAction<R> {
        /**
         * Execute the action given to {@link #call(ObjectName, MBeanAction, Object...)}.
         *
         * @param pConn MBeanServer on which the action should be performed
         * @param pName an objectname interpreted specifically by the action
         * @param extraArgs any extra args given as context from the outside
         * @return the return value
         *
         * @throws ReflectionException
         * @throws InstanceNotFoundException if the MBean does not exist. For {@link #call(ObjectName, MBeanAction, Object...)} this
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
