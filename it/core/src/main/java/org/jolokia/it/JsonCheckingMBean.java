package org.jolokia.it;

/**
 * @author roland
 * @since 27.01.13
 */
public interface JsonCheckingMBean {
    ComplexTestData getData();
    ComplexTestData execute(String[] args);
}
