package org.jolokia.handler;

import static org.jolokia.util.RequestType.EXEC;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.request.JmxExecRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class OpenExecHandlerTest {
    private ExecHandler handler;

    private ObjectName oName;
	
    @BeforeMethod
    public void createHandler() throws MalformedObjectNameException {
        StringToObjectConverter converter = new StringToObjectConverter();
        handler = new ExecHandler(new AllowAllRestrictor(),converter);
    }

    @BeforeTest
    public void registerMbean() throws MalformedObjectNameException, MBeanException, InstanceAlreadyExistsException, IOException, NotCompliantMBeanException, ReflectionException {
        oName = new ObjectName("jolokia:test=exec");

        MBeanServerConnection conn = getMBeanServer();
        conn.createMBean(OpenExecData.class.getName(),oName);        
    }
    
    /**
     * If a field in the argument is not set it will be set to its default value
     */
    @Test
    public void missingField() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, MalformedObjectNameException {
    	// set a value just for stringField, leave out intField
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
                operation("compositeData").
                arguments("{ \"stringField\":\"aString\" }").
                build();
        handler.handleRequest(getMBeanServer(),request);
    }

    /**
     * set a non-existing field 
     */
    @Test(expectedExceptions={IllegalArgumentException.class})
    public void invalidField() throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
    	// set a value just for stringField, leave out intField
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
                operation("compositeData").
                arguments("{ \"nonExistentField\":\"aString\" }").
                build();
        handler.handleRequest(getMBeanServer(),request);    	
    }    
    
    /**
     * Give an invalid value (wrong type) for the field 
     */
    @Test(expectedExceptions={ NumberFormatException.class })
    public void invalidValueForField() throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
        		operation("compositeData").
        		arguments("{ \"intField\":\"aString\" }").
        		build();
        handler.handleRequest(getMBeanServer(),request);    	
    }
    

    private MBeanServerConnection getMBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }

}
