/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.discovery.file;

import org.jolokia.discovery.AgentDetails;
import org.jolokia.discovery.DiscoveryListener;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple file based discovery implementation which just writes a file per agent into
 * a jolokia discovery folder on the disk
 */
public class FileDiscovery implements FileDiscoveryMXBean, DiscoveryListener {
    public static final String DEFAULT_TMP_DIR = "/tmp";
    public static final String JOLOKIA_DISCOVERY_FOLDER = "jolokiaDiscovery";
    public static final String JOLOKIA_FILE_EXTENSION = ".jolokia";

    private static FileDiscovery instance;

    private final File jolokiaDiscoverDirectory;
    private AtomicBoolean started = new AtomicBoolean(false);
    private boolean keepAlive;
    private Timer timer;
    private boolean ownTimer;
    private Map<AgentDetails, File> detailsToFileMap = new ConcurrentHashMap<AgentDetails, File>();
    private Map<AgentDetails, TimerTask> timerTasks = new ConcurrentHashMap<AgentDetails, TimerTask>();
    private long keepAlivePeriod = 90 * 1000;
    private long updateFilePeriod = 30 * 1000;
    private TimerTask keepAliveTask;
    private boolean registerInJmx = true;
    private MBeanServer mBeanServer;
    private ObjectName objectName;

    /**
     * Returns the singleton instance if its available
     */
    public static FileDiscovery getInstance() {
        return instance;
    }

    public FileDiscovery() {
        instance = this;
        String tmpDir = DEFAULT_TMP_DIR;
        try {
            System.getProperty("java.io.tmpdir", DEFAULT_TMP_DIR);
        } catch (Exception e) {
            // ignore
        }
        jolokiaDiscoverDirectory = new File(tmpDir, JOLOKIA_DISCOVERY_FOLDER);
        jolokiaDiscoverDirectory.mkdirs();
    }

    public void onAgentStarted(final AgentDetails details) {
        final File file = createJolokiaFile(details);
        if (file != null) {
            checkStarted();
            file.deleteOnExit();
            detailsToFileMap.put(details, file);
            writeAgentDetailsFile(file, details);

            if (isKeepAlive()) {
                if (updateFilePeriod > 0) {
                    TimerTask task = new TimerTask() {
                        @Override
                        public void run() {
                            writeAgentDetailsFile(file, details);
                        }
                    };
                    timerTasks.put(details, task);
                    getTimer().scheduleAtFixedRate(task, updateFilePeriod, updateFilePeriod);
                }
                if (keepAlivePeriod > 0 && keepAliveTask == null) {
                    keepAliveTask = new TimerTask() {
                        @Override
                        public void run() {
                            removeOldFiles();
                        }
                    };
                    getTimer().scheduleAtFixedRate(keepAliveTask, keepAlivePeriod, keepAlivePeriod);
                }
            }
        }
    }

    public void onAgentStopped(AgentDetails details) {
        File file = detailsToFileMap.remove(details);
        if (file == null) {
            file = createJolokiaFile(details);
        }
        if (file != null) {
            file.delete();
        }
        if (detailsToFileMap.isEmpty()) {
            // lets stop the timer
            stop();
        }
    }

    protected void checkStarted() {
        // TODO we may wish to add a more explicit start/stop lifecycle for this bean
        // to take part of the usual JMX registration code?
        if (started.compareAndSet(false, true)) {
            // lets register in JMX
            if (mBeanServer == null) {
                mBeanServer = ManagementFactory.getPlatformMBeanServer();
            }
            try {
                objectName = new ObjectName(FileDiscoveryMXBean.OBJECT_NAME);
                if (!mBeanServer.isRegistered(objectName)) {
                    mBeanServer.registerMBean(this, objectName);
                }
            } catch (Exception e) {
                System.out.println("Failed to register " + this + " in JMX: " + e);
            }
        }
    }


