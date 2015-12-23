package org.jolokia.server.core.util;

import java.util.concurrent.ThreadFactory;

/**
 * Thread factory for creating daemon threads only
 *
 * @author roland
 * @since 28.02.14
 */
public class DaemonThreadFactory implements ThreadFactory {

    /** {@inheritDoc} */
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    }

}
