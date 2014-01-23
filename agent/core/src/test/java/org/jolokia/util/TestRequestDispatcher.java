package org.jolokia.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.management.*;

import org.jolokia.backend.NotChangedException;
import org.jolokia.backend.RequestDispatcher;
import org.jolokia.request.JolokiaRequest;

/**
 * @author roland
 * @since 11.06.13
 */
public class TestRequestDispatcher implements RequestDispatcher {

    private final Map stateMap;

    private TestRequestDispatcher(Map pStateMap) {
        stateMap = pStateMap;
    }

    public Object dispatch(JolokiaRequest pJolokiaRequest) throws AttributeNotFoundException, NotChangedException, ReflectionException, IOException, InstanceNotFoundException, MBeanException {
        if (stateMap != null) {
            return stateMap.get(pJolokiaRequest.toString());
        } else {
            return null;
        }
    }

    public void destroy() {
    }

    public static class Builder {

        JolokiaRequest req;
        Map stateMap = new HashMap();

        public Builder request(JolokiaRequest pReq) {
            req = pReq;
            return this;
        }

        public Builder andReturnMapValue(Object ... pArgs) {
            if (req == null) {
                throw new IllegalArgumentException("No request stored before");
            }
            Map value = new HashMap();
            for (int i = 0; i < pArgs.length; i += 2) {
                value.put(pArgs[i],pArgs[i+1]);
            };
            stateMap.put(req.toString(),value);
            req = null;
            return this;
        }

        public TestRequestDispatcher build() {
            return new TestRequestDispatcher(stateMap);
        }
    }
}
