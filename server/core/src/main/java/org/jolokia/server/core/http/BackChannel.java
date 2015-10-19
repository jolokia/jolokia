package org.jolokia.server.core.http;/*
 * 
 * Copyright 2014 Roland Huss
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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Channel for talking back to the client.
 *
 * @author roland
 * @since 07/07/15
 */
public interface BackChannel {


    /**
     * Open the channel. Note, that a channel which has been already closed cannot be
     * reopened again.
     *
     * @param pParams params for opening the back channel.
     *
     * The following parameter keys should be use for opening:
     *
     * <ul>
     *  <li><strong>backChannel.contentType</strong> - Content-Type to set on the backchannel</li>
     *  <li><strong>backChannel.encoding</strong> - Encoding used for the client communication</li>
     * </ul>
     * @throws IOException if the channel was already closed
     */
    void open(Map<String,?> pParams) throws IOException;

    /**
     * Close the channel. After this no write is allowed anymore
     */
    void close();

    /**
     * Get the write for writing to the client
     *
     * @return writer
     * @throws IOException if the channel is already closed.
     */
    PrintWriter getWriter() throws IOException;
}
