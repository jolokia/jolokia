package org.jolokia.jvmagent.spring.backend;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;

import org.jolokia.server.core.request.JolokiaListRequest;
import org.jolokia.server.core.service.JolokiaContext;
import org.jolokia.server.core.util.ClassUtil;
import org.jolokia.server.core.util.RequestType;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import static org.jolokia.service.jmx.handler.list.DataKeys.*;

/**
 * @author roland
 * @since 17.12.13
 */
public class SpringListHandler extends SpringCommandHandler<JolokiaListRequest> {

    public SpringListHandler(ApplicationContext pAppContext, JolokiaContext pJolokiaContext) {
        super(pAppContext,pJolokiaContext, RequestType.LIST);
    }


    @Override
    public Object handleRequest(JolokiaListRequest pJmxReq, Object pPreviousResult)
            throws InstanceNotFoundException, AttributeNotFoundException {
        return null;
    }

    JSONObject getAllSpringBeans(ConfigurableApplicationContext appCtx) {
        ConfigurableBeanFactory bdFactory = appCtx.getBeanFactory();
        JSONObject ret = new JSONObject();
        for (String beanName : appCtx.getBeanDefinitionNames()) {
            BeanDefinition bd = bdFactory.getMergedBeanDefinition(beanName);
            ret.put("name=" + beanName,getSpringBeanInfo(bd));
        }
        return ret;
    }


    JSONObject getSpringBeanInfo(BeanDefinition pBeanDef) {
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
        return null;
    }

    private JSONObject getAttributes(BeanDefinition pBeanDef, Class pBeanClass) {
        JSONObject ret = new JSONObject();
        //pBeanDef.getAttribute()
        return null;
    }


}
