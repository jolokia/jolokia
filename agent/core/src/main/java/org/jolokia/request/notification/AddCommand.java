package org.jolokia.request.notification;

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.util.EscapeUtil;

/**
 * @author roland
 * @since 19.03.13
 */
public class AddCommand extends ClientCommand {

    private final ObjectName objectName;

    private List<String> filter;

    private Object handback;

    AddCommand(Stack<String> pStack) throws MalformedObjectNameException {
        super(CommandType.ADD, pStack);
        objectName = new ObjectName(pStack.pop());
        if (!pStack.isEmpty()) {
            filter = EscapeUtil.split(pStack.pop(),EscapeUtil.CSV_ESCAPE,",");
        }
        if (!pStack.isEmpty()) {
            handback = pStack.pop();
        }
    }

    AddCommand(Map<String,?> pMap) throws MalformedObjectNameException {
        super(CommandType.ADD, pMap);
        objectName = new ObjectName((String) pMap.get("mbean"));
        Object f = pMap.get("filter");
        if (f != null) {
            filter = f instanceof List ? (List<String>) f : Arrays.asList(f.toString());
        }
        handback = pMap.get("handback");
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public List<String> getFilter() {
        return filter;
    }

    public Object getHandback() {
        return handback;
    }
}
