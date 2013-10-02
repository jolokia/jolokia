package org.jolokia.jvmagent;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import org.jolokia.config.Configuration;

/**
 * Class that facilitates custom authenticator tests
 */
public class Dummy extends Authenticator {

    private Configuration config;

    public Dummy() {
        throw new UnsupportedOperationException("I expect to get some config parameters, use another constructor");
    }

    public Dummy(Configuration configuration) {
        this.config = configuration;
    }

    @Override
    public Result authenticate(HttpExchange httpExchange) {
        throw new UnsupportedOperationException("I am a authenticator called Dummy, what else were you expecting?");
    }

    public Configuration getConfig() {
        return config;
    }
}
