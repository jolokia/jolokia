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
package org.jolokia.client.request;

import java.util.ArrayList;
import java.util.List;
import javax.management.ObjectName;

import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.json.JSONObject;

/**
 * <p>A base request class for Jolokia operations that deal with MBean represented by {@link ObjectName}.
 * Such name may represent single MBean or {@link ObjectName#apply(ObjectName) more MBeans matched by a pattern}.</p>
 *
 * <p>JSON form of {@link HttpMethod#POST} is:<pre>{@code
 * {
 *     "type": "operation-id",
 *     "mbean": "object-name",
 *     "target": {
 *         "url": "target remote JMX URI",
 *         "user": "remote JMX connector username",
 *         "password": "remote JMX connector password"
 *     }
 * }
 * }</pre>
 * {@code target} field is optional.
 * </p>
 *
 * <p>For {@link HttpMethod#GET}, "type" is encoded as the first path segment after Jolokia Agent base URI and
 * "mbean" is encoded as second URI path segment. Unlike as in {@link JolokiaListRequest}, object name is
 * encoded in single path segment (list operations may filter by domain only, so it encodes the {@link ObjectName}
 * into two segments).</p>
 *
 * @author roland
 * @since Apr 24, 2010
 */
public abstract class JolokiaMBeanRequest extends JolokiaRequest {

    /** Name or pattern used by this request */
    private final ObjectName objectName;

    /**
     * Creates an MBean-related {@link JolokiaRequest}
     *
     * @param pType
     * @param pMBeanName
     * @param pTargetConfig
     */
    protected JolokiaMBeanRequest(JolokiaOperation pType, ObjectName pMBeanName, JolokiaTargetConfig pTargetConfig) {
        super(pType, pTargetConfig);
        objectName = pMBeanName;
    }

    /**
     * Get the object name for the MBean on which this request operates
     *
     * @return MBean's name
     */
    public ObjectName getObjectName() {
        return objectName;
    }

    /**
     * <p>First path element of all {@link JolokiaMBeanRequest MBean requests} is always the canonical name of the MBean
     * encoded as single path element.</p>
     *
     * <p>Note - this is different from {@link JolokiaListRequest}, where {@link ObjectName#getDomain()} and
     * {@link ObjectName#getCanonicalKeyPropertyListString()} are encoded as two path segments.</p>
     *
     * @return
     */
    @Override
    public List<String> getRequestParts() {
        List<String> ret = new ArrayList<>();
        // whatever the object name, it'll be escaped when creating GET request
        ret.add(objectName.getCanonicalName());
        return ret;
    }

    @Override
    public JSONObject toJson() {
        JSONObject ret = super.toJson();
        ret.put("mbean", objectName.getCanonicalName());
        return ret;
    }

}
