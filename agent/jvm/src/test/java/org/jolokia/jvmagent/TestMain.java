package org.jolokia.jvmagent;

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

/**
 * @author roland
 * @since Mar 3, 2010
 */
public class TestMain {

    public static void main(String[] args) throws InterruptedException {
        for (int i = 1;i< 10;i++) {
            Thread thread = new Thread("Bla " + i) {
                @Override
                public void run() {
                    try {
                        sleep((long) (Math.random() * 20000));
                    } catch (InterruptedException e) {
                    }
                }
            };
            thread.start();
        }
    }
}
