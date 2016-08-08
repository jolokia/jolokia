package org.jolokia.support.spring;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jolokia.support.spring.log.CommonsLogHandler;
import org.jolokia.support.spring.log.Log4jLogHandler;
import org.jolokia.server.core.service.api.LogHandler;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 18.10.13
 */
public class SpringJolokiaLogHandlerHolderTest {
    @Test
    public void logHandlerProp() throws Exception {
        SpringJolokiaLogHandlerHolder holder = new SpringJolokiaLogHandlerHolder();
        LogHandler handler = new CommonsLogHandler(null);
        holder.setLogHandler(handler);
        holder.afterPropertiesSet();
        assertEquals(holder.getLogHandler(), handler);
    }

    @Test
    public void logHandlerViaType() throws Exception {
        SpringJolokiaLogHandlerHolder holder = new SpringJolokiaLogHandlerHolder();
        holder.setType("log4j");
        holder.setCategory("JOLOKIA");
        holder.afterPropertiesSet();
        Log4jLogHandler logHandler = (Log4jLogHandler) holder.getLogHandler();
        Logger logger = (Logger) ReflectionTestUtils.getField(logHandler, "logger");
        assertEquals(logger.getName(),"JOLOKIA");
    }

    @Test
    public void checkAllTypes() throws Exception {
        List<String> types = new ArrayList<String>();
        for (SpringJolokiaLogHandlerHolder.LogHandlerType t :
                SpringJolokiaLogHandlerHolder.LogHandlerType.values()) {
            types.add((String) ReflectionTestUtils.getField(t, "type"));
        }
        for (String type : types) {
            SpringJolokiaLogHandlerHolder holder = new SpringJolokiaLogHandlerHolder();
            holder.setType(type);
            holder.afterPropertiesSet();
            LogHandler logHandler = holder.getLogHandler();
            assertTrue(logHandler.getClass().getName().toLowerCase().contains(type.toLowerCase()));
            logHandler.info("info");
            logHandler.debug("debug");
            logHandler.error("error", new Exception());
            logHandler.isDebug();
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
          expectedExceptionsMessageRegExp = ".*((log-ref|type).*){2}.*")
    public void noTypeGiven() throws Exception {
        SpringJolokiaLogHandlerHolder holder = new SpringJolokiaLogHandlerHolder();
        holder.afterPropertiesSet();
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*bla.*")
    public void invalidType() throws Exception {
        SpringJolokiaLogHandlerHolder holder = new SpringJolokiaLogHandlerHolder();
        holder.setType("bla");
        holder.afterPropertiesSet();
    }
}
