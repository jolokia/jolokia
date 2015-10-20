package org.jolokia.service.notif.sse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import javax.management.AttributeNotFoundException;
import javax.management.Notification;

import org.jolokia.server.core.http.BackChannel;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.notification.*;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.Serializer;
import org.json.simple.JSONObject;

/**
 * Dummy implementation
 *
 * @author roland
 * @since 20.03.13
 */
public class SseNotificationBackend extends AbstractJolokiaService<NotificationBackend> implements NotificationBackend {

    /**
     * Create a server side event notification backend which will return notifications
     * via the backchannel.
     *
     * @param order of this notification backend
     */
    public SseNotificationBackend(int order) {
        super(NotificationBackend.class,order);
    }

    /** {@inheritDoc} */
    public void init(JolokiaContext pContext) {
        if (getJolokiaContext() == null) {
            super.init(pContext);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (getJolokiaContext() != null) {
            super.destroy();
        }
    }

    /** {@inheritDoc} */
    public String getNotifType() {
        return "sse";
    }

    /** {@inheritDoc} */
    public BackendCallback subscribe(final NotificationSubscription pSubscription) {
        final Client client = pSubscription.getClient();
        return new BackendCallback() {

            /** {@inheritDoc} */
            public void handleNotification(Notification notification, Object handback) {
                BackChannel backChannel = client.getBackChannel(getNotifType());
                if (backChannel != null) {
                    try {
                        JolokiaContext ctx = getJolokiaContext();
                        Serializer serializer = ctx.getMandatoryService(Serializer.class);
                        PrintWriter writer = backChannel.getWriter();
                        SseNotificationResult result = new SseNotificationResult(notification,handback);
                        writer.println("id:" + notification.getSequenceNumber());
                        writer.println("data:" + serializer.serialize(result, null /* no path */, SerializeOptions.DEFAULT));
                        writer.println();
                        writer.flush();
                    } catch (IOException e) {
                        // TODO: Collect in a buffer, ordered by sequence number
                    } catch (AttributeNotFoundException e) {
                        // No path, no exception
                    }
                }
                // TODO: Collect exception in client specific buffer and send it when a reconnect happened
                // Also: Think about thread for periodically pinging with a comment in order to keep connection open
            }
        };
    }

    /** {@inheritDoc} */
    public void unsubscribe(String pClientId, String pHandle) {

    }

    /** {@inheritDoc} */
    public void unregister(String pClientId) {

    }

    /** {@inheritDoc} */
    public Map<String, ?> getConfig() {
        JSONObject ret = new JSONObject();
        ret.put(BackChannel.CONTENT_TYPE, "text/event-stream");
        ret.put(BackChannel.ENCODING, "UTF-8");
        return ret;
    }
}
