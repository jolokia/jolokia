package org.jolokia.restrictor.policy;

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

    private List<Pattern> patterns;

    public CorsChecker(Document pDoc) {
        NodeList corsNodes = pDoc.getElementsByTagName("cors");
        if (corsNodes.getLength() > 0) {
            patterns = new ArrayList<Pattern>();
            for (int i = 0; i < corsNodes.getLength(); i++) {
                Node corsNode = corsNodes.item(i);
                NodeList nodes = corsNode.getChildNodes();
                for (int j = 0;j <nodes.getLength();j++) {
                    Node node = nodes.item(j);
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    assertNodeName(node,"allow-origin");
                    String p = node.getTextContent().trim().toLowerCase();
                    p = Pattern.quote(p).replace("*","\\E.*\\Q");
                    patterns.add(Pattern.compile("^" + p + "$"));
                }
            }
        }
    }

    @Override
    public boolean check(String pArg) {
        if (patterns == null || patterns.size() == 0) {
            return true;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(pArg).matches()) {
                return true;
            }
        }
        return false;
    }
}
