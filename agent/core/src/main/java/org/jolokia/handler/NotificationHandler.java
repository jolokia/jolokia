package org.jolokia.handler;

import java.io.IOException;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxRequest;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.RequestType;

/**
 * @author roland
 * @since 19.03.13
 */
public class NotificationHandler extends JsonRequestHandler {
    public NotificationHandler(Restrictor pRestrictor) {
        super(pRestrictor);
    }

    @Override
    public RequestType getType() {
        return RequestType.NOTIFICATION;
    }

    @Override
    public boolean handleAllServersAtOnce(JmxRequest pRequest) {
        return true;
    }

    @Override
    protected void checkForRestriction(JmxRequest pRequest) {
    }

    @Override
    protected Object doHandleRequest(MBeanServerConnection server, JmxRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        return null;
    }

    @Override
    public Object doHandleRequest(MBeanServerExecutor serverManager, JmxRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        return super.doHandleRequest(serverManager, request);
    }
}
