package org.jolokia.request.notification;

import java.util.Map;
import java.util.Stack;

/**
 * @author roland
 * @since 19.03.13
 */
public class PingCommand extends ClientCommand {

    PingCommand(Stack<String> pStack) {
        super(CommandType.PING, pStack);
    }

    PingCommand(Map<String, ?> pMap) {
        super(CommandType.PING, pMap);
    }
}
