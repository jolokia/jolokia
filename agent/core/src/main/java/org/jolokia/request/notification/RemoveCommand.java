package org.jolokia.request.notification;

import java.util.Map;
import java.util.Stack;

/**
 * @author roland
 * @since 19.03.13
 */
public class RemoveCommand extends ClientCommand {

    private String handle;

    RemoveCommand(Stack<String> pStack) {
        super(CommandType.REMOVE, pStack);
        handle = pStack.pop();
    }

    RemoveCommand(Map<String, ?> pMap) {
        super(CommandType.REMOVE, pMap);
        handle = (String) pMap.get("handle");
        if (handle == null) {
            throw new IllegalArgumentException("No handle given for a 'remove' notification command");
        }
    }

    public String getHandle() {
        return handle;
    }
}
