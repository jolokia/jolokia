package org.jolokia.service.jmx.handler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.management.*;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenType;

import org.jolokia.server.core.request.JolokiaWriteRequest;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.service.serializer.WriteRequestValues;
import org.jolokia.server.core.util.RequestType;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * Handler for dealing with write request.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class WriteHandler extends AbstractCommandHandler<JolokiaWriteRequest> {

    /** {@inheritDoc} */
    public RequestType getType() {
        return RequestType.WRITE;
    }

    /** {@inheritDoc} */
    @Override
    protected void checkForRestriction(JolokiaWriteRequest pRequest) {
        if (!context.isAttributeWriteAllowed(pRequest.getObjectName(),pRequest.getAttributeName())) {
            throw new SecurityException("Writing attribute " + pRequest.getAttributeName() +
                    " forbidden for MBean " + pRequest.getObjectNameAsString());
        }
    }

    /** {@inheritDoc} */
    @Override
    public Object doHandleSingleServerRequest(MBeanServerConnection server, JolokiaWriteRequest request)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {

        try {
            return setAttribute(request, server);
        } catch (IntrospectionException exp) {
            throw new IllegalArgumentException("Cannot get info for MBean " + request.getObjectName() + ": " +exp,exp);
        } catch (InvalidAttributeValueException e) {
            throw new IllegalArgumentException("Invalid value " + request.getValue() + " for attribute " +
                    request.getAttributeName() + ", MBean " + request.getObjectNameAsString(),e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot set value " + request.getValue() + " for attribute " +
                    request.getAttributeName() + ", MBean " + request.getObjectNameAsString(),e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot set value " + request.getValue() + " for attribute " +
                    request.getAttributeName() + ", MBean " + request.getObjectNameAsString(),e);
        }
    }

    private Object setAttribute(JolokiaWriteRequest request, MBeanServerConnection server)
            throws MBeanException, AttributeNotFoundException, InstanceNotFoundException,
            ReflectionException, IntrospectionException, InvalidAttributeValueException, IllegalAccessException, InvocationTargetException, IOException {
        // Old value, will throw an exception if attribute is not known. That's good.
        Object oldValue = server.getAttribute(request.getObjectName(), request.getAttributeName());

        MBeanInfo mInfo = server.getMBeanInfo(request.getObjectName());
        MBeanAttributeInfo aInfo = null;
        
        for (MBeanAttributeInfo i : mInfo.getAttributes()) {
            if (i.getName().equals(request.getAttributeName())) {
                aInfo = i;
                break;
            }
        }
        WriteRequestValues values;
        if (aInfo instanceof OpenMBeanAttributeInfo) {
            OpenMBeanAttributeInfo info = (OpenMBeanAttributeInfo) aInfo;
            values = getValues(info.getOpenType(), oldValue, request);
        } else {
            // aInfo is != null otherwise getAttribute() would have already thrown an ArgumentNotFoundException
            values = getValues(aInfo.getType(), oldValue, request);
        }
        Attribute attribute = new Attribute(request.getAttributeName(),values.getUpdatedValue());
        server.setAttribute(request.getObjectName(),attribute);
        return values.getOldValue();
    }

    /**
     * Get values for a write request. This method returns an array with two objects.
     * If no path is given (<code>pRequest.getExtraArgs() == null</code>), the returned values
     * are the new value and the old value. However, if a path is set, the returned new value
     * is the outer value (which can be set by an corresponding JMX set operation) where the
     * new value is set via the path expression. The old value is the value of the object specified
     * by the given path.
     *
     *
     * @param pType type of the outermost object to set as returned by an MBeanInfo structure.
     * @param pCurrentValue the object of the outermost object which can be null
     * @param pRequest the initial request
     * @return object array with two elements, element 0 is the value to set (see above), element 1
     *         is the old value.
     *
     * @throws AttributeNotFoundException if no such attribute exists (as specified in the request)
     * @throws IllegalAccessException if access to MBean fails
     * @throws InvocationTargetException reflection error when setting an object's attribute
     */
    private WriteRequestValues getValues(String pType, Object pCurrentValue, JolokiaWriteRequest pRequest)
            throws AttributeNotFoundException, IllegalAccessException, InvocationTargetException {
        List<String> pathParts = pRequest.getPathParts();
        Object newValue = pRequest.getValue();

        if (pathParts != null && pathParts.size() > 0) {
            if (pCurrentValue == null ) {
                throw new IllegalArgumentException(
                        "Cannot set value with path when parent object is not set");
            }

            // We set an inner value, hence we have to return provided value itself for resetting
            // it later back via JMX
            return context.getMandatoryService(Serializer.class).setInnerValue(pCurrentValue, newValue, pathParts);

        } else {
            // Return the objectified value
            return new WriteRequestValues(context.getMandatoryService(Serializer.class).deserialize(pType, newValue), pCurrentValue);
        }
    }

    private WriteRequestValues getValues(OpenType<?> pOpenType, Object pCurrentValue, JolokiaWriteRequest pRequest) {
        // TODO: What to do when path is not null ? Simplest: Throw exception. Advanced: Extract other values and create
        // a new CompositeData with old values and the new value.
        // However, since this is probably out of scope, we will simply throw an exception if the path is not empty.
        List<String> pathParts = pRequest.getPathParts();
        if (pathParts != null && pathParts.size() > 0) {
            throw new IllegalArgumentException("Cannot set value for OpenType " + pOpenType + " with inner path " +
                                               pRequest.getPath() + " since OpenTypes are immutable");
        }
        return new WriteRequestValues(context.getMandatoryService(Serializer.class).deserializeOpenType(pOpenType, pRequest.getValue()), pCurrentValue);
    }
}

