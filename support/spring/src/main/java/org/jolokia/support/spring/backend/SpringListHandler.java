package org.jolokia.support.spring.backend;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;

import org.jolokia.server.core.request.JolokiaListRequest;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.*;
import org.jolokia.service.jmx.handler.list.DataKeys;
import org.json.simple.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import static org.jolokia.service.jmx.handler.list.DataKeys.*;

/**
 * A handler for dealing with "list" requests. Currently only one application context
 * is supported.
 *
 * @author roland
 * @since 17.12.13
 */
public class SpringListHandler extends SpringCommandHandler<JolokiaListRequest> {

    final static Map<Class,String> WRAPPER_TO_PRIMITIVE;

    public SpringListHandler(ApplicationContext pAppContext, JolokiaContext pJolokiaContext) {
        super(pAppContext, pJolokiaContext, RequestType.LIST);
    }

    @Override
    public Object handleRequest(JolokiaListRequest pJmxReq, Object pPreviousResult)
            throws InstanceNotFoundException, AttributeNotFoundException {
        String domain = getApplicationContext().getApplicationName();
        if (domain == null || domain.length() == 0) {
            domain = "default";
        }
        JSONObject ret = new JSONObject();
        JSONObject beans = getAllSpringBeans(getAsConfigurableApplicationContext());
        ret.put(SpringRequestHandler.PROVIDER + "@" + domain,beans);
        return ret;
    }

    // ====================================================================================

    private JSONObject getAllSpringBeans(ConfigurableApplicationContext appCtx) {
        ConfigurableBeanFactory bdFactory = appCtx.getBeanFactory();
        JSONObject ret = new JSONObject();
        // TODO: Fix for FactoryBeans
        for (String beanName : appCtx.getBeanDefinitionNames()) {
            BeanDefinition bd = bdFactory.getMergedBeanDefinition(beanName);
            ret.put("name=" + beanName, getSpringBeanInfo(bd));
        }
        return ret;
    }

    private JSONObject getSpringBeanInfo(BeanDefinition pBeanDef) {
        JSONObject ret = new JSONObject();
        ret.put(DESCRIPTION,pBeanDef.getDescription());
        String beanClassName = pBeanDef.getBeanClassName();
        Class beanClass = ClassUtil.classForName(beanClassName);
        if (beanClass != null) {
            ret.put(ATTRIBUTES, getAttributes(pBeanDef, beanClass));
            ret.put(OPERATIONS, getOperations(beanClass));
        }
        return ret;
    }

    private JSONObject getOperations(Class pBeanClass) {
        JSONObject ret = new JSONObject();
        for (Method method : pBeanClass.getMethods()) {
            int modifier = method.getModifiers();
            if (Modifier.isPublic(modifier) &&
                (modifier & (Modifier.ABSTRACT | Modifier.STATIC)) == 0) {
                JSONObject oMap = new JSONObject();
                oMap.put(ARGS,extractArguments(method));
                oMap.put(RETURN_TYPE,classToString(method.getReturnType()));
                JsonUtil.addJSONObjectToJSONObject(ret,method.getName(),oMap);
            }
        }
        return ret;
    }

    private JSONArray extractArguments(Method method) {
        JSONArray ret = new JSONArray();
        int i = 0;
        for (Class paramType : method.getParameterTypes()) {
            JSONObject params = new JSONObject();
            params.put(TYPE,classToString(paramType));
            // Maybe extract real name when running under Java 8 with reflection and Method.getParameters()
            // or by extracting debugging info (like Spring MVC does). For now we simply add dummy values;
            params.put(NAME,"arg" + i++);
            ret.add(params);
        }
        return ret;
    }

    private JSONObject getAttributes(BeanDefinition pBeanDef, Class pBeanClass) {
        JSONObject ret = new JSONObject();

        addIfNotNull(ret, DESCRIPTION, pBeanDef.getDescription());
        for (PropertyDescriptor propDesc : BeanUtils.getPropertyDescriptors(pBeanClass)) {
            JSONObject aMap = new JSONObject();
            Class propType = propDesc.getPropertyType();
            addIfNotNull(aMap, TYPE, propType != null ? classToString(propType) : null);
            addIfNotNull(aMap, DESCRIPTION, propDesc.getShortDescription());
            aMap.put(READ_WRITE, propDesc.getReadMethod() != null && propDesc.getWriteMethod() != null);
            ret.put(propDesc.getName(),aMap);
        }
        return ret;
    }

    private ConfigurableApplicationContext getAsConfigurableApplicationContext() {
        ApplicationContext appCtx = getApplicationContext();
        if (! (appCtx instanceof  ConfigurableApplicationContext)) {
            throw new IllegalArgumentException("Given Spring application context " + appCtx + " is not a " +
                                               "ConfigurableApplicationContext");
        }
        return (ConfigurableApplicationContext) appCtx;
    }

    private void addIfNotNull(JSONObject pMap, DataKeys pKey, String pValue) {
        if (pValue != null) {
            pMap.put(pKey,pValue);
        }
    }

    // Class mapping for primitive values
    static {
        WRAPPER_TO_PRIMITIVE = new HashMap<Class, String>();
        Object[] p = {
                Boolean.TYPE, "boolean",
                Character.TYPE, "character",
                Byte.TYPE, "byte",
                Short.TYPE, "short",
                Integer.TYPE, "int",
                Long.TYPE, "long",
                Float.TYPE, "float",
                Double.TYPE, "double",
                Void.TYPE, "void"
        };

        for (int i = 0; i < p.length; i += 2) {
            WRAPPER_TO_PRIMITIVE.put((Class) p[i],(String) p[i+1]);
        }
    }

    private String classToString(Class pClass) {
        if (pClass.isPrimitive()) {
            String ret = WRAPPER_TO_PRIMITIVE.get(pClass);
            if (ret == null) {
                throw new IllegalStateException("Internal: No mapping for primitive type " + pClass + " found");
            }
            return ret;
        } else {
            return pClass.getCanonicalName();
        }
    }
}
