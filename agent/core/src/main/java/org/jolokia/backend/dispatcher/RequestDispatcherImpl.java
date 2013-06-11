package org.jolokia.backend.dispatcher;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import javax.management.*;

import org.jolokia.backend.LocalRequestHandler;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.ConfigKey;
import org.jolokia.request.JmxRequest;
import org.jolokia.service.JolokiaContext;

/**
 * Manager object responsible for finding a {@link RequestHandler} and
 * dispatching the request.
 *
 * @author roland
 * @since 11.06.13
 */
public class RequestDispatcherImpl implements RequestDispatcher {

    private List<RequestHandler> requestHandlers;

    public RequestDispatcherImpl(List<RequestHandler> pRequestHandlers) {
        requestHandlers = pRequestHandlers;
    }


    public RequestDispatcherImpl(JolokiaContext pCtx) {
        LocalRequestHandler localDispatcher = new LocalRequestHandler(pCtx);
        requestHandlers = createRequestDispatchers(pCtx.getConfig(ConfigKey.DISPATCHER_CLASSES),
                                                   pCtx);
        requestHandlers.add(localDispatcher);
    }

    public DispatchResult dispatch(JmxRequest pJmxRequest) throws AttributeNotFoundException, NotChangedException, ReflectionException, IOException, InstanceNotFoundException, MBeanException {
        for (RequestHandler dispatcher : getRequestHandlers()) {
            if (dispatcher.canHandle(pJmxRequest)) {
                Object retValue = dispatcher.dispatchRequest(pJmxRequest);
                boolean useValueWithPath = dispatcher.useReturnValueWithPath(pJmxRequest);
                return new DispatchResult(retValue,useValueWithPath ? pJmxRequest.getPathParts() : null);
            }
        }
        return null;
    }

    private List<RequestHandler> getRequestHandlers() {
        return requestHandlers;
    }

// Construct configured dispatchers by reflection. Returns always
    // a list, an empty one if no request dispatcher should be created
    private List<RequestHandler> createRequestDispatchers(String pClasses,
                                                          JolokiaContext pContext) {
        List<RequestHandler> ret = new ArrayList<RequestHandler>();
        if (pClasses != null && pClasses.length() > 0) {
            String[] names = pClasses.split("\\s*,\\s*");
            for (String name : names) {
                ret.add(createDispatcher(name, pContext));
            }
        }
        return ret;
    }

    // Create a single dispatcher
    private RequestHandler createDispatcher(String pDispatcherClass,
                                               JolokiaContext pContext) {
        try {
            Class clazz = this.getClass().getClassLoader().loadClass(pDispatcherClass);
            Constructor constructor = clazz.getConstructor(JolokiaContext.class);
            return (RequestHandler)
                    constructor.newInstance(pContext);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't load class " + pDispatcherClass + ": " + e,e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + pDispatcherClass + " has invalid constructor: " + e,e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Constructor of " + pDispatcherClass + " couldn't be accessed: " + e,e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(pDispatcherClass + " couldn't be instantiated: " + e,e);
        }
    }

    public void destroy() throws JMException {
        for (RequestHandler dispatcher : requestHandlers) {
            dispatcher.destroy();
        }
    }
}
