package org.jolokia.service.notif.sse;/*
 * 
 * Copyright 2015 Roland Huss
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import org.jolokia.server.core.http.BackChannel;

/**
 * @author roland
 * @since 14/12/15
 */
class SseHeartBeat implements Runnable {

    private static final byte[] COMMENT_FIELD = ": ".getBytes(StandardCharsets.UTF_8);

    // Scheduler for doing heartbeats
    private final ScheduledExecutorService scheduler;

    // back channel on which to issue the heartbeat
    private final BackChannel backChannel;

    // Heartbeat in seconds
    private int heartBeatPeriod = 10;

    // hearbeat hand
    private Future<?> heartBeat;

    public SseHeartBeat(BackChannel pBackChannel) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.backChannel = pBackChannel;
    }

    void start() {
        scheduleHeartBeat();
    }

    public void stop() {
        if (heartBeat != null) {
            heartBeat.cancel(false);
        }
    }

    void scheduleHeartBeat() {
        synchronized (backChannel) {
            if (!backChannel.isClosed()) {
                heartBeat = scheduler.schedule(this, heartBeatPeriod, TimeUnit.SECONDS);
            }
        }
    }

    public void run() {
        // If the other peer closes the connection, the first
        // flush() should generate a TCP reset that is detected
        // on the second flush()
        try {
            synchronized (backChannel) {
                OutputStream outputStream = backChannel.getOutputStream();
                outputStream.write(COMMENT_FIELD);
                outputStream.write('\r');
                outputStream.flush();
                outputStream.write('\n');
                outputStream.flush();
            }
            // We could write, reschedule heartbeat
            scheduleHeartBeat();
        } catch (IOException exp) {
            // The other peer closed the connection
            backChannel.close();
        }
    }
}
