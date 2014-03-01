package org.jolokia.jvmagent;

import java.util.concurrent.ThreadFactory;

/**
 * Thread factory for creating daemon threads only
 *
 * @author roland
 * @since 28.02.14
 */
class DaemonThreadFactory implements ThreadFactory {

    @Override
    /** {@inheritDoc} */
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    }

}
