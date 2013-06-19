package org.jolokia.it;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.openmbean.TabularData;

/**
 * @author roland
 * @since Jun 30, 2009
 */
public interface OperationCheckingMBean {

    void reset();

    int fetchNumber(String arg);

    public void throwCheckedException() throws Exception;

    public void throwRuntimeException();

    int overloadedMethod();

    int overloadedMethod(String arg);

    int overloadedMethod(String arg,int arg2);

    int overloadedMethod(String[] arg);

    boolean nullArgumentCheck(String arg1,Object arg2);

    boolean emptyStringArgumentCheck(String arg1);

    String arrayArguments(String args[], String extra);

    Object objectArrayArg(Object[] args);

    Object listArgument(List arg);

    Boolean booleanArguments(boolean arg1, Boolean arg2);

    Map mapArgument(Map arg);

    int intArguments(int arg1, Integer arg2);

    double doubleArguments(double arg1, Double arg2);

    public int sleep(int seconds) throws InterruptedException;

    public String echo(String pEcho);

    TimeUnit findTimeUnit(TimeUnit unit);

    BigDecimal addBigDecimal(int first, BigDecimal second);

    TabularData update(String name,TabularData data);
}
