package org.jolokia.jvmagent;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;

public class Dummy extends Authenticator {

    @Override
    public Result authenticate(HttpExchange httpExchange) {
        throw new UnsupportedOperationException("I am a authenticator called Dummy, what else were you expecting?");
    }
}
