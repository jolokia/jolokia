package org.jolokia.request.notification;

/**
 * @author roland
 * @since 19.03.13
 */
public abstract class Command {

    // Command action
    private CommandType type;

    protected Command(CommandType pType) {
        type = pType;
    }

    public CommandType getType() {
        return type;
    }

}
