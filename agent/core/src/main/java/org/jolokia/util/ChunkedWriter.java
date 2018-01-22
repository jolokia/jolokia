/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.UnsupportedCharsetException;

import sun.nio.cs.StreamEncoder;

public class ChunkedWriter extends Writer {

   public static final char[] UTF8_TERMINATE_CHUNK = {};

   private StreamEncoder delegate;

   private boolean isOpen = true;

   public ChunkedWriter(OutputStream stream, String charset) {
      try {
         delegate = StreamEncoder.forOutputStreamWriter(stream, stream, charset);
      } catch (UnsupportedEncodingException e) {
         throw new UnsupportedCharsetException(charset);
      }
   }

   @Override
   public void write(char[] cbuf, int off, int len) throws IOException {
      delegate.write(cbuf, off, len);
   }

   @Override
   public void flush() throws IOException {
      delegate.flush();
   }

   @Override
   public void close() throws IOException {
      delegate.close();
      isOpen = false;
   }

   public boolean isOpen() { return isOpen; }

   public void write(int c) throws IOException {
      delegate.write(c);
   }

   public void write(String str, int off, int len) throws IOException {
      delegate.write(str, off, len);
   }
}
