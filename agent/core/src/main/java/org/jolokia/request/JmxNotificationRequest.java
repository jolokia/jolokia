package org.jolokia.request;

import java.util.Map;
import java.util.Stack;

import javax.management.MalformedObjectNameException;

import org.jolokia.request.notification.NotificationCommand;
import org.jolokia.request.notification.NotificationCommandFactory;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;

/**
 * @author roland
 * @since 19.03.13
 */
public class JmxNotificationRequest extends JmxRequest {

    // Command to execute
    private NotificationCommand command;

    /**
     * Constructor for GET requests.
     *
     * @param pCommand command to use
     * @param pParams processing parameters
     */
    JmxNotificationRequest(NotificationCommand pCommand, ProcessingParameters pParams) {
        super(RequestType.NOTIFICATION, null, pParams);
        command = pCommand;
    }

    /**
     * Constructor for POST requests
     *
     * @param pCommand notification specific command
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     */
    JmxNotificationRequest(NotificationCommand pCommand, Map<String, ?> pRequestMap, ProcessingParameters pParams) {
        super(pRequestMap, pParams);
        command = pCommand;
    }

    /**
     * Command encapsulating the concrete notification request
     *
     * @return notification command
     */
    public <T extends NotificationCommand> T getCommand() {
        return (T) command;
    }

    /** {@inheritDoc} */
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        JSONObject commandJson = command.toJSON();
        ret.putAll(commandJson);
        return ret;
    }

    /**
     * Creator for {@link JmxReadRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JmxNotificationRequest> newCreator() {
        return new RequestCreator<JmxNotificationRequest>() {
            /** {@inheritDoc} */
            public JmxNotificationRequest create(Stack<String> pStack, ProcessingParameters pParams) throws MalformedObjectNameException {
                NotificationCommand notifCommand = NotificationCommandFactory.createCommand(pStack);
                return new JmxNotificationRequest(notifCommand,pParams);
            }

            /** {@inheritDoc} */
            public JmxNotificationRequest create(Map<String, ?> requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                NotificationCommand notifCommand = NotificationCommandFactory.createCommand(requestMap);
                return new JmxNotificationRequest(notifCommand,requestMap,pParams);
            }
        };
    }

}
