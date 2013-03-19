package org.jolokia.request.notification;

import java.util.*;

import javax.management.MalformedObjectNameException;

import static org.jolokia.request.notification.CommandType.*;

/**
 * @author roland
 * @since 19.03.13
 */
public class CommandFactory {

    private final static Map<CommandType, Creator> CREATORS =
            new HashMap<CommandType, Creator>();

    public static Command createCommand(String pCommand, Stack<String> pStack) throws MalformedObjectNameException {
        CommandType type = CommandType.getTypeByName(pCommand);
        return CREATORS.get(type).create(pStack, null);
    }

    public static Command createCommand(Map<String, ?> pCommandMap) throws MalformedObjectNameException {
        String command = (String) pCommandMap.get("command");
        CommandType type = CommandType.getTypeByName(command);
        return CREATORS.get(type).create(null,pCommandMap);
    }

    // =====================================================================

    private interface Creator {
        Command create(Stack<String> pStack,Map<String,?> pMap) throws MalformedObjectNameException;
    }

    static {
        CREATORS.put(REGISTER,new Creator() {
            public Command create(Stack<String> pStack,Map<String,?> pMap) {
                return new RegisterCommand();
            }
        });
        CREATORS.put(UNREGISTER,new Creator() {
            public Command create(Stack<String> pStack,Map<String, ?> pMap) {
                return pStack != null ? new UnregisterCommand(pStack) : new UnregisterCommand(pMap);
            }
        });
        CREATORS.put(ADD,new Creator() {
            public Command create(Stack<String> pStack, Map<String, ?> pMap) throws MalformedObjectNameException {
                return pStack != null ? new AddCommand(pStack) : new AddCommand(pMap);
            }
        });
        CREATORS.put(REMOVE,new Creator() {
            public Command create(Stack<String> pStack, Map<String, ?> pMap) throws MalformedObjectNameException {
                return pStack != null ? new RemoveCommand(pStack) : new RemoveCommand(pMap);
            }
        });
        CREATORS.put(PING,new Creator() {
            public Command create(Stack<String> pStack, Map<String, ?> pMap) throws MalformedObjectNameException {
                return pStack != null ? new PingCommand(pStack) : new PingCommand(pMap);
            }
        });
        CREATORS.put(LIST,new Creator() {
            public Command create(Stack<String> pStack, Map<String, ?> pMap) throws MalformedObjectNameException {
                return pStack != null ? new ListCommand(pStack) : new ListCommand(pMap);
            }
        });


    }

}
