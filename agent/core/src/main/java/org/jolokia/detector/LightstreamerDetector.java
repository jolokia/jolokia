package org.jolokia.detector;

import java.lang.instrument.Instrumentation;
import org.jolokia.backend.executor.MBeanServerExecutor;

public class LightstreamerDetector extends AbstractServerDetector {


    /** {@inheritDoc}
     * @param pMBeanServerExecutor*/
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
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.contains("com.lightstreamer")) {
                return true;
            }
        }
        for (Class loadedClass : instrumentation.getAllLoadedClasses()) {
            if (loadedClass.getCanonicalName().contains("com.lightstreamer")) {
                return true;
            }
        }
        return false;
    }
    
    public static final int LIGHTSTREAMER_DETECT_TIMEOUT = 5 * 60 * 1000;
    public static final int LIGHTSTREAMER_DETECT_INTERVAL = 200;
    public static final int LIGHTSTREAMER_DETECT_FINAL_DELAY = 500;
    public static final String LIGHTSTREAMER_MBEAN_CLASS = "com.lightstreamer.jmx.ServerMBean";

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
