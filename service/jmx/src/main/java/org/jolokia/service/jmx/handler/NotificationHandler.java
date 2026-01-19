/*
 * Copyright 2009-2026 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.service.jmx.handler;

import java.io.IOException;
import javax.management.JMException;
import javax.management.MBeanServerConnection;

import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.request.JolokiaNotificationRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.service.jmx.handler.notification.NotificationDispatcher;

/**
 * A request handler which is responsible for managing notification requests.
 *
 * @author roland
 * @since 19.03.13
 */
public class NotificationHandler extends AbstractCommandHandler<JolokiaNotificationRequest> {

    // Dispatcher for notification registration requests
    private NotificationDispatcher dispatcher;

    @Override
    public void init(JolokiaContext pContext, String pProvider) {
        super.init(pContext, pProvider);
        dispatcher = new NotificationDispatcher(pContext);
    }

    @Override
    public RequestType getType() {
        return RequestType.NOTIFICATION;
    }

    @Override
    protected void checkForRestriction(JolokiaNotificationRequest pRequest) {
        // Not used currently ...
    }

    @Override
    public boolean handleAllServersAtOnce(JolokiaNotificationRequest pRequest) {
        // We always handler requests on all MBeanServers
        return true;
    }

    @Override
    protected Object doHandleSingleServerRequest(MBeanServerConnection server, JolokiaNotificationRequest request) {
        throw new UnsupportedOperationException("Internal: Notification handler works an all MBeanServers, not on single one");
    }

    @Override
    public Object doHandleAllServerRequest(MBeanServerAccess serverManager, JolokiaNotificationRequest request, Object pPreviousResult)
            throws IOException, JMException, BadRequestException, EmptyResponseException {
        // Just delegate
        return dispatcher.dispatch(serverManager, request.getCommand());
    }

}
