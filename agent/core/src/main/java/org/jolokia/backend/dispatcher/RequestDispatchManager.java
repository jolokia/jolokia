package org.jolokia.backend.dispatcher;

import java.io.IOException;
import java.util.List;

import javax.management.*;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxRequest;

/**
 * Manager object responsible for finding a {@link RequestDispatcher} and
 * dispatching the request.
 *
 * @author roland
 * @since 11.06.13
 */
public class RequestDispatchManager {


    private List<RequestDispatcher> requestDispatchers;

    public RequestDispatchManager(List<RequestDispatcher> pRequestDispatchers) {
        requestDispatchers = pRequestDispatchers;
    }

    public DispatchResult dispatch(JmxRequest pJmxRequest) throws AttributeNotFoundException, NotChangedException, ReflectionException, IOException, InstanceNotFoundException, MBeanException {
        for (RequestDispatcher dispatcher : getRequestDispatchers()) {
            if (dispatcher.canHandle(pJmxRequest)) {
                Object retValue = dispatcher.dispatchRequest(pJmxRequest);
                boolean useValueWithPath = dispatcher.useReturnValueWithPath(pJmxRequest);
                return new DispatchResult(retValue,useValueWithPath ? pJmxRequest.getPathParts() : null);
            }
        }
        return null;
    }

    private List<RequestDispatcher> getRequestDispatchers() {
        return requestDispatchers;
    }

    public void destroy() throws JMException {
        for (RequestDispatcher dispatcher : requestDispatchers) {
            dispatcher.destroy();
        }
    }
}