    protected void stop() {
        try {
            if (keepAliveTask != null) {
                keepAliveTask.cancel();
            }
            for (TimerTask timerTask : timerTasks.values()) {
                timerTask.cancel();
            }
            if (ownTimer) {
                timer.cancel();
            }
            if (objectName != null && mBeanServer != null) {
                try {
                    mBeanServer.unregisterMBean(objectName);
                } catch (Exception e) {
                    // ignore
                }
            }
        } finally {
            timer = null;
            keepAliveTask = null;
            ownTimer = false;
            timerTasks.clear();
            started.set(false);
        }
    }

    public List<AgentDetails> findAgents() {
        List<AgentDetails> answer = new ArrayList<AgentDetails>();
        File[] files = getJolokiaFiles();
        for (File file : files) {
            if (file.isFile() && file.length() > 0) {
                AgentDetails details = readAgentDetailsFile(file);
                if (details != null) {
                    answer.add(details);
                }
            }
        }
        return answer;
    }

    /**
     * Returns all the current jolokia agent files
     */
    protected File[] getJolokiaFiles() {
        File[] answer = jolokiaDiscoverDirectory.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(JOLOKIA_FILE_EXTENSION);
            }
        });
        if (answer == null) {
            answer = new File[0];
        }
        return answer;
    }

    /**
     * Removes any jolokia files which are too old (modified before the keepalive timer period
     */
    protected void removeOldFiles() {
        long oldModifiedTime = System.currentTimeMillis() - getKeepAlivePeriod();
        File[] files = getJolokiaFiles();
        for (File file : files) {
            if (file.isFile()) {
                long modified = file.lastModified();
                if (modified > 0 && modified < oldModifiedTime && !detailsToFileMap.containsValue(file)) {
                    System.out.println("Removing old jolokia file: " + file.getName()
                            + " as modified at " + new Date(modified));

                    file.delete();
                }
            }
        }
    }

    protected File createJolokiaFile(AgentDetails details) {
        String location = details.getLocation();
        try {
            URL url = new URL(location);
            String fileName = url.getHost() + "." + url.getPort() + JOLOKIA_FILE_EXTENSION;
            return new File(jolokiaDiscoverDirectory, fileName);
        } catch (MalformedURLException e) {
            System.out.println("Failed to parse location: " + location);
            return null;
        }
    }

    protected AgentDetails readAgentDetailsFile(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            try {
                String location = reader.readLine();
                if (location == null) {
                    return null;
                }
                String name = reader.readLine();
                return new AgentDetails(location, name);
            } finally {
                try {
                    reader.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + file);
        }
    }

    protected void writeAgentDetailsFile(File file, AgentDetails details) {
        try {
            FileWriter writer = new FileWriter(file);
            try {
                writer.append(details.getLocation());
                writer.append("\n");
                writer.append(details.getName());
                writer.append("\n");
            } finally {
                try {
                    writer.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + file);
        }
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Enables or disables the keep alive logic (whereby the jolokia files are updated
     * periodically and old files are removed
     */
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    /**
     * Returns the current timer, lazy creating a new one if required
     */
    public Timer getTimer() {
        if (timer == null) {
            boolean daemon = true;
            timer = new Timer("Jolokia FileDiscovery KeepAlive", daemon);
            ownTimer = true;
        }
        return timer;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public long getKeepAlivePeriod() {
        return keepAlivePeriod;
    }

    public void setKeepAlivePeriod(long keepAlivePeriod) {
        this.keepAlivePeriod = keepAlivePeriod;
    }

    public boolean isRegisterInJmx() {
        return registerInJmx;
    }

    public void setRegisterInJmx(boolean registerInJmx) {
        this.registerInJmx = registerInJmx;
    }

    public long getUpdateFilePeriod() {
        return updateFilePeriod;
    }

    public void setUpdateFilePeriod(long updateFilePeriod) {
        this.updateFilePeriod = updateFilePeriod;
    }

    public MBeanServer getmBeanServer() {
        return mBeanServer;
    }

    public void setmBeanServer(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
    }
}
