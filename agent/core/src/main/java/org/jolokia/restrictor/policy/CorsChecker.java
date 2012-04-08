package org.jolokia.restrictor.policy;

import java.util.regex.Pattern;

import org.w3c.dom.*;

/**
 * Check for location restrictions for CORS based cross browser platform requests
 *
 * @author roland
 * @since 07.04.12
 */
public class CorsChecker extends AbstractChecker<String> {

    Pattern[] patterns;

    public CorsChecker(Document pDoc) {
        NodeList nodes = pDoc.getElementsByTagName("allow-origin");
        patterns = new Pattern[nodes.getLength()];
        for (int i = 0;i<nodes.getLength();i++) {
            Node node = nodes.item(i);
            String p = node.getTextContent().trim().toLowerCase();
            p = Pattern.quote(p).replace("*","\\E.*\\Q");
            patterns[i] = Pattern.compile("^" + p + "$");
        }
    }

    @Override
    public boolean check(String pArg) {
        if (patterns.length == 0) {
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
