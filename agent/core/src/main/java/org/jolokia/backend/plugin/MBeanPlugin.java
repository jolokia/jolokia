package org.jolokia.backend.plugin;

/*
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

import java.util.Map;

import javax.management.JMException;

/**
 * Interface describing a plugin which can be used to register extra MBeans for enhancing the Jolokia API.
 * MBeanPlugins should have a no argument constructor,
 * are looked up from the classpath and should be registered in <code>META-INF/mbean-plugins</code>
 *
 * @author roland
 * @since 12/01/15
 */
public interface MBeanPlugin {

    /**
     * Init method for the plugin. The plugin purpose is to register MBeans and to remember the context so
     * that it can be later used for doing JMX calls. The context should be propagated to the registered MBean so
     * that it can be reused for JMX lookups during its operation.
     *
     * @param ctx the context in order to access JMX
     * @param map configuration specific for this plugin
     */
    void init(MBeanPluginContext ctx, Map map) throws JMException;

    /**
     * Get unique id for this plugin. This id is also used for looking up plugin specific configuration.
     *
     * @return unique id for this plugin
     */
    String getId();
}
