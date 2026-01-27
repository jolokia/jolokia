/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.client.exception;

/**
 * HTTP exception after JolokiaClient received the response with http code different than 200.
 */
public class JolokiaHttpException extends RuntimeException {

    private final int status;

    public JolokiaHttpException(String message, int httpStatus) {
        super(message);
        this.status = httpStatus;
    }

    public int getHttpStatus() {
        return status;
    }

}
