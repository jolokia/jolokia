package org.jolokia.server.core.request;

import java.util.HashMap;
import java.util.Map;

import org.jolokia.server.core.config.ConfigKey;

/**
 * Used for testing, only modifies visibility of the constructor
 * @author roland
 * @since 30.04.13
 */
public class TestProcessingParameters extends ProcessingParameters {

    public TestProcessingParameters(Map<ConfigKey, String> pConfig) {
        super(pConfig);
    }

    public TestProcessingParameters() {
        super(new HashMap<>());
    }

    public static ProcessingParameters create(Object ... pParams) {
        Map<ConfigKey,String> cfg = new HashMap<>();
        for (int i = 0; i < pParams.length; i+=2) {
            cfg.put((ConfigKey) pParams[i],(String) pParams[i+1]);
        }
        return new TestProcessingParameters(cfg);
    }
}
