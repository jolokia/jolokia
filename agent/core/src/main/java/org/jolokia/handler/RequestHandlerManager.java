package org.jolokia.handler;

import org.jolokia.JmxRequest;
import org.jolokia.config.Restrictor;
import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.ObjectToJsonConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author roland
 * @since Nov 13, 2009
 */
public class RequestHandlerManager {

    // Map with all json request handlers
    private static final Map<JmxRequest.Type, JsonRequestHandler> REQUEST_HANDLER_MAP =
            new HashMap<JmxRequest.Type, JsonRequestHandler>();

    public RequestHandlerManager(ObjectToJsonConverter pObjectToJsonConverter,
                                     StringToObjectConverter pStringToObjectConverter,
                                     Restrictor pRestrictor) {
        registerRequestHandlers(pObjectToJsonConverter,pStringToObjectConverter,pRestrictor);
    }

    protected final void registerRequestHandlers(ObjectToJsonConverter objectToJsonConverter,
                                           StringToObjectConverter stringToObjectConverter,
                                           Restrictor restrictor) {
        JsonRequestHandler handlers[] = {
                new ReadHandler(restrictor),
                new WriteHandler(restrictor,objectToJsonConverter),
                new ExecHandler(restrictor,stringToObjectConverter),
                new ListHandler(restrictor),
                new VersionHandler(restrictor),
                new SearchHandler(restrictor)
        };
        for (JsonRequestHandler handler : handlers) {
            REQUEST_HANDLER_MAP.put(handler.getType(),handler);
        }
    }

    public JsonRequestHandler getRequestHandler(JmxRequest.Type pType) {
        JsonRequestHandler handler = REQUEST_HANDLER_MAP.get(pType);
        if (handler == null) {
            throw new UnsupportedOperationException("Unsupported operation '" + pType + "'");
        }
        return handler;
    }


}
