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
package org.jolokia.server.core.util.jmx;

import java.io.IOException;
import java.util.Set;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.JMRuntimeException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

/**
 * <p>This is an interface Jolokia uses to <em>manage</em> all available MBean servers to be accessed
 * through {@link MBeanServerConnection}. The found/discovered MBean servers are then available using
 * {@link #getMBeanServers()}. Additionally two methods are provided to execute actions on the managed
 * MBean servers:<ul>
 *     <li>{@link #each} - invoke an action on all MBean servers and all matching {@link ObjectInstance mbeans} without
 *     expecting a result (actions are responsible for <em>collecting</em> the result). The action itself is
 *     never called with a pattern, because the querying is performed outside the action.</li>
 *     <li>{@link #call} - invoke an action on all MBean servers passing the {@link ObjectName} and
 *     return a first available result. The action decides what to do if the {@link ObjectName} is a pattern.</li>
 * </ul></p>
 *
 * <p>For {@link #each} call, because the {@link ObjectName} we pass may be a pattern, the behavior is to query
 * the MBean servers for concrete {@link ObjectInstance object instances}. This behavior makes one JMX exception
 * special - {@link InstanceNotFoundException} - when iterating over the MBean servers and Object instances, this
 * exception is ignored and doesn't break the iteration - because an MBean may get unregistered between querying
 * and actual action on this MBean.<br>
 * All other exceptions break the loop and are propagated to the caller.</p>
 *
 * <p>Neither the callbacks nor the {@code each()}/{@code call()} methods throw {@link org.jolokia.server.core.request.BadRequestException}
 * because validation if user/client input should already be performed.</p>
 *
 * @author roland
 * @since 17.01.13
 */
public interface MBeanServerAccess {

    /**
     * <p>Ask to invoke an {@link MBeanEachCallback} on a given {@link ObjectName} for all managed
     * {@link MBeanServerConnection MBean server connections} .</p>
     *
     * <p>If {@code pObjectName} is null or a pattern, the MBean names are queried first for each
     * {@link MBeanServerConnection} and all found {@link ObjectInstance instances} are passed into the callback.
     * If it is a non-pattern {@link ObjectName}, the query is also performed simply to get the
     * one {@link ObjectInstance}.</p>
     *
     * @param pObjectName object name to use, which can be a pattern (or null) in which case the callback is called with
     *                    more than one {@link ObjectInstance}.
     * @param pCallback the action to be called for each {@link ObjectInstance} found on every managed MBean server
     * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection}
     * @throws JMException JMX checked exception for all possible JMX exceptions from {@link MBeanServerConnection} interface
     * @throws JMRuntimeException JMX unchecked exception
     */
    void each(ObjectName pObjectName, MBeanEachCallback pCallback) throws IOException, JMException;

    /**
     * <p>Ask to invoke an {@link MBeanAction} on a given {@link ObjectName} and get a result of such action
     * from the first {@link MBeanServerConnection} that provides such result.</p>
     *
     * @param pObjectName object name to use by the action - whether it's a pattern or not
     * @param pAction the action to be called on every managed MBean server until any result is available
     * @param pExtraArgs any extra args to be passed to the action
     * @return the return value of the successful action
     * @param <R> type of the return value
     * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection}
     * @throws JMException JMX checked exception for all possible JMX exceptions from {@link MBeanServerConnection} interface
     * @throws JMRuntimeException JMX unchecked exception
     */
    <R> R call(ObjectName pObjectName, MBeanAction<R> pAction, Object... pExtraArgs) throws IOException, JMException;

    /**
     * Query all managed {@link MBeanServerConnection MBean server connections} and return the union of all results
     *
     * @param pObjectName pattern to query for. If null, then all MBeans of all MBeanServers are returned
     * @return the found {@link ObjectName MBean names}
     * @throws IOException if called remotely and an {@link IOException} occurred - on any of the managed servers
     */
    Set<ObjectName> queryNames(ObjectName pObjectName) throws IOException;

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
     * Get all MBeanServers which are handled by this manager
     *
     * @return set of MBeanServers to handle (in the proper merge order)
     */
    Set<MBeanServerConnection> getMBeanServers();

    // ---- Callback interfaces associated with MBeanServerAccess

    /**
     * <p>This callback is used together with {@link #each(ObjectName, MBeanEachCallback)} when iterating over all
     * active MBeanServers and all matching {@link ObjectInstance ObjectInstances} (or one, if the {@link ObjectName}
     * parameter is not a pattern).</p>
     *
     * <p>Caller of {@link MBeanServerAccess#each(ObjectName, MBeanEachCallback)} should construct this callback
     * to <em>collect</em> information from multiple invocations because no return value is expected.</p>
     */
    interface MBeanEachCallback {

        /**
         * Callback invoked for a single {@link MBeanServerConnection} and one {@link ObjectInstance}.
         *
         * @param pConn
         * @param pInstance MBean name+class derived from the {@link ObjectName} passed to {@link #each(ObjectName, MBeanEachCallback)}
         *                  call. When the {@link ObjectName} was a pattern or null, there will be more {@link ObjectInstance} objects.
         * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection}
         * @throws JMException JMX checked exception for all possible JMX exceptions from {@link MBeanServerConnection} interface
         * @throws JMRuntimeException JMX unchecked exception
         */
        void callback(MBeanServerConnection pConn, ObjectInstance pInstance) throws IOException, JMException;
    }

    /**
     * <p>This callback is used together with {@link #call(ObjectName, MBeanAction, Object...)} when iterating over all
     * active MBeanServers. First call of this action that doesn't throw {@link InstanceNotFoundException}
     * or {@link AttributeNotFoundException} and provides a result is treated as the final result of the {@code execute()}.
     * One of these two exceptions is a signal that we should proceed to next MBean server.</p>
     *
     * <p>This action is treated as a "get the first available result" action.</p>
     *
     * @param <R> return type for the execute method
     */
    interface MBeanAction<R> {

        /**
         * Action invoked for a single {@link MBeanServerConnection} and the passed {@link ObjectName}. When
         * there are more servers processed, first action that provides a result stops the iteration.
         *
         * @param pConn MBeanServer on which the action should be performed
         * @param pName an {@link ObjectName} interpreted by the action (whether it's a pattern or not)
         * @param extraArgs any extra args given as context from the outside
         * @return the return value
         *
         * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection}
         * @throws JMException JMX checked exception for all possible JMX exceptions from {@link MBeanServerConnection} interface.
         *         {@link InstanceNotFoundException} or {@link AttributeNotFoundException} indicates that next
         *         {@link MBeanServerConnection} should be checked.
         * @throws JMRuntimeException JMX unchecked exception
         */
        R execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs) throws IOException, JMException;
    }

}
