package org.jolokia.server.core.service.serializer;

public class WriteRequestValues
{
    private final Object updatedValue;

    private final Object oldValue;

    public WriteRequestValues(Object updatedValue, Object oldValue) {
        this.updatedValue = updatedValue;
        this.oldValue = oldValue;
    }

    public Object getUpdatedValue() {
        return updatedValue;
    }

    public Object getOldValue() {
        return oldValue;
    }
}
