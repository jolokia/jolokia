package org.jolokia.client.jmxadapter;

public class MBeanExample implements MBeanExampleMXBean {

    @Override
    @SuppressWarnings("unused")
    public void unsupportedOperation() {
        throw new UnsupportedOperationException("ha ha");
    }

    @Override
    @SuppressWarnings("unused")
    public void unexpectedFailureMethod() {
        throw new NullPointerException("uh oh");
    }
}
