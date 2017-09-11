package org.jolokia.detector;

import java.lang.instrument.Instrumentation;
import java.util.Properties;
import org.jolokia.backend.executor.MBeanServerExecutor;

public class LightstreamerDetector extends AbstractServerDetector {

    private static final String[] SYSTEM_PROERTY_NAMES = new String[]{
        "com.lightstreamer.internal_lib_path",
        "com.lightstreamer.kernel_lib_path",
        "com.lightstreamer.logging_lib_path",
        "com.lightstreamer.database_lib_path"};
    
    /**
     * {@inheritDoc}
     *
     * @param pMBeanServerExecutor
     */
    public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
        String serverVersion = getSingleStringAttribute(pMBeanServerExecutor, "com.lightstreamer:type=Server", "LSVersion");
        if (serverVersion != null) {
            return new ServerHandle("LightStreamer", "LightStreamer", serverVersion, null);
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
        for (String expectedPropertyName : SYSTEM_PROERTY_NAMES) {
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
