package org.jolokia.service.history;

import java.lang.management.ManagementFactory;

import javax.management.*;

import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.AbstractJolokiaService;
import org.jolokia.server.core.service.JolokiaContext;
import org.jolokia.server.core.util.jmx.JmxUtil;
import org.json.simple.JSONObject;

/**
 * @author roland
 * @since 09.09.13
 */
public class HistoryMBeanRequestInterceptor extends AbstractJolokiaService<RequestInterceptor> implements RequestInterceptor {

    // Objectname for updating the history
    private ObjectName historyObjectName;

    /**
     * Consruction of a base service for a given type and order
     *
     * @param pOrderId order id. A user of JolokiaService <em>must ensure</em> that the given
     *                 order id is unique for the given type. It used for ordering the services but is also
     *                 used as an id when storing it in  aset.
     */
    public HistoryMBeanRequestInterceptor(int pOrderId) {
        super(RequestInterceptor.class, pOrderId);
    }

    /** {@inheritDoc} */
    public void init(JolokiaContext pCtx) {
        int maxEntries;
        try {
            maxEntries = Integer.parseInt(pCtx.getConfig(ConfigKey.HISTORY_MAX_ENTRIES));
        } catch (NumberFormatException exp) {
            maxEntries = Integer.parseInt(ConfigKey.HISTORY_MAX_ENTRIES.getDefaultValue());
        }
        HistoryStore historyStore = new HistoryStore(maxEntries);
        try {
            // Register the Config MBean

            String oname = History.OBJECT_NAME + ",agent=" + pCtx.getAgentDetails().getAgentId();
            History history = new History(historyStore,oname);
            pCtx.registerMBean(history, oname);

            historyObjectName = JmxUtil.newObjectName(oname);
        } catch (InstanceAlreadyExistsException exp) {
            // That's ok, we are reusing it.
        } catch (NotCompliantMBeanException e) {
            pCtx.error("Error registering config MBean: " + e, e);
        } catch (MalformedObjectNameException e) {
            pCtx.error("Invalid name for config MBean: " + e, e);
        } catch (MBeanRegistrationException e) {
            pCtx.error("MBean Registration Error: " + e,e);
        }
        //int maxDebugEntries = configuration.getAsInt(ConfigKey.DEBUG_MAX_ENTRIES);
        //debugStore = new DebugStore(maxDebugEntries, configuration.getAsBoolean(ConfigKey.DEBUG));
    }

    /**
     * Update history
     * @param pJmxReq request obtained
     * @param pJson result as included in the response
     */
    public void intercept(JolokiaRequest pJmxReq, JSONObject pJson) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            if (historyObjectName != null) {
                mBeanServer.invoke(historyObjectName,
                                   "updateAndAdd",
                                   new Object[] { pJmxReq, pJson},
                                   new String[] { JolokiaRequest.class.getName(), JSONObject.class.getName() });
            }
        } catch (InstanceNotFoundException e) {
            // Ignore, no history MBean is enabled, so no update
        } catch (MBeanException e) {
            throw new IllegalStateException("Internal: Cannot update History store",e);
        } catch (ReflectionException e) {
            throw new IllegalStateException("Internal: Cannot call History MBean via reflection",e);
        }
    }
}
