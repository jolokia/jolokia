package org.jolokia.osgi.util;

import org.jolokia.osgi.security.ServiceAuthenticationHttpContext;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

/**
 * Helper class for logging errors
 *
 * @author roland
 * @since 06.02.18
 */
public class LogHelper {


    /**
     * Log error to a logging service (if available), otherwise log to std error
     *
     * @param pMessage message to log
     * @param pThrowable an exception to log
     */
    public static void logError(String pMessage, Throwable pThrowable) {
        final BundleContext bundleContext = FrameworkUtil
            .getBundle(ServiceAuthenticationHttpContext.class)
            .getBundleContext();
        logError(bundleContext, pMessage, pThrowable);
    }


    /**
     * Log error to a logging service (if available), otherwise log to std error
     *
     * @param pBundleContext bundle context to lookup LogService
     * @param pMessage message to log
     * @param pThrowable an exception to log
     */
    public static void logError(BundleContext pBundleContext, String pMessage, Throwable pThrowable) {

        final ServiceReference lRef = pBundleContext.getServiceReference(LogService.class.getName());
        if (lRef != null) {
            try {
                final LogService logService = (LogService) pBundleContext.getService(lRef);
                if (logService != null) {
                    logService.log(LogService.LOG_ERROR, pMessage, pThrowable);
                    return;
                }
            } finally {
                pBundleContext.ungetService(lRef);
            }
        }
        System.err.println("Jolokia-Error: " + pMessage + " : " + pThrowable.getMessage());
    }
}
