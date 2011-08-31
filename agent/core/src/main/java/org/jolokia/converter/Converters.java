package org.jolokia.converter;

import java.util.Map;


import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.converter.object.StringToObjectConverter;
import org.jolokia.converter.object.StringToOpenTypeConverter;
import org.jolokia.util.ConfigKey;

/**
 * Wrapper class holding various converters
 *
 * @author roland
 * @since 02.08.11
 */
public class Converters {

    // From object to json:
    private ObjectToJsonConverter toJsonConverter;

    // From string/json to object:
    private StringToObjectConverter toObjectConverter;
    private StringToOpenTypeConverter toOpenTypeConverter;

    /**
     * Create converters (string-to-object, string-to-openType and object-to-json)
     *
     * @param pConfig configuration for converters
     */
    public Converters(Map<ConfigKey, String> pConfig) {
        toObjectConverter = new StringToObjectConverter();
        toOpenTypeConverter = new StringToOpenTypeConverter(toObjectConverter);
        toJsonConverter = new ObjectToJsonConverter(toObjectConverter,pConfig);
    }

    /**
     * Get the converter which is repsonsible for converting objects to JSON
     *
     * @return converter
     */
    public ObjectToJsonConverter getToJsonConverter() {
        return toJsonConverter;
    }

    /**
     * Get the converter which translates a given string value to a certain object (depending
     * on type)
     *
     * @return converter
     */
    public StringToObjectConverter getToObjectConverter() {
        return toObjectConverter;
    }

    /**
     * Get the converter for strings to {@link javax.management.openmbean.OpenType}
     *
     * @return converter
     */
    public StringToOpenTypeConverter getToOpenTypeConverter() {
        return toOpenTypeConverter;
    }
}
