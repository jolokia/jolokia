package org.jolokia.server.core.http;/*
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

/**
 * Thread local holder providing a possible back channel for the current request.
 * Note if the
 *
 * @author roland
 * @since 19/10/15
 */
public class BackChannelHolder {

    private static final ThreadLocal<BackChannel> backChannelThreadLocal = new ThreadLocal<>();

    /**
     * Get the currently set back channel
     *
     * @return the back channel acquired
     */
    public static BackChannel get() {
        synchronized (backChannelThreadLocal) {
            return backChannelThreadLocal.get();
        }
    }

    /**
     * Set the back channel for this request.
     */
    public static void set(BackChannel pBackChannel) {
        synchronized (backChannelThreadLocal) {
            backChannelThreadLocal.set(pBackChannel);
        }
    }

    /**
     * Cleanup
     */
    public static void remove() {
        synchronized (backChannelThreadLocal) {
            backChannelThreadLocal.remove();
        }
    }
}
