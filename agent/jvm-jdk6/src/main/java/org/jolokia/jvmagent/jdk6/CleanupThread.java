package org.jolokia.jvmagent.jdk6;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import com.sun.net.httpserver.HttpServer;
import org.omg.PortableInterceptor.ACTIVE;

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
    private boolean active = true;

    CleanUpThread(HttpServer pServer,ThreadGroup pThreadGroup) {
        super("Jolokia Agent Cleanup Thread");
        server = pServer;
        threadGroup = pThreadGroup;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            boolean retry = true;
            while(retry && active) {
                // Get all threads, wait for 'foreign' (== not our own threads)
                // and when all finished, finish as well. This is in order to avoid
                // hanging endless because the HTTP Server thread cant be set into
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

    public void stopServer() {
        active = false;
        interrupt();
    }
}

