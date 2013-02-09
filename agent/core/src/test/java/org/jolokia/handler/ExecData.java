package org.jolokia.handler;

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

import java.util.*;

import org.jolokia.util.DateUtil;
import org.json.simple.JSONObject;

/**
 * @author roland
 * @since 19.04.11
 */
public class ExecData implements ExecDataMBean {
    public void simple() {}

    public Date simpleWithArguments(String p1) {
        return DateUtil.fromISO8601(p1);
    }

    public Map withArgs(long p1, List p2, boolean p3) {
        JSONObject ret = new JSONObject();
        ret.put("long",p1);
        ret.put("list",p2);
        ret.put("boolean",p3);
        return ret;
    }

    public int overloaded(int p1) {
        return 1;
    }

    public int overloaded(int p1, String p2) {
        return 2;
    }

    public int overloaded(boolean p1) {
        return 3;
    }

}
