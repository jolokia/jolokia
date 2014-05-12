package org.jolokia.test.util;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import mockit.Mock;
import mockit.MockUp;

import static org.testng.Assert.*;

/**
* @author roland
* @since 27.05.14
*/ // Mockup class using jmockit for getting into JAAS Login Module
public final class MockLoginContext extends MockUp<LoginContext> {

    static public final Subject SUBJECT = new Subject();

    private String realm;
    private boolean success;

    public MockLoginContext(String pRealm, boolean pSuccess) {
        super();
        success = pSuccess;
        realm = pRealm;
    }

    @Mock
    public void $init(String pRealm, CallbackHandler handler) {
        assertEquals(pRealm,realm);
    }

    @Mock
    public void login() throws LoginException {
        if (!success) {
            throw new LoginException("Failed");
        }
    }

    @Mock
    public Subject getSubject() {
        return success ? SUBJECT : null;
    }
}
