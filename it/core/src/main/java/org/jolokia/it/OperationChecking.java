package org.jolokia.it;

import javax.management.*;

/**
 * @author roland
 * @since Jun 30, 2009
 */
public class OperationChecking implements OperationCheckingMBean,MBeanRegistration {

    private int counter = 0;

    public void reset() {
        counter = 0;
    }

    public int fetchNumber(String arg) {
        if ("inc".equals(arg)) {
            return counter++;
        } else {
            throw new IllegalArgumentException("Invalid arg " + arg);
        }
    }

    public boolean nullArgumentCheck(String arg1,Object arg2) {
        return arg1 == null && arg2 == null;
    }

    public boolean emptyStringArgumentCheck(String arg1) {
        return arg1 != null && arg1.length() == 0;
    }

    public String arrayArguments(String args[], String extra) {
        return args[0];
    }

    public int overloadedMethod(String arg) {
        return 1;
    }

    public int overloadedMethod(String arg, int arg2) {
        return 2;
    }

    public int overloadedMethod(String[] arg) {
        return 3;
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        return new ObjectName("jolokia.it:type=operation");

    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }
}
