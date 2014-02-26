package org.jolokia.core.service.detector;

import java.util.Map;

import org.jolokia.core.request.JolokiaRequest;
import org.jolokia.core.service.JolokiaContext;
import org.jolokia.core.util.jmx.MBeanServerExecutor;
import org.json.simple.JSONObject;

/**
 * Information about the the server product the agent is running in.
 *
 * @author roland
 * @since 05.11.10
 */
public interface ServerHandle {

    /**
     * Get name of vendor
     */
    String getVendor();

    /**
     * Get the name of the server this agent is running in
     *
     * @return server name
     */
    String getProduct();

    /**
     * Get version number of the agent server
     * @return version number
     */
    String getVersion();

    /**
     * Hook for performing certain workarounds/pre processing just before
     * a request gets dispatched
     *
     * @param pExecutor a JMX executor for easy JMX access
     * @param pJmxReq the request to dispatch
     */
    void preDispatch(MBeanServerExecutor pExecutor, JolokiaRequest pJmxReq);

    /**
     * Hook called after the detection phase. This can be used by a handle to perform
     * some specific action, possibly based on the configuration given.
     *
     * The default is a no-op.
     *
     * @param pExecutor JMX executor for allowing easy JMX accessing
     * @param pContext the Jolokia Context
     */
    void postDetect(MBeanServerExecutor pExecutor, JolokiaContext pContext);

    /**
     * Return this info as an JSONObject
     *
     * @return this object in JSON representation
     */
    JSONObject toJSONObject();

    /**
     * Extract extra dynamic information specific for this server handle. It can be obtained
     * from JMX if necessary and hence an server executor is given for enabling a JMX query.
     * A subclass should override this since this default method returns null.
     *
     * @param pServerManager server manager for allowing a query
     * @return extra information
     */
    Map<String, String> getExtraInfo(MBeanServerExecutor pServerManager);

    // =============================================================================================
    // A "null" server handle
    final ServerHandle NULL_SERVER_HANDLE = new ServerHandle() {
        public String getVendor() {
            return null;
        }

        public String getProduct() {
            return null;
        }

        public String getVersion() {
            return null;
        }

        public void preDispatch(MBeanServerExecutor pExecutor, JolokiaRequest pJmxReq) {

        }

        public void postDetect(MBeanServerExecutor pExecutor, JolokiaContext pContext) {

        }

        public JSONObject toJSONObject() {
            return new JSONObject();
        }

        public Map<String, String> getExtraInfo(MBeanServerExecutor pServerManager) {
            return null;
        }
    };
}
