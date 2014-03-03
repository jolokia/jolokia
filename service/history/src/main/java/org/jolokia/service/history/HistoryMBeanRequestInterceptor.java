package org.jolokia.service.history;

import java.lang.management.ManagementFactory;

import javax.management.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.RequestInterceptor;
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
        super.init(pCtx);

        int maxEntries = getMaxEntries(pCtx);
        HistoryStore historyStore = new HistoryStore(maxEntries);
        History history = new History(historyStore);
        historyObjectName = registerJolokiaMBean(History.OBJECT_NAME,history);

        //int maxDebugEntries = configuration.getAsInt(ConfigKey.DEBUG_MAX_ENTRIES);
        //debugStore = new DebugStore(maxDebugEntries, configuration.getAsBoolean(ConfigKey.DEBUG));
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();
        unregisterJolokiaMBean(historyObjectName);
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

    private int getMaxEntries(JolokiaContext pCtx) {
        int maxEntries;
        try {
            maxEntries = Integer.parseInt(pCtx.getConfig(ConfigKey.HISTORY_MAX_ENTRIES));
        } catch (NumberFormatException exp) {
            maxEntries = Integer.parseInt(ConfigKey.HISTORY_MAX_ENTRIES.getDefaultValue());
        }
        return maxEntries;
    }
}
