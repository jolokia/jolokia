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
        Client client = pSubscription.getClient();
        final BackChannel backChannel = client.getBackChannel(getNotifType());
        return new BackendCallback() {

            /** {@inheritDoc} */
            public void handleNotification(Notification notification, Object handback) {
                if (backChannel != null) {
                    try {
                        JolokiaContext ctx = getJolokiaContext();
                        Serializer serializer = ctx.getMandatoryService(Serializer.class);
                        PrintWriter writer = backChannel.getWriter();
                        SseNotificationResult result = new SseNotificationResult(notification,handback);
                        // TODO: Should options be specified somehow ?
                        writer.print(serializer.serialize(result, null /* no path */, SerializeOptions.DEFAULT));
                    } catch (IOException e) {
                        // Hmm, what to do here ? Ignore it ? Collect it in an MBean ?
                    } catch (AttributeNotFoundException e) {
                        // No path, no exception
                    }
                }
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
        ret.put("backChannel.contentType","text/event-stream");
        ret.put("backChannel.encoding","UTF-8");
        return ret;
    }
}
