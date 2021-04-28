package org.jolokia.it;

/*
 * Copyright 2009-2021 Roland Huss
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.*;
import javax.management.openmbean.TabularData;

/**
 * @author roland
 * @since Jun 30, 2009
 */
public class OperationChecking implements OperationCheckingMBean,MBeanRegistration {

    private int counter = 0;
    private String domain;

    public OperationChecking(String pDomain) {
        domain = pDomain;
    }

    public void reset() {
        counter = 0;
    }

    public int fetchNumber(String arg) {
        if ("inc".equals(arg)) {
            return counter++;
        } else {
            throw new IllegalArgumentException("Invalid arg " + arg);
        }
    }

    public void throwCheckedException() throws Exception {
        throw new Exception("Inner exception");
    }

    public void throwRuntimeException() {
        throw new IllegalArgumentException("Illegal Argument", new IllegalStateException("Illegal State"));
    }

    public boolean nullArgumentCheck(String arg1,Object arg2) {
        return arg1 == null && arg2 == null;
    }

    public boolean emptyStringArgumentCheck(String arg1) {
        return arg1 != null && arg1.length() == 0;
    }

    public String arrayArguments(String args[], String extra) {
        return args[0];
    }

    public Object objectArrayArg(Object[] args) {
        if (args == null) {
            return null;
        } else {
            return args[0];
        }
    }

    public Object listArgument(List arg) {
        if (arg == null) {
            return null;
        }
        return arg.get(0);
    }

    public Boolean booleanArguments(boolean arg1, Boolean arg2) {
        if (arg2 == null) {
            return null;
        }
        return arg1 && arg2;
    }

    public Map mapArgument(Map arg) {
        return arg;
    }

    public int intArguments(int arg1, Integer arg2) {
        if (arg2 == null) {
            return -1;
        }
        return arg1 + arg2;
    }

    public double doubleArguments(double arg1, Double arg2) {
        if (arg2 == null) {
            return -1.0;
        }
        return arg1 + arg2;
    }

    public BigDecimal addBigDecimal(int first,BigDecimal second) {
        return second.add(BigDecimal.valueOf(first));
    }

    public TabularData update(String name, TabularData data) {
        return data;
    }

    public int overloadedMethod() {
        return 0;
    }

    public int overloadedMethod(String arg) {
        return 1;
    }

    public int overloadedMethod(String arg, int arg2) {
        return 2;
    }

    public int overloadedMethod(String[] arg) {
        return 3;
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        return new ObjectName(domain + ":type=operation");

    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }

    public int sleep(int seconds) throws InterruptedException {
        synchronized(this) {
            this.wait(seconds * 1000L);
        }
        return seconds;
    }

    public String echo(String pEcho) {
        return pEcho;
    }

    public TimeUnit findTimeUnit(TimeUnit unit) {
        return unit;
    }
}
