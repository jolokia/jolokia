package org.jolokia.it.core;

public class GenericsChecking implements GenericsMXBean {

    private volatile DummyObject dummyObject = new DummyObject("dummy");

    public DummyObject retrieve() {
        return dummyObject;
    }

    public void update(DummyObject var1) {
        dummyObject = var1;
    }

    public void someOtherMethod() {
        // not used
    }
}
