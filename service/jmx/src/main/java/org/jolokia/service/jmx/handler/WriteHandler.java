/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.service.jmx.handler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.JMException;
import javax.management.JMRuntimeException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenType;

import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.JolokiaWriteRequest;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.util.RequestType;

/**
 * Handler for dealing with write request.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class WriteHandler extends AbstractCommandHandler<JolokiaWriteRequest> {

    @Override
    public RequestType getType() {
        return RequestType.WRITE;
    }

    @Override
    protected void checkForRestriction(JolokiaWriteRequest pRequest) {
        if (!context.isAttributeWriteAllowed(pRequest.getObjectName(),pRequest.getAttributeName())) {
            throw new SecurityException("Writing attribute " + pRequest.getAttributeName() +
                    " forbidden for MBean " + pRequest.getObjectNameAsString());
        }
    }

    @Override
    public Object doHandleSingleServerRequest(MBeanServerConnection server, JolokiaWriteRequest request)
            throws IOException, JMException, BadRequestException {

        MBeanInfo mInfo = server.getMBeanInfo(request.getObjectName());
        MBeanAttributeInfo aInfo = null;

        for (MBeanAttributeInfo i : mInfo.getAttributes()) {
            if (i.getName().equals(request.getAttributeName())) {
                aInfo = i;
                break;
            }
        }

        if (aInfo == null) {
            throw new AttributeNotFoundException("No such attribute: " + request.getAttributeName());
        }
        if (!aInfo.isWritable()) {
            throw new AttributeNotFoundException("Can't write attribute: " + request.getAttributeName());
        }

        // it can be write-only though
        boolean writeOnly = !aInfo.isReadable();

        // Old value, will throw an exception if attribute is not known. That's good.
        Object currentAttributeValue = writeOnly ? null : server.getAttribute(request.getObjectName(), request.getAttributeName());

        Values values;
        if (aInfo instanceof OpenMBeanAttributeInfo info) {
            // MXBean case - we'll be converting the incoming value to an OpenType
            values = getOpenTypeAttribute(info.getOpenType(), currentAttributeValue, request);
        } else {
            // generic conversion
            values = handleGenericAttribute(aInfo.getType(), currentAttributeValue, request);
        }

        Attribute attribute = new Attribute(request.getAttributeName(), values.newValue);
        server.setAttribute(request.getObjectName(), attribute);

        return values.oldValue;
    }

    private Values getOpenTypeAttribute(OpenType<?> pOpenType, Object pCurrentValue, JolokiaWriteRequest pRequest) throws BadRequestException {
        // we treat OpenTypes and MXBeans as "whole" values and we don't support getting part of it (using path)
        if (pRequest.getPathParts() != null && !pRequest.getPathParts().isEmpty()) {
            // no BadRequestException here - simply the server MBean doesn't support inner path. So error 500
            throw new IllegalArgumentException("Cannot set value for OpenType " + pOpenType + " with inner path " +
                pRequest.getPath() + " since OpenTypes are immutable");
        }

        try {
            Object newValue = context.getMandatoryService(Serializer.class).deserializeOpenType(pOpenType, pRequest.getValue());
            return new Values(newValue, pCurrentValue);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            // because deserialization of user-provided data is involved, we turn these into BadRequestException
            throw new BadRequestException("Can't process " + pRequest + ": " + e.getMessage(), e);
        }
    }

    /**
     * Prepare an Object to be set for an attribute or its inner value if {@link JolokiaRequest#getPathParts() a path}
     * is specified. Without a path, we simply return a value to be passed to
     * {@link MBeanServerConnection#setAttribute(ObjectName, Attribute)}. With a path, the "inner value"
     * will already be set in the attribute, which is then re-set via JMX.
     *
     * @param pType         type of the outermost object to set as returned by an MBeanInfo structure.
     * @param pCurrentValue the object of the outermost object which can be null
     * @param pRequest      the initial request
     * @return
     * @throws AttributeNotFoundException if no such attribute exists (as specified in the request)
     */
    private Values handleGenericAttribute(String pType, Object pCurrentValue, JolokiaWriteRequest pRequest)
            throws JMException, BadRequestException {
        List<String> pathParts = pRequest.getPathParts();
        Object newValue = pRequest.getValue();

        if (pathParts != null && !pathParts.isEmpty() && pCurrentValue == null) {
            // server-side problem - error 500
            throw new IllegalArgumentException(
                "Cannot set value with path when parent object is not set");
        }

        try {
            if (pathParts != null && !pathParts.isEmpty()) {
                // We set an inner value, hence we have to return provided value itself for resetting
                // it later back via JMX
                Object oldInnerValue;
                try {
                    oldInnerValue = context.getMandatoryService(Serializer.class).setInnerValue(pCurrentValue, newValue, pathParts);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    // reflection based exception
                    throw new JMRuntimeException(e.getMessage());
                }
                // we already set the inner value, but because MBeanServerConnection.getAttribute() may have
                // returned a copy (?), we return pCurrentValue as new value to set with MBeanServerConnection.setAttribute()
                return new Values(pCurrentValue, oldInnerValue);
            } else {
                // the value to be set will be deserialized to a proper type and set as a JMX attribute by the caller
                return new Values(context.getMandatoryService(Serializer.class).deserialize(pType, newValue), pCurrentValue);
            }
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            // because deserialization of user-provided data is involved, we turn these into BadRequestException
            throw new BadRequestException("Can't process " + pRequest + ": " + e.getMessage(), e);
        }
    }

    private record Values(Object newValue, Object oldValue) {
    }

}
