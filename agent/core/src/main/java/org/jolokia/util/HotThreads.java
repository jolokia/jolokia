/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jolokia.util;


import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class HotThreads {

    private static final Object mutex = new Object();

    private int busiestThreads = 3;
    private int interval = 500;
    private int threadElementsSnapshotDelay = 10;
    private int threadElementsSnapshotCount = 10;
    private String type = "cpu";
    private boolean ignoreIdleThreads = true;

    public HotThreads interval(int interval) {
        this.interval = interval;
        return this;
    }

    public HotThreads busiestThreads(int busiestThreads) {
        this.busiestThreads = busiestThreads;
        return this;
    }

    public HotThreads ignoreIdleThreads(boolean ignoreIdleThreads) {
        this.ignoreIdleThreads = ignoreIdleThreads;
        return this;
    }

    public HotThreads threadElementsSnapshotDelay(int threadElementsSnapshotDelay) {
        this.threadElementsSnapshotDelay = threadElementsSnapshotDelay;
        return this;
    }

    public HotThreads threadElementsSnapshotCount(int threadElementsSnapshotCount) {
        this.threadElementsSnapshotCount = threadElementsSnapshotCount;
        return this;
    }

    public HotThreads type(String type) {
        if ("cpu".equals(type) || "wait".equals(type) || "block".equals(type)) {
            this.type = type;
        } else {
            throw new IllegalArgumentException("type not supported [" + type + "]");
        }
        return this;
    }

    public String detect() throws Exception {
        synchronized (mutex) {
            return innerDetect();
        }
    }

    private static boolean isIdleThread(ThreadInfo threadInfo) {
        String threadName = threadInfo.getThreadName();

        // NOTE: these are likely JVM dependent
        if (threadName.equals("Signal Dispatcher") ||
                threadName.equals("Finalizer") ||
                threadName.equals("Reference Handler")) {
            return true;
        }

        for (StackTraceElement frame : threadInfo.getStackTrace()) {
            String className = frame.getClassName();
            String methodName = frame.getMethodName();
            if (className.equals("java.util.concurrent.ThreadPoolExecutor") &&
                    methodName.equals("getTask")) {
                return true;
            }
            if (className.equals("sun.nio.ch.SelectorImpl") &&
                    methodName.equals("select")) {
                return true;
            }
            if (className.equals("org.elasticsearch.threadpool.ThreadPool$EstimatedTimeThread") &&
                    methodName.equals("run")) {
                return true;
            }
            if (className.equals("org.elasticsearch.indices.ttl.IndicesTTLService$Notifier") &&
                    methodName.equals("await")) {
                return true;
            }
            if (className.equals("java.util.concurrent.LinkedTransferQueue") &&
                    methodName.equals("poll")) {
                return true;
            }
        }

        return false;
    }

    private String innerDetect() throws Exception {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (threadBean.isThreadCpuTimeSupported() == false) {
            throw new Exception("thread CPU time is not supported on this JDK");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Hot threads at ");
        sb.append(System.currentTimeMillis());
        sb.append(", interval=");
        sb.append(interval);
        sb.append(", busiestThreads=");
        sb.append(busiestThreads);
        sb.append(", ignoreIdleThreads=");
        sb.append(ignoreIdleThreads);
        sb.append(":\n");

        Map<Long, MyThreadInfo> threadInfos = new HashMap<Long, MyThreadInfo>();
        for (long threadId : threadBean.getAllThreadIds()) {
            // ignore our own thread...
            if (Thread.currentThread().getId() == threadId) {
                continue;
            }
            long cpu = threadBean.getThreadCpuTime(threadId);
            if (cpu == -1) {
                continue;
            }
            ThreadInfo info = threadBean.getThreadInfo(threadId, 0);
            if (info == null) {
                continue;
            }
            threadInfos.put(threadId, new MyThreadInfo(cpu, info));
        }
        Thread.sleep(interval);
        for (long threadId : threadBean.getAllThreadIds()) {
            // ignore our own thread...
            if (Thread.currentThread().getId() == threadId) {
                continue;
            }
            long cpu = threadBean.getThreadCpuTime(threadId);
            if (cpu == -1) {
                threadInfos.remove(threadId);
                continue;
            }
            ThreadInfo info = threadBean.getThreadInfo(threadId, 0);
            if (info == null) {
                threadInfos.remove(threadId);
                continue;
            }
            MyThreadInfo data = threadInfos.get(threadId);
            if (data != null) {
                data.setDelta(cpu, info);
            } else {
                threadInfos.remove(threadId);
            }
        }
        // sort by delta CPU time on thread.
        List<MyThreadInfo> hotties = new ArrayList<MyThreadInfo>(threadInfos.values());
        final int busiestThreads = Math.min(this.busiestThreads, hotties.size());

        Collections.sort(hotties, new Comparator<MyThreadInfo>() {
            public int compare(MyThreadInfo o1, MyThreadInfo o2) {
                if ("cpu".equals(type)) {
                    return (int) (o2.cpuTime - o1.cpuTime);
                } else if ("wait".equals(type)) {
                    return (int) (o2.waitedTime - o1.waitedTime);
                } else if ("block".equals(type)) {
                    return (int) (o2.blockedTime - o1.blockedTime);
                }
                throw new IllegalArgumentException("expected thread type to be either 'cpu', 'wait', or 'block', but was " + type);
            }
        });
        // analyse N stack traces for M busiest threads
        long[] ids = new long[busiestThreads];
        for (int i = 0; i < busiestThreads; i++) {
            MyThreadInfo info = hotties.get(i);
            ids[i] = info.info.getThreadId();
        }
        ThreadInfo[][] allInfos = new ThreadInfo[threadElementsSnapshotCount][];
        for (int j = 0; j < threadElementsSnapshotCount; j++) {
            // NOTE, javadoc of getThreadInfo says: If a thread of the given ID is not alive or does not exist,
            // null will be set in the corresponding element in the returned array. A thread is alive if it has
            // been started and has not yet died.
            allInfos[j] = threadBean.getThreadInfo(ids, Integer.MAX_VALUE);
            Thread.sleep(threadElementsSnapshotDelay);
        }
        for (int t = 0; t < busiestThreads; t++) {
            long time = 0;
            if ("cpu".equals(type)) {
                time = hotties.get(t).cpuTime;
            } else if ("wait".equals(type)) {
                time = hotties.get(t).waitedTime;
            } else if ("block".equals(type)) {
                time = hotties.get(t).blockedTime;
            }
            String threadName = null;
            for (ThreadInfo[] info : allInfos) {
                if (info != null && info[t] != null) {
                    if (ignoreIdleThreads && isIdleThread(info[t])) {
                        info[t] = null;
                        continue;
                    }
                    threadName = info[t].getThreadName();
                    break;
                }
            }
            if (threadName == null) {
                continue; // thread is not alive yet or died before the first snapshot - ignore it!
            }
            double percent = (((double) time) / TimeUnit.MILLISECONDS.toNanos(interval)) * 100;
            sb.append(String.format(Locale.ROOT, "%n%4.1f%% (%s out of %s) %s usage by thread '%s'%n", percent, TimeUnit.NANOSECONDS.toMillis(time), interval, type, threadName));
            // for each snapshot (2nd array index) find later snapshot for same thread with max number of
            // identical StackTraceElements (starting from end of each)
            boolean[] done = new boolean[threadElementsSnapshotCount];
            for (int i = 0; i < threadElementsSnapshotCount; i++) {
                if (done[i]) continue;
                int maxSim = 1;
                boolean[] similars = new boolean[threadElementsSnapshotCount];
                for (int j = i + 1; j < threadElementsSnapshotCount; j++) {
                    if (done[j]) continue;
                    int similarity = similarity(allInfos[i][t], allInfos[j][t]);
                    if (similarity > maxSim) {
                        maxSim = similarity;
                        similars = new boolean[threadElementsSnapshotCount];
                    }
                    if (similarity == maxSim) similars[j] = true;
                }
                // print out trace maxSim levels of i, and mark similar ones as done
                int count = 1;
                for (int j = i + 1; j < threadElementsSnapshotCount; j++) {
                    if (similars[j]) {
                        done[j] = true;
                        count++;
                    }
                }
                if (allInfos[i][t] != null) {
                    final StackTraceElement[] show = allInfos[i][t].getStackTrace();
                    if (count == 1) {
                        sb.append(String.format(Locale.ROOT, "  unique snapshot%n"));
                        for (int l = 0; l < show.length; l++) {
                            sb.append(String.format(Locale.ROOT, "    %s%n", show[l]));
                        }
                    } else {
                        sb.append(String.format(Locale.ROOT, "  %d/%d snapshots sharing following %d elements%n", count, threadElementsSnapshotCount, maxSim));
                        for (int l = show.length - maxSim; l < show.length; l++) {
                            sb.append(String.format(Locale.ROOT, "    %s%n", show[l]));
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    private static final StackTraceElement[] EMPTY = new StackTraceElement[0];

    private int similarity(ThreadInfo threadInfo, ThreadInfo threadInfo0) {
        StackTraceElement[] s1 = threadInfo == null ? EMPTY : threadInfo.getStackTrace();
        StackTraceElement[] s2 = threadInfo0 == null ? EMPTY : threadInfo0.getStackTrace();
        int i = s1.length - 1;
        int j = s2.length - 1;
        int rslt = 0;
        while (i >= 0 && j >= 0 && s1[i].equals(s2[j])) {
            rslt++;
            i--;
            j--;
        }
        return rslt;
    }


    class MyThreadInfo {
        long cpuTime;
        long blockedCount;
        long blockedTime;
        long waitedCount;
        long waitedTime;
        boolean deltaDone;
        ThreadInfo info;

        MyThreadInfo(long cpuTime, ThreadInfo info) {
            blockedCount = info.getBlockedCount();
            blockedTime = info.getBlockedTime();
            waitedCount = info.getWaitedCount();
            waitedTime = info.getWaitedTime();
            this.cpuTime = cpuTime;
            this.info = info;
        }

        void setDelta(long cpuTime, ThreadInfo info) {
            if (deltaDone) throw new IllegalStateException("setDelta already called once");
            blockedCount = info.getBlockedCount() - blockedCount;
            blockedTime = info.getBlockedTime() - blockedTime;
            waitedCount = info.getWaitedCount() - waitedCount;
            waitedTime = info.getWaitedTime() - waitedTime;
            this.cpuTime = cpuTime - this.cpuTime;
            deltaDone = true;
            this.info = info;
        }
    }
}

