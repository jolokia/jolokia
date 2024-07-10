package org.jolokia.service.notif.sse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.management.AttributeNotFoundException;
import javax.management.Notification;

import org.jolokia.server.core.http.BackChannel;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.notification.*;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.json.JSONObject;

/**
 * Dummy implementation
 *
 * @author roland
 * @since 20.03.13
 */
public class SseNotificationBackend extends AbstractJolokiaService<NotificationBackend> implements NotificationBackend {

    private static final byte[] CRLF = new byte[]{'\r', '\n'};
    private static final byte[] ID_FIELD = "id: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DATA_FIELD = "data: ".getBytes(StandardCharsets.UTF_8);

    // Map with heart beat threads
    private final HashMap<String,SseHeartBeat> heartBeatMap = new HashMap<>();

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
                if (backChannel != null && !backChannel.isClosed()) {
                    JolokiaContext ctx = getJolokiaContext();
                    Serializer serializer = ctx.getMandatoryService(Serializer.class);
                    NotificationResult result =
                            new NotificationResult(pSubscription.getHandle(), Collections.singletonList(notification),
                                                   handback, 0);
                    try {
                        long id = notification.getSequenceNumber();
                        String data =
                                serializer.serialize(result, null /* no path */, SerializeOptions.DEFAULT)
                                          .toString();
                        sendMessage(backChannel,id,data);
                    } catch (IOException e) {
                        // TODO: Collect in a buffer, ordered by sequence number
                    } catch (AttributeNotFoundException e) {
                        // No path, no exception, so cant happen (TM) in 'serialize()'
                    }
                }
                // TODO: Collect exception in client specific buffer and send it when a reconnect happened
            }
        };
    }

    public void channelInit(Client client, BackChannel channel) {
        // Start heartbeat
        SseHeartBeat heartBeat = new SseHeartBeat(channel);
        heartBeat.start();
        heartBeatMap.put(client.getId(),heartBeat);
    }


    /** {@inheritDoc} */
    public void unsubscribe(String pClientId, String pHandle) {
        // TODO: Clean up any store notifications
    }

    /** {@inheritDoc}
     * @param pClient*/
    public void unregister(Client pClient) {
        // Stop heartbeat
        SseHeartBeat heartBeat = heartBeatMap.remove(pClient.getId());
        if (heartBeat != null) {
            heartBeat.stop();
        }

        // Close channel.
        BackChannel backChannel = pClient.getBackChannel(getNotifType());
        if (backChannel != null) {
            backChannel.close();
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    public Map<String, ?> getConfig() {
        JSONObject ret = new JSONObject();
        ret.put(BackChannel.CONTENT_TYPE, "text/event-stream");
        ret.put(BackChannel.ENCODING, "UTF-8");
        return ret;
    }

    // ===================================================================================

    private void sendMessage(BackChannel pBackChannel, long pId, String pData) throws IOException {
        synchronized (pBackChannel) {
            OutputStream os = pBackChannel.getOutputStream();
            id(os,pId);
            data(os,pData);
            os.write(CRLF);
            os.flush();
        }
    }

    private void id(OutputStream pOs, long pId) throws IOException {
        printLine(pOs, ID_FIELD, Long.toString(pId).getBytes(StandardCharsets.UTF_8));
    }

    private void data(OutputStream pOs, String pData) throws IOException {
        synchronized (this) {
            BufferedReader reader = new BufferedReader(new StringReader(pData));
            String line;
            while ((line = reader.readLine()) != null) {
                printLine(pOs, DATA_FIELD, line.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void printLine(OutputStream pOs, byte[] pField, byte[] pValue) throws IOException {
        pOs.write(pField);
        pOs.write(pValue);
        pOs.write(CRLF);
    }
}
