package org.jolokia.agent.osgi;

import java.util.Arrays;
import java.util.List;

import org.jolokia.server.core.osgi.OsgiAgentActivator;
import org.jolokia.server.detector.osgi.DetectorActivator;
import org.jolokia.service.discovery.osgi.DiscoveryServiceActivator;
import org.jolokia.service.history.osgi.HistoryServiceActivator;
import org.jolokia.service.jmx.osgi.JmxServiceActivator;
import org.jolokia.service.notif.pull.osgi.PullNotificationServiceActivator;
import org.jolokia.service.notif.sse.osgi.SseNotificationServiceActivator;
import org.jolokia.service.serializer.osgi.SerializerServiceActivator;
import org.osgi.framework.*;
import org.osgi.service.log.LogService;

/**
 * @author roland
 * @since 04.03.14
 */
public class JolokiaBundleActivator implements BundleActivator {

    List<BundleActivator> activators;

    public JolokiaBundleActivator() {
        activators = Arrays.asList(
                new OsgiAgentActivator(),
                new DetectorActivator(),
                new JmxServiceActivator(),
                new SerializerServiceActivator(),
                new DiscoveryServiceActivator(),
                new HistoryServiceActivator(),
                new PullNotificationServiceActivator(),
                new SseNotificationServiceActivator()
                );
    }

    public void start(BundleContext pContext) throws Exception {
        for (BundleActivator activator : activators) {
            try {
                activator.start(pContext);
            } catch (Exception exp) {
                logError(pContext, "Error during start with " + activator + ": " + exp,exp);
            }
        }
    }

    public void stop(BundleContext pContext) throws Exception {
        for (BundleActivator activator : activators) {
           try {
               activator.stop(pContext);
           } catch (Exception exp) {
               logError(pContext, "Error during stop for " + activator + ": " + exp,exp);
           }
        }
    }

    private void logError(BundleContext ctx, String pTxt,Exception pExp) {
        ServiceReference ref = ctx.getServiceReference(LogService.class.getName());
        if (ref != null) {
            try {
                LogService service = (LogService) ctx.getService(ref);
                if (service != null) {
                    service.log(LogService.LOG_ERROR,pTxt,pExp);
                    return;
                }
            } finally {
               ctx.ungetService(ref);
            }
        }
        System.err.println("E> " + pTxt);
        pExp.printStackTrace();
    }
}
