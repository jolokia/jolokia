package org.jolokia.support.spring.backend;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.management.*;

import org.jolokia.server.core.request.JolokiaReadRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

/**
 * @author roland
 * @since 02.12.13
 */
public class SpringReadHandler extends SpringCommandHandler<JolokiaReadRequest> {

    protected SpringReadHandler(ApplicationContext pAppContext, JolokiaContext pContext) {
        super(pAppContext, pContext, RequestType.READ);
    }

    @Override
    public Object handleRequest(JolokiaReadRequest pJmxReq, Object pPreviousResult) throws InstanceNotFoundException, AttributeNotFoundException {
        ObjectName oName = pJmxReq.getObjectName();
        String beanName = oName.getKeyProperty("name");
        if (beanName == null) {
            beanName = oName.getKeyProperty("id");
        }
        if (beanName == null) {
            throw new IllegalArgumentException("No bean name given with property 'name' when requesting " + oName);
        }

        ApplicationContext ctx = getApplicationContext();
        try {
            Object bean = ctx.getBean(beanName);
            Class clazz = bean.getClass();
            String attribute = pJmxReq.getAttributeName();
            if (attribute == null) {
                throw new UnsupportedOperationException("Multi attribute read not implemented yet");
            }
            // Try get method first
            Method getter = ReflectionUtils.findMethod(
                clazz, "get" + attribute.substring(0, 1).toUpperCase() + attribute.substring(1));
            if (getter != null) {
                return ReflectionUtils.invokeMethod(getter,bean);
            }

            // Next: Direct field access
            Field field = ReflectionUtils.findField(clazz,attribute);
            if (field != null) {
                boolean isAccessible = field.isAccessible();
                field.setAccessible(true);
                try {
                    return ReflectionUtils.getField(field,bean);
                } finally {
                    field.setAccessible(isAccessible);
                }
            }
            throw new AttributeNotFoundException("No attribute " + attribute +
                                                 " found on bean " + beanName + "(class " + clazz + ") while processing " + oName);

        } catch (NoSuchBeanDefinitionException exp) {
            throw (InstanceNotFoundException)
                    new InstanceNotFoundException("No bean with name " + beanName + " found in application context").initCause(exp);
        }
    }
}
