package org.jolokia.client.jmxadapter;

public class MBeanExample implements MBeanExampleMXBean {

    @Override
    public void unsupportedOperation() {
        throw new UnsupportedOperationException("ha ha");
    }

    @Override
    public void unexpectedFailureMethod() {
        throw new NullPointerException("uh oh");
    }
}
