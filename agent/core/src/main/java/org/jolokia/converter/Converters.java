package org.jolokia.converter;

import java.util.Map;

import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.converter.object.StringToObjectConverter;
import org.jolokia.converter.object.StringToOpenTypeConverter;
import org.jolokia.util.ConfigKey;

/**
 * @author roland
 * @since 02.08.11
 */
public class Converters {

    // From object to json:
    private ObjectToJsonConverter toJsonConverter;

    // From string/json to object:
    private StringToObjectConverter toObjectConverter;
    private StringToOpenTypeConverter toOpenTypeConverter;

    public Converters(Map<ConfigKey, String> pConfig) {
        toObjectConverter = new StringToObjectConverter();
        toOpenTypeConverter = new StringToOpenTypeConverter(toObjectConverter);
        toJsonConverter = new ObjectToJsonConverter(toObjectConverter,pConfig);
    }

    public ObjectToJsonConverter getToJsonConverter() {
        return toJsonConverter;
    }

    public StringToObjectConverter getToObjectConverter() {
        return toObjectConverter;
    }

    public StringToOpenTypeConverter getToOpenTypeConverter() {
        return toOpenTypeConverter;
    }
}
