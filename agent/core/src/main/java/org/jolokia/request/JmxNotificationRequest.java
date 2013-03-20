package org.jolokia.request;

import java.util.Map;
import java.util.Stack;

import javax.management.MalformedObjectNameException;

import org.jolokia.config.ProcessingParameters;
import org.jolokia.request.notification.Command;
import org.jolokia.request.notification.CommandFactory;
import org.jolokia.util.RequestType;

/**
 * @author roland
 * @since 19.03.13
 */
public class JmxNotificationRequest extends JmxRequest {

    // Command to execute
    private Command command;

    /**
     * Constructor for GET requests.
     *
     * @param pCommand command to use
     * @param pParams processing parameters
     */
    JmxNotificationRequest(Command pCommand, ProcessingParameters pParams) {
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
    JmxNotificationRequest(Command pCommand, Map<String, ?> pRequestMap, ProcessingParameters pParams) {
        super(pRequestMap, pParams);
        command = pCommand;
    }

    /**
     * Command encapsulating the concrete notification request
     *
     * @return notification command
     */
    public <T extends Command> T getCommand() {
        return (T) command;
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
                Command command = CommandFactory.createCommand(pStack);
                return new JmxNotificationRequest(command,pParams);
            }

            /** {@inheritDoc} */
            public JmxNotificationRequest create(Map<String, ?> requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                Command command = CommandFactory.createCommand(requestMap);
                return new JmxNotificationRequest(command,requestMap,pParams);
            }
        };
    }

}
