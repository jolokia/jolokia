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

import org.jolokia.client.EscapeUtil;
import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.response.JolokiaListResponse;
import org.jolokia.json.JSONObject;

/**
 * <p>Request for list JMX information obtained from {@link javax.management.MBeanServerConnection#queryMBeans}.
 * List operation supports {@code path} arguments to retrieve partial/internal information from a list of
 * JSON representation of {@link javax.management.MBeanInfo}.</p>
 *
 * <p>Jolokia Agent treats first two path elements as
 * {@link ObjectName#getDomain()} and {@link ObjectName#getCanonicalKeyPropertyListString()}. If there's only a domain,
 * entire {@link ObjectName} is recreated at server side as {@code <domain>:*}.</p>
 *
 * <p>If 3rd path element is specified, it is used to select single {@code DataUpdater}, where the standard updaters
 * are available for {@link javax.management.MBeanInfo} information like "class", "op", "attr", "desc", ... No more
 * path elements are supported.</p>
 *
 * <p>JSON form of a "list" {@link HttpMethod#POST} is:<pre>{@code
 * {
 *     "type": "list",
 *     "path": "domain" | "domain/keys" | "domain/keys/updater",
 *     "target": {
 *         "url": "target remote JMX URI",
 *         "user": "remote JMX connector username",
 *         "password": "remote JMX connector password"
 *     }
 * }
 * }</pre>
 * There are no new dedicated fields in JSON representation of the "list" requests. However the segments from
 * the {@code path} are interpreted at Jolokia Agent side.</p>
 *
 * @author roland
 * @since 26.03.11
 */
public class JolokiaListRequest extends JolokiaRequest {

    private final List<String> pathElements;

    // normal constructors

    /**
     * Default constructor to be used when all meta information should
     * be fetched. No {@link JolokiaTargetConfig} or path elements are present.
     */
    protected JolokiaListRequest() {
        this(null, (String) null);
    }

    /**
     * Default constructor to be used when all meta information should
     * be fetched. Optionally accepts proxy configuration.
     *
     * @param pTargetConfig proxy target configuration or {@code null} if no proxy should be used
     */
    protected JolokiaListRequest(JolokiaTargetConfig pTargetConfig) {
        this(pTargetConfig, (String) null);
    }

    // constructors with path specified as single argument which should be proper URI path (already escaped)

    /**
     * Constructor using a path to restrict the information returned by the list command
     *
     * @param pPath path into the JSON response, which is JSON representation of {@link javax.management.MBeanInfo}.
     *              The path <strong>must already be properly escaped</strong> when it contains slashes or exclamation marks.
     *              You can use {@link EscapeUtil#escape(String)} in order to escape a single path element.
     */
    public JolokiaListRequest(String pPath) {
        this(null, pPath);
    }

    /**
     * Constructor using a path to restrict the information returned by the list command.
     * Optionally accepts proxy configuration.
     *
     * @param pConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pPath   path into the JSON response. The path <strong>must already be
     *                properly escaped</strong> when it contains slashes or exclamation marks.
     *                You can use {@link EscapeUtil#escape(String)} in order to escape a single path element.
     */
    public JolokiaListRequest(JolokiaTargetConfig pConfig, String pPath) {
        super(JolokiaOperation.LIST, pConfig);
        pathElements = EscapeUtil.splitPath(pPath);
    }

    // constructors with path specified as list of path segments, where each must be already escaped

    /**
     * Constructor using a list of path elements to restrict the information
     *
     * @param pPathElements list of path elements. The elements <strong>must not be escaped</strong>
     */
    public JolokiaListRequest(List<String> pPathElements) {
        this(null, pPathElements);
    }

    /**
     * Constructor using a list of path elements to restrict the information
     *
     * @param pConfig       proxy target configuration or <code>null</code> if no proxy should be used
     * @param pPathElements list of path elements. The elements <strong>must not be escaped</strong>
     */
    public JolokiaListRequest(JolokiaTargetConfig pConfig, List<String> pPathElements) {
        super(JolokiaOperation.LIST, pConfig);
        pathElements = new ArrayList<>(pPathElements);
    }

    // constructors for List request related to single ObjectName, where the name is transformed into
    // a path of 2 elements

    /**
     * Constructor for fetching the meta data of a specific MBean. If the {@link ObjectName#isPattern() name is a pattern},
     * the response may include more MBeans grouped by the domain.
     *
     * @param pObjectName name of MBean for which to fetch the meta data
     */
    public JolokiaListRequest(ObjectName pObjectName) {
        this(null, pObjectName);
    }

    /**
     * Constructor for fetching the meta data of a specific MBean. If the {@link ObjectName#isPattern() name is a pattern},
     * the response may include more MBeans grouped by the domain.
     *
     * @param pConfig     proxy target configuration or <code>null</code> if no proxy should be used
     * @param pObjectName name of MBean for which to fetch the meta data
     */
    public JolokiaListRequest(JolokiaTargetConfig pConfig, ObjectName pObjectName) {
        super(JolokiaOperation.LIST, pConfig);
        pathElements = new ArrayList<>();
        pathElements.add(pObjectName.getDomain());
        pathElements.add(pObjectName.getCanonicalKeyPropertyListString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public JolokiaListResponse createResponse(JSONObject pResponse) {
        return new JolokiaListResponse(this, pResponse);
    }

    @Override
    public List<String> getRequestParts() {
        return pathElements;
    }

    @Override
    public JSONObject toJson() {
        JSONObject ret = super.toJson();
        String pathToUse = EscapeUtil.combinePath(pathElements);
        if (pathToUse != null) {
            ret.put("path", pathToUse);
        }
        return ret;
    }

}
