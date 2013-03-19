package org.jolokia.request.notification;

import java.util.HashMap;
import java.util.Map;

/**
 * @author roland
 * @since 19.03.13
 */
public enum CommandType {

    REGISTER("register"),
    UNREGISTER("unregister"),
    ADD("add"),
    REMOVE("remove"),
    PING("ping"),
    LIST("list");

    private String type;

    private final static Map<String, CommandType> commandsByName =
            new HashMap<String, CommandType>();

    // Initialise lookup map
    static {
        for (CommandType t : CommandType.values()) {
            commandsByName.put(t.getType(), t);
        }
    }

    //
    CommandType(String pPType) {
        type = pPType;
    }

    /**
     * Return the name of the typs
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * Case insensitive lookup by name
     *
     * @param pName name of the command (lower- or upper case)
     * @return command looked up
     * @throws IllegalArgumentException if the argument is either <code>null</code> or
     *         does not map to a type.
     */
    public static CommandType getTypeByName(String pName) {
        if (pName == null) {
            throw new IllegalArgumentException("No command given");
        }
        CommandType command = commandsByName.get(pName.toLowerCase());
        if (command == null) {
            throw new UnsupportedOperationException("No command with name '" + pName + "' exists");
        }
        return command;
    }
}
