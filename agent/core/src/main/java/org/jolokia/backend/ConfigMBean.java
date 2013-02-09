package org.jolokia.backend;

import java.io.IOException;

import javax.management.MalformedObjectNameException;

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
 * MBean for handling configuration issues from outside.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public interface ConfigMBean {

    // Name under which this bean gets registered
    String OBJECT_NAME = "jolokia:type=Config";

    // Legacy name for jmx4perl version < 0.80
    String LEGACY_OBJECT_NAME = "jmx4perl:type=Config";
    
    // Operations
    /**
     * Switch on history tracking for a specific attribute. If <code>pMaxEntries</code> is 0
     * history tracking is switched off.
     *
     * @param pMBean MBean object name
     * @param pAttribute attribute name
     * @param pPath inner path (optional)
     * @param pTarget remote target or null for a local mbean
     * @param pMaxEntries max last entries to remember, if 0 history tracking is switched off.
     * @throws MalformedObjectNameException if the given name is not proper object name
     * @deprecated use {@see #setHistoryLimitForAttribute} instead
     */
    void setHistoryEntriesForAttribute(String pMBean,String pAttribute,String pPath,String pTarget,int pMaxEntries) throws MalformedObjectNameException;


    /**
     * Switch on history tracking for a specific attribute. If <code>pMaxEntries</code> and <code>pMaxDuration</code> is 
     * 0 then history tracking is switched off.
     *
     * If either <code>pMaxEntries</code> or <code>pMaxDuration</code> 0, then the given limit applies. If both are != 0,
     * then both limits are applied simultaneously.
     *
     * @param pMBean MBean object name
     * @param pAttribute attribute name
     * @param pPath inner path (optional)
     * @param pTarget remote target or null for a local mbean
     * @param pMaxEntries max last entries to remember, if 0 history tracking is switched off.
     * @param pMaxDuration maximum duration the maximum duration for how long to keep a value (in seconds)
     * @throws MalformedObjectNameException if the given name is not proper object name
     */
    void setHistoryLimitForAttribute(String pMBean,String pAttribute,String pPath,String pTarget,int pMaxEntries,long pMaxDuration) 
            throws MalformedObjectNameException;


    /**
     * Switch on history tracking for an operation. If <code>pMaxEntries</code> is 0
     * history tracking is switched off. The return value of the operation will be tracked.
     * @param pMBean MBean object name
     * @param pOperation operation to track
     * @param pTarget remote target or null for a loal mbean
     * @param pMaxEntries max last entries to remember, if 0 history tracking is switched off.
     * @deprecated use {@see #setHistoryLimitForOperation} instead
     */
    void setHistoryEntriesForOperation(String pMBean,String pOperation,String pTarget,int pMaxEntries)
            throws MalformedObjectNameException;

    /**
     * Switch on history tracking for an operation. If <code>pMaxEntries</code> and pMaxDuration is 0
     * history tracking is switched off. The return value of the operation will be tracked.
     *
     * If either <code>pMaxEntries</code> or <code>pMaxDuration</code> 0, then the given limit applies. If both are != 0,
     * then both limits are applied simultaneously.
     *
     * @param pMBean MBean object name
     * @param pOperation operation to track
     * @param pTarget remote target or null for a loal mbean
     * @param pMaxEntries max last entries to remember, if 0 history tracking is switched off.
     * @param pMaxDuration maximum duration the maximum duration for how long to keep a value (in seconds)
     */
    void setHistoryLimitForOperation(String pMBean,String pOperation,String pTarget,int pMaxEntries,long pMaxDuration)
            throws MalformedObjectNameException;

    /**
     * Remove all history entries and switch off history tracking globally.
     */
    void resetHistoryEntries();

    /**
     * Get latest debug information if debugging is switched on. The returned output
     * will not take more than {@link #getMaxDebugEntries()} lines.
     *
     * @return debug info in plain ascii.
     */
    String debugInfo();

    /**
     * Reset all debug information stored internally
     */
    void resetDebugInfo();

    // Attributes

    /**
     * Get the size in bytes which the history mechanism requires in total if serialized.
     *
     * @return size of the complete history in bytes
     * @throws IOException if serialization (which is required for the size determination) fails.
     */
    int getHistorySize() throws IOException;

    /**
     * Number of global limit for history entries. No attribute historization can exceed this
     * limit (i.e if in {@link #setHistoryLimitForAttribute(String, String, String, String, int, long)}
     * the <code>pMaxEntries</code> is set larger than this limit, the global limit will be taken}
     *
     * @return the global history limit
     */
    int getHistoryMaxEntries();

    /**
     * Set the global history limit
     * @param pLimit limit to set
     */
    void setHistoryMaxEntries(int pLimit);

    /**
     * Check, whether debugging is switched on
     * @return state of debugging
     */
    boolean isDebug();

    /**
     * Set debugging to given state
     * @param pSwitch true, if debugging should be switched on, false otherwise
     */
    void setDebug(boolean pSwitch);

    /**
     * Number of debug entries to remember
     *
     * @return number of debug entries
     */
    int getMaxDebugEntries();

    /**
     * Set the number of debugging info to remember
     * @param pNumber entries to set
     */
    void setMaxDebugEntries(int pNumber);
}
