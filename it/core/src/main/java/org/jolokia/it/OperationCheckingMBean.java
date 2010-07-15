package org.jolokia.it;

/**
 * @author roland
 * @since Jun 30, 2009
 */
public interface OperationCheckingMBean {

    void reset();

    int fetchNumber(String arg);

    int overloadedMethod(String arg);

    int overloadedMethod(String arg,int arg2);

    int overloadedMethod(String[] arg);

    boolean nullArgumentCheck(String arg1,Object arg2);

    boolean emptyStringArgumentCheck(String arg1);

    String arrayArguments(String args[], String extra);
}
