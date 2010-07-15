package org.jolokia.jvmagent.jdk6;

import com.sun.net.httpserver.HttpServer;

/**
 * Thread for stopping the HttpServer as soon as every non-daemon
 * thread has exited. This thread was inspired by the ideas from
 * Daniel Fuchs (although the implementation is different)
 * (http://blogs.sun.com/jmxetc/entry/more_on_premain_and_jmx)
 *
 * @author roland
 * @since Mar 3, 2010
 */
class CleanUpThread extends Thread {

    private HttpServer server;
    private ThreadGroup threadGroup;

    CleanUpThread(HttpServer pServer,ThreadGroup pThreadGroup) {
        super("J4P Agent Cleanup Thread");
        server = pServer;
        threadGroup = pThreadGroup;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            boolean retry = true;
            while(retry) {
                // Get all threads, wait for 'foreign' (== not our own threads)
                // and when all finished, finish as well. This is in order to avoid
                // hanging endless because the HTTP Serer thread cant be set into
                // daemon mode
                Thread threads[] = enumerateThreads();
                retry = joinThreads(threads);
            }
        } finally {
            // All non-daemon threads stopped ==> server can be stopped, too
            server.stop(0);
        }
    }


    // Enumerate all active threads
    private Thread[] enumerateThreads() {
        boolean fits = false;
        int inc = 50;
        Thread[] threads = null;
        int nrThreads = 0;
        while (!fits) {
            try {
                threads = new Thread[Thread.activeCount()+inc];
                nrThreads = Thread.enumerate(threads);
                fits = true;
            } catch (ArrayIndexOutOfBoundsException exp) {
                inc += 50;
            }
        }
        // Trim array
        Thread ret[] = new Thread[nrThreads];
        System.arraycopy(threads,0,ret,0,nrThreads);
        return ret;
    }

    // Join threads, return false if only our own threads are left.
    private boolean joinThreads(Thread pThreads[]) {
        for (int i=0;i< pThreads.length;i++) {
            final Thread t = pThreads[i];
            if (t.isDaemon() ||
                    t.getThreadGroup().equals(threadGroup) ||
                    t.getName().startsWith("DestroyJavaVM")) {
                // These are threads which should not prevent the server from stopping.
                continue;
            }
            try {
                t.join();
            } catch (Exception ex) {
                // Ignore that one.
            } finally {
                // We just joined a 'foreign' thread, so we redo the loop
                return true;
            }
        }
        // All 'foreign' threads has finished, hence we are prepared to stop
        return false;
    }
}

