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
        oName = new ObjectName("jolokia:test=openExec");

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

    /**
     * The operation argument is an array
     */
    @Test
    public void arrayOfComposites() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, MalformedObjectNameException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
                operation("arrayData").
                arguments("[ { \"stringField\":\"aString\" } ]").
                build();
        handler.handleRequest(getMBeanServer(),request);
    }

    /**
     * The operation argument is a List
     */
    @Test
    public void listOfComposites() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, MalformedObjectNameException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
                operation("listData").
                arguments("[ { \"stringField\":\"aString\" } ]").
                build();
        handler.handleRequest(getMBeanServer(),request);
    }
    
    /**
     * The operation argument is a Set
     */
    @Test
    public void setOfComposites() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, MalformedObjectNameException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
                operation("opSetData").
                arguments("[ { \"stringField\":\"aString\" } ]").
                build();
        handler.handleRequest(getMBeanServer(),request);
    }
    
    /**
     * The operation argument is a Map
     */
    @Test
    public void mapOfComposites() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, MalformedObjectNameException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
                operation("mapData").
                arguments("{ \"aKey\":{ \"stringField\":\"aString\" } }").
                build();
        handler.handleRequest(getMBeanServer(),request);
    }
    
    /**
     * Set a nested field inside the composite argument
     */
    @Test
    public void nested() throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
				operation("compositeData").
				arguments("{ \"nestedClass\":{\"nestedField\":\"aString\"} }").
				build();
        handler.handleRequest(getMBeanServer(),request);    	    	
    }
    
    /**
     * Set an array field inside the composite argument
     */
    @Test
    public void compositeWithArrayField() throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
        		operation("compositeData").
        		arguments("{ \"array\":[\"one\", \"two\"] }").
        		build();
        handler.handleRequest(getMBeanServer(),request);    	
    }
    
    /**
     * Set a List field inside the composite argument with values that can't be converted to
     * the list element type
     */
    @Test(expectedExceptions={NumberFormatException.class})
    public void invalidTypeCompositeWithListField() throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
        		operation("compositeData").
        		arguments("{ \"list\":[\"one\", \"two\"] }").
        		build();
        handler.handleRequest(getMBeanServer(),request);    	
    }

    /**
     * Set a List field inside the composite argument
     */
    @Test
    public void compositeWithListField() throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
        		operation("compositeData").
        		arguments("{ \"list\":[\"1\", \"2\"] }").
        		build();
        handler.handleRequest(getMBeanServer(),request);    	
    }    
    
    /**
     * Set a Map field inside the composite argument
     */
    @Test
    public void compositeWithMapField() throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
        		operation("compositeData").
        		arguments("{ \"map\":{ \"5\":{\"nestedField\":\"value1\"}, \"7\":{\"nestedField\":\"value2\"} } }").
        		build();
        handler.handleRequest(getMBeanServer(),request);    	
    }

    /**
     * Set a Set field inside the composite argument 
     */
    @Test
    public void compositeWithSetField() throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
        		operation("compositeData").
        		arguments("{ \"set\": [\"value1\",\"value2\"] }").
        		build();
        handler.handleRequest(getMBeanServer(),request);    	
    }
    
    
    @Test
    public void overloaded() throws MalformedObjectNameException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
				operation("overloaded(javax.management.openmbean.CompositeData)").
				arguments("{ \"stringField\": \"aString\" }").
				build();
        handler.handleRequest(getMBeanServer(),request);    	    	
    }
        

    private MBeanServerConnection getMBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }

}
