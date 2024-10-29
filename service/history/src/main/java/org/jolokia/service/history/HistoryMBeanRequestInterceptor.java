package org.jolokia.service.history;

import java.lang.management.ManagementFactory;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.management.*;
import javax.security.auth.Subject;

import org.jolokia.server.core.auth.JolokiaAgentPrincipal;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.json.JSONObject;

/**
 * @author roland
 * @since 09.09.13
 */
public class HistoryMBeanRequestInterceptor extends AbstractJolokiaService<RequestInterceptor> implements RequestInterceptor {

    // Objectname for updating the history
    private ObjectName historyObjectName;

    /**
     * Construction of a base service for a given type and order
     *
     * @param pOrderId order id used for ordering of services with a certain type
     */
    public HistoryMBeanRequestInterceptor(int pOrderId) {
        super(RequestInterceptor.class, pOrderId);
    }

    /** {@inheritDoc} */
    public void init(JolokiaContext pCtx) {
        // Init can be called twice in OSGi env, since we are registered as two services there.
        if (getJolokiaContext() == null) {
            super.init(pCtx);

            int maxEntries = getMaxEntries(pCtx);
            HistoryStore historyStore = new HistoryStore(maxEntries);
            History history = new History(historyStore);
            historyObjectName = registerJolokiaMBean(History.OBJECT_NAME,history);

            //int maxDebugEntries = configuration.getAsInt(ConfigKey.DEBUG_MAX_ENTRIES);
            //debugStore = new DebugStore(maxDebugEntries, configuration.getAsBoolean(ConfigKey.DEBUG));
        }
    }

    @Override
    public void destroy() throws Exception {
        if (getJolokiaContext() != null) {
            unregisterJolokiaMBean(historyObjectName);
        }
        super.destroy();
    }

    /**
     * Update history
     * @param pJmxReq request obtained
     * @param pJson result as included in the response
     */
    public void intercept(JolokiaRequest pJmxReq, JSONObject pJson) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        if (historyObjectName != null) {
            try {
                Subject.doAs(JolokiaAgentPrincipal.asSubject(), new PrivilegedExceptionAction<>() {
                    @Override
                    public Object run() {
                        try {
                            mBeanServer.invoke(historyObjectName,
                                "updateAndAdd",
                                new Object[] { pJmxReq, pJson },
                                new String[] { JolokiaRequest.class.getName(), JSONObject.class.getName() });
                        } catch (InstanceNotFoundException e) {
                            // Ignore, no history MBean is enabled, so no update
                        } catch (MBeanException e) {
                            throw new IllegalStateException("Internal: Cannot update History store",e);
                        } catch (ReflectionException e) {
                            throw new IllegalStateException("Internal: Cannot call History MBean via reflection",e);
                        }
                        return null;
                    }
                });
            } catch (PrivilegedActionException ignored) {
            }
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
