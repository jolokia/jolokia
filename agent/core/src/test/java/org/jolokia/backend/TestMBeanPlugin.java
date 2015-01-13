package org.jolokia.backend;/*
 * 
 * Copyright 2014 Roland Huss
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

import java.io.IOException;
import java.util.*;

import javax.management.*;
import javax.management.openmbean.CompositeData;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.backend.plugin.MBeanPlugin;
import org.jolokia.backend.plugin.MBeanPluginContext;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 13/01/15
 */
public class TestMBeanPlugin implements MBeanPlugin {

    private static boolean initCalled = false;

    public void init(MBeanPluginContext ctx, Map map) throws JMException {
        assertNotNull(ctx);
        if (map != null) {
            assertEquals(map.size(), 1);
            assertEquals(map.get("path"), "/tmp");
        }
        ctx.registerMBean(new Test(ctx), "jolokia:type=plugin,name=test");

        initCalled = true;
    }

    private void checkJmx(MBeanPluginContext ctx) throws MalformedObjectNameException, InstanceAlreadyExistsException, NotCompliantMBeanException {
        ctx.registerMBean(new Test(ctx),"jolokia:type=plugin,name=test");
    }

    public static boolean isInitCalled() {
        return initCalled;
    }

    public String getId() {
        return "test";
    }

    public interface TestMBean {
        int convert(String arg);
        void error() throws Exception;
        long getMemoryUsed() throws MalformedObjectNameException, IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException;
        long getMemoryMax() throws MalformedObjectNameException, MBeanException, IOException, ReflectionException;
    }

    public class Test implements TestMBean {

        MBeanPluginContext ctx;

        public Test(MBeanPluginContext ctx) {
            this.ctx = ctx;
        }

        public int convert(String arg) {
            return Integer.parseInt(arg);
        }

        public void error() throws Exception {
            throw new Exception();
        }

        public long getMemoryUsed() throws MalformedObjectNameException, IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
            Set<ObjectName> names = ctx.queryNames(new ObjectName("java.lang:type=Memory"));
            ObjectName memName = names.iterator().next();
            assertEquals(names.size(),1);
            return ctx.call(memName, new MBeanServerExecutor.MBeanAction<Long>() {
                public Long execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs) throws ReflectionException, InstanceNotFoundException, IOException, MBeanException, AttributeNotFoundException {
                    CompositeData data = (CompositeData) pConn.getAttribute(pName,"HeapMemoryUsage");
                    return (Long) data.get("used");
                }
            });
        }

        public long getMemoryMax() throws MalformedObjectNameException, MBeanException, IOException, ReflectionException {
            final List<Long> mems = new ArrayList<Long>();
            ctx.each(new ObjectName("java.lang:type=Memory"), new MBeanServerExecutor.MBeanEachCallback() {
                public void callback(MBeanServerConnection pConn, ObjectName pName) throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                    CompositeData cd = null;
                    try {
                        cd = (CompositeData) pConn.getAttribute(pName,"HeapMemoryUsage");
                        mems.add((Long) cd.get("max"));
                    } catch (AttributeNotFoundException e) {
                        // Ignore it ...
                    }
                }
            });
            assertEquals(mems.size(), 1);
            return mems.get(0);
        }
    }
}
