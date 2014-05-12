package org.jolokia.osgi;

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

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

public interface LoginContextFactory {

    LoginContext createLoginContext(java.lang.String name, javax.security.auth.Subject subject, javax.security.auth.callback.CallbackHandler callbackHandler) throws LoginException;
}
