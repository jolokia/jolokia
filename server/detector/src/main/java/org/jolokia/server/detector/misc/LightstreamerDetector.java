package org.jolokia.server.detector.misc;

import java.lang.instrument.Instrumentation;
import java.util.Properties;

import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.detector.jee.AbstractServerDetector;

public class LightstreamerDetector extends AbstractServerDetector {

    private static final String[] SYSTEM_PROPERTY_NAMES = new String[]{
        "com.lightstreamer.internal_lib_path",
        "com.lightstreamer.kernel_lib_path",
        "com.lightstreamer.logging_lib_path",
        "com.lightstreamer.database_lib_path"};

    /**
     * The order of this detector
     *
     * @param pOrder detector's order
     */
    public LightstreamerDetector(int pOrder) {
        super("lightstreamer", pOrder);
    }

    /**
     * {@inheritDoc}
     *
     * @param pMBeanServerAccess
     */
    public ServerHandle detect(MBeanServerAccess pMBeanServerAccess) {
        String serverVersion = getSingleStringAttribute(pMBeanServerAccess, "com.lightstreamer:type=Server", "LSVersion");
        if (serverVersion != null) {
            return new DefaultServerHandle("LightStreamer", "LightStreamer", serverVersion);
        }
        return null;
    }

    @Override
    public void jvmAgentStartup(Instrumentation instrumentation) {
        if (isLightStreamer(instrumentation)) {
            awaitLightstreamerMBeans(instrumentation);
        }
    }

    protected boolean isLightStreamer(Instrumentation instrumentation) {
        Properties systemProperties = System.getProperties();
        for (String expectedPropertyName : SYSTEM_PROPERTY_NAMES) {
            if (systemProperties.containsKey(expectedPropertyName)) {
                return true;
            }
        }
        return false;
    }

    private static final int LIGHTSTREAMER_DETECT_TIMEOUT = 5 * 60 * 1000;
    private static final int LIGHTSTREAMER_DETECT_INTERVAL = 200;
    private static final int LIGHTSTREAMER_DETECT_FINAL_DELAY = 500;
    private static final String LIGHTSTREAMER_MBEAN_CLASS = "com.lightstreamer.jmx.ServerMBean";

    private void awaitLightstreamerMBeans(Instrumentation instrumentation) {
        int count = 0;
        while (count * LIGHTSTREAMER_DETECT_INTERVAL < LIGHTSTREAMER_DETECT_TIMEOUT) {
            boolean serverMBean = isClassLoaded(LIGHTSTREAMER_MBEAN_CLASS, instrumentation);
            if (serverMBean) {
                try {
                    Thread.sleep(LIGHTSTREAMER_DETECT_FINAL_DELAY);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            try {
                Thread.sleep(LIGHTSTREAMER_DETECT_INTERVAL);
                count++;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException(String.format("Detected Lightstreamer, but JMX MBeans were not loaded after %d seconds", LIGHTSTREAMER_DETECT_TIMEOUT / 1000));
    }
}
