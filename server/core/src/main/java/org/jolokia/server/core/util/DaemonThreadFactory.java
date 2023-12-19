package org.jolokia.server.core.util;

import java.util.concurrent.ThreadFactory;

/**
 * Thread factory for creating daemon threads only
 *
 * @author roland
 * @since 28.02.14
 */
public class DaemonThreadFactory implements ThreadFactory {

    private int threadInitNumber;
    private final String threadNamePrefix;

    public DaemonThreadFactory(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    private synchronized int nextThreadNum() {
        return threadInitNumber++;
    }


    /** {@inheritDoc} */
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, threadNamePrefix + nextThreadNum());
        t.setDaemon(true);
        return t;
    }

}
