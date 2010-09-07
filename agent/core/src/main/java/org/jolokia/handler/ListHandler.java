package org.jolokia.handler;


import org.jolokia.JmxRequest;
import org.jolokia.config.Restrictor;

import javax.management.*;
import java.io.IOException;
import java.util.*;

/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
 */

/**
 * Handler for obtaining a list of all available MBeans and its attributes
 * and operations.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ListHandler extends JsonRequestHandler {

    // Properties for JSON answer
    private static final String KEY_DESCRIPTION = "desc";
    private static final String KEY_ERROR = "error";
    private static final String KEY_NAME = "name";
    private static final String KEY_TYPES = "types";
    private static final String KEY_ARGS = "args";
    private static final String KEY_RETURN = "ret";
    private static final String KEY_OPERATION = "op";
    private static final String KEY_TYPE = "type";
    private static final String KEY_NOTIFICATION = "not";
    private static final String KEY_READ_WRITE = "rw";
    private static final String KEY_ATTRIBUTE = "attr";

    public JmxRequest.Type getType() {
        return JmxRequest.Type.LIST;
    }

    public ListHandler(Restrictor pRestrictor) {
        super(pRestrictor);
    }

    @Override
    public boolean handleAllServersAtOnce(JmxRequest pRequest) {
        return true;
    }

    @Override
    public Object doHandleRequest(Set<MBeanServerConnection> pServers, JmxRequest request)
            throws InstanceNotFoundException, IOException {
        try {
            Map<String /* domain */,
                    Map<String /* props */,
                            Map<String /* attribute/operation/error */,
                                    List<String /* names */>>>> ret =
                    new HashMap<String, Map<String, Map<String, List<String>>>>();
            for (MBeanServerConnection server : pServers) {
                for (Object nameObject : server.queryNames((ObjectName) null,(QueryExp) null)) {
                    ObjectName name = (ObjectName) nameObject;
                    Map mBeansMap = getOrCreateMap(ret,name.getDomain());
                    Map mBeanMap = getOrCreateMap(mBeansMap,name.getCanonicalKeyPropertyListString());

                    try {
                        MBeanInfo mBeanInfo = server.getMBeanInfo(name);
                        mBeanMap.put(KEY_DESCRIPTION,mBeanInfo.getDescription());
                        addAttributes(mBeanMap, mBeanInfo);
                        addOperations(mBeanMap, mBeanInfo);
                        addNotifications(mBeanMap, mBeanInfo);
                        // Trim if needed
                        if (mBeanMap.size() == 0) {
                            mBeansMap.remove(name.getCanonicalKeyPropertyListString());
                            if (mBeansMap.size() == 0) {
                                ret.remove(name.getDomain());
                            }
                        }
                    } catch (IOException exp) {
                        // In case of a remote call, IOEcxeption can occur e.g. for
                        // NonSerializableExceptions
                        mBeanMap.put(KEY_ERROR,exp);
                    }
                }
            }
            return ret;
        } catch (ReflectionException e) {
            throw new IllegalStateException("Internal error while retrieving list: " + e,e);
        } catch (IntrospectionException e) {
            throw new IllegalStateException("Internal error while retrieving list: " + e,e);
        }

    }

    private void addNotifications(Map pMBeanMap,MBeanInfo pMBeanInfo) {
        Map notMap = new HashMap();
        for (MBeanNotificationInfo notInfo : pMBeanInfo.getNotifications()) {
            Map map = new HashMap();
            map.put(KEY_NAME,notInfo.getName());
            map.put(KEY_DESCRIPTION,notInfo.getDescription());
            map.put(KEY_TYPES,notInfo.getNotifTypes());
        }
        if (notMap.size() > 0) {
            pMBeanMap.put(KEY_NOTIFICATION,notMap);
        }
    }

    private void addOperations(Map pMBeanMap, MBeanInfo pMBeanInfo) {
        // Extract operations
        Map opMap = new HashMap();
        for (MBeanOperationInfo opInfo : pMBeanInfo.getOperations()) {
            Map map = new HashMap();
            List argList = new ArrayList();
            for (MBeanParameterInfo paramInfo :  opInfo.getSignature()) {
                Map args = new HashMap();
                args.put(KEY_DESCRIPTION,paramInfo.getDescription());
                args.put(KEY_NAME,paramInfo.getName());
                args.put(KEY_TYPE,paramInfo.getType());
                argList.add(args);
            }
            map.put(KEY_ARGS,argList);
            map.put(KEY_RETURN,opInfo.getReturnType());
            map.put(KEY_DESCRIPTION,opInfo.getDescription());
            Object ops = opMap.get(opInfo.getName());
            if (ops != null) {
                if (ops instanceof List) {
                    // If it is already a list, simply add it to the end
                    ((List) ops).add(map);
                } else if (ops instanceof Map) {
                    // If it is a map, add a list with two elements
                    // (the old one and the new one)
                    List opList = new ArrayList();
                    opList.add(ops);
                    opList.add(map);
                    opMap.put(opInfo.getName(), opList);
                } else {
                    throw new IllegalArgumentException("Internal: list, addOperations: Expected Map or List, not "
                            + ops.getClass());
                }
            } else {
                // No value set yet, simply add the map as plain value
                opMap.put(opInfo.getName(),map);
            }
        }
        if (opMap.size() > 0) {
            pMBeanMap.put(KEY_OPERATION,opMap);
        }
    }

    private void addAttributes(Map pMBeanMap, MBeanInfo pMBeanInfo) {
        // Extract attributes
        Map attrMap = new HashMap();
        for (MBeanAttributeInfo attrInfo : pMBeanInfo.getAttributes()) {
            Map map = new HashMap();
            map.put(KEY_TYPE,attrInfo.getType());
            map.put(KEY_DESCRIPTION,attrInfo.getDescription());
            map.put(KEY_READ_WRITE,Boolean.valueOf(attrInfo.isWritable() && attrInfo.isReadable()));
            attrMap.put(attrInfo.getName(),map);
        }
        if (attrMap.size() > 0) {
            pMBeanMap.put(KEY_ATTRIBUTE,attrMap);
        }
    }

    private Map getOrCreateMap(Map pMap, String pKey) {
        Map nMap = (Map) pMap.get(pKey);
        if (nMap == null) {
            nMap = new HashMap();
            pMap.put(pKey,nMap);
        }
        return nMap;
    }

    // will not be called
    @Override
    public Object doHandleRequest(MBeanServerConnection server, JmxRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
        return null;
    }


}
