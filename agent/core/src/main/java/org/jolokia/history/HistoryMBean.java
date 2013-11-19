package org.jolokia.history;

import java.io.IOException;

import javax.management.MalformedObjectNameException;

import org.jolokia.request.JolokiaRequest;
import org.json.simple.JSONObject;

/**
 * JMX interface for the history store
 *
 * @author roland
 * @since 11.06.13
 */
public interface HistoryMBean {

    // Name under which this bean gets registered
    String OBJECT_NAME = "jolokia:type=Config";

    // Operations

    /**
     * Update the history store with the value of an an read, write or execute operation. Also, the timestamp
     * of the insertion is recorded. Also, the recorded history values are added to the given json value.
     * This operation must be called only internally.
     *
     * @param pJmxReq request for which an entry should be added in this history store
     * @param pJson the JSONObject to which to add the history.
     */
    void updateAndAdd(JolokiaRequest pJmxReq, JSONObject pJson);

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
}
