package org.jolokia.handler;

import java.io.IOException;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.handler.notification.NotificationDispatcher;
import org.jolokia.request.JmxNotificationRequest;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.RequestType;

/**
 * A request handler which is responsible for managing notification
 * requests.
 *
 * @author roland
 * @since 19.03.13
 */
public class NotificationHandler extends JsonRequestHandler<JmxNotificationRequest> {

    // Dispatcher for notification registration requests
    private NotificationDispatcher dispatcher;

    /**
     * Create a handler with the given restrictor
     *
     * @param pContext jolokia context to use
     */
    public NotificationHandler(JolokiaContext pContext) {
        super(pContext);
        dispatcher = new NotificationDispatcher(pContext);
    }

    @Override
    /** {@inheritDoc} */
    public RequestType getType() {
        return RequestType.NOTIFICATION;
    }

    @Override
    /** {@inheritDoc} */
    public boolean handleAllServersAtOnce(JmxNotificationRequest pRequest) {
        // We always handler requests on all MBeanServers
        return true;
    }

    @Override
    /** {@inheritDoc} */
    protected void checkForRestriction(JmxNotificationRequest pRequest) {
        // Not used currently ...
    }

    @Override
    /** {@inheritDoc} */
    protected Object doHandleRequest(MBeanServerConnection server, JmxNotificationRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        throw new UnsupportedOperationException("Internal: Notification handler works an all MBeanServers, not on single one");
    }

    @Override
    /** {@inheritDoc} */
    public Object doHandleRequest(MBeanServerExecutor serverManager, JmxNotificationRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        return dispatcher.dispatch(serverManager,request.getCommand());
    }

    /** {@inheritDoc} */
    public void destroy() throws JMException {
        dispatcher.destroy();
    }
}
