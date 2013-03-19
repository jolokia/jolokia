package org.jolokia.request.notification;

import java.util.Map;
import java.util.Stack;

/**
 * @author roland
 * @since 19.03.13
 */
abstract public class ClientCommand extends Command {

    private String client;

    protected ClientCommand(CommandType pType, Stack<String> pStack) {
        super(pType);
        client = pStack.pop();
    }

    protected ClientCommand(CommandType pType, Map<String, ?> pMap) {
        super(pType);
        client = (String) pMap.get("client");
    }

    public String getClient() {
        return client;
    }
}
