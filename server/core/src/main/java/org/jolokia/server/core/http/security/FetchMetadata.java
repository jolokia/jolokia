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
package org.jolokia.server.core.http.security;

/**
 * Data related to <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Fetch_metadata">Fetch metadata</a>
 * headers.
 * @param site {@code Sec-Fetch-Site}
 * @param mode {@code Sec-Fetch-Mode}
 * @param dest {@code Sec-Fetch-Dest}
 */
public record FetchMetadata(String site, String mode, String dest) {
}
