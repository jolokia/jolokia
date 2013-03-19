package org.jolokia.request.notification;

import java.util.Map;
import java.util.Stack;

/**
 * @author roland
 * @since 19.03.13
 */
public class UnregisterCommand extends ClientCommand {

    UnregisterCommand(Stack<String> pStack) {
        super(CommandType.UNREGISTER, pStack);
    }

    UnregisterCommand(Map<String,?> pMap) {
        super(CommandType.UNREGISTER,pMap);
    }
}
