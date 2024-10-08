package org.jolokia.server.core.restrictor.policy;

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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.w3c.dom.*;

/**
 * Check for location restrictions for CORS based cross browser platform requests
 *
 * @author roland
 * @since 07.04.12
 */
public class CorsChecker extends AbstractChecker<String> {

    private boolean strictChecking = false;
    private boolean ignoreScheme = false;

    private List<Pattern> patterns;

    /**
     * Constructor buiilding up this checker from the XML document provided.
     * CORS sections look like
     * <pre>
     *     &lt;cors&gt;
     *       &lt;allow-origin&gt;http://jolokia.org&lt;/allow-origin&gt;
     *       &lt;allow-origin&gt;*://*.jmx4perl.org&lt;/allow-origin&gt;
     *
     *       &lt;strict-checking/&gt;
     *       &lt;ignore-scheme/&gt;
     *     &lt;/cors&gt;
     * </pre>
     *
     * @param pDoc the overall policy documents
     */
    public CorsChecker(Document pDoc) {
        NodeList corsNodes = pDoc.getElementsByTagName("cors");
        if (corsNodes.getLength() > 0) {
            patterns = new ArrayList<>();
            for (int i = 0; i < corsNodes.getLength(); i++) {
                Node corsNode = corsNodes.item(i);
                NodeList nodes = corsNode.getChildNodes();
                for (int j = 0;j <nodes.getLength();j++) {
                    Node node = nodes.item(j);
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    assertNodeName(node,"allow-origin", "strict-checking", "ignore-scheme");
                    if (node.getNodeName().equals("allow-origin")) {
                        String p = node.getTextContent().trim().toLowerCase();
                        p = Pattern.quote(p).replace("*", "\\E.*\\Q");
                        patterns.add(Pattern.compile("^" + p + "$"));
                    } else if (node.getNodeName().equals("strict-checking")) {
                        strictChecking = true;
                    } else if (node.getNodeName().equals("ignore-scheme")) {
                        ignoreScheme = true;
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean check(String pArg) {
        return check(pArg, false);
    }

    public boolean check(String pOrigin, boolean pOnlyWhenStrictCheckingIsEnabled) {
        // Method called during strict checking, but we have not configured that
        // So the check passes always.
        if (pOnlyWhenStrictCheckingIsEnabled && !strictChecking) {
            return true;
        }

        // If strict checking is enabled but Origin is null, then don't allow. This can
        // be the case when both Referer: and Origin: are set to null
        if (pOrigin == null && strictChecking) {
            return false;
        }

        if (patterns == null || patterns.isEmpty() || pOrigin == null) {
            return true;
        }

        for (Pattern pattern : patterns) {
            if (pattern.matcher(pOrigin).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean ignoreScheme() {
        return ignoreScheme;
    }
}
