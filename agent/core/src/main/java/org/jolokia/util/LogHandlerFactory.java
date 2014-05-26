package org.jolokia.util;

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

import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;

public class LogHandlerFactory {

    public static LogHandler createLogHandler(String pLogHandlerClass, String pDebug) {
        if (pLogHandlerClass != null) {
            return ClassUtil.newInstance(pLogHandlerClass);
        } else {
            final boolean debug = Boolean.valueOf(pDebug);
            return new LogHandler.StdoutLogHandler(debug);
        }
    }

    public static LogHandler createLogHandler(Configuration pConfig) {
        return createLogHandler(pConfig.get(ConfigKey.LOGHANDLER_CLASS),pConfig.get(ConfigKey.DEBUG));
    }


}
