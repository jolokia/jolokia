package org.jolokia.it.core;

public interface GenericManagementInterface<T> {

    T retrieve();

    void update(T var1);
}
