package org.jolokia.request.notification;

import java.util.Map;
import java.util.Stack;

/**
 * @author roland
 * @since 19.03.13
 */
public class ListCommand extends ClientCommand {
    ListCommand(Stack<String> pStack) {
        super(CommandType.LIST, pStack);
    }

    ListCommand(Map<String, ?> pMap) {
        super(CommandType.LIST, pMap);
    }
}
