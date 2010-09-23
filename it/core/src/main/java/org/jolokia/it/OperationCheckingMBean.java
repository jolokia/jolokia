package org.jolokia.it;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * @author roland
 * @since Jun 30, 2009
 */
public interface OperationCheckingMBean {

    void reset();

    int fetchNumber(String arg);

    int overloadedMethod(String arg);

    int overloadedMethod(String arg,int arg2);

    int overloadedMethod(String[] arg);

    boolean nullArgumentCheck(String arg1,Object arg2);

    boolean emptyStringArgumentCheck(String arg1);

    String arrayArguments(String args[], String extra);
}
