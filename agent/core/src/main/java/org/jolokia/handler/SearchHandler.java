package org.jolokia.handler;

import org.jolokia.request.*;
import org.jolokia.config.Restrictor;

import javax.management.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * Handler responsible for searching for MBean names.
 * @author roland
 * @since Jun 18, 2009
 */
public class SearchHandler extends JsonRequestHandler<JmxSearchRequest> {

    // Pattern for value in which case the value needs to be escaped
    private static final Pattern INVALID_CHARS_PATTERN = Pattern.compile(":\",=\\*?");

    public SearchHandler(Restrictor pRestrictor) {
        super(pRestrictor);
    }

    @Override
    public RequestType getType() {
        return RequestType.SEARCH;
    }

    @Override
    @SuppressWarnings("PMD.ReplaceHashtableWithMap")
    public Object doHandleRequest(MBeanServerConnection server, JmxSearchRequest request)
            throws InstanceNotFoundException, AttributeNotFoundException, MBeanException, IOException {
        Set<ObjectName> names = server.queryNames(request.getObjectName(),null);
        if (names == null || names.size() == 0) {
            throw new InstanceNotFoundException("No MBean with pattern " + request.getObjectNameAsString() + " found");
        }
        List<String> ret = new ArrayList<String>();
        for (ObjectName name : names) {

            // Check whether the property-list values needs to be escaped:
            Map<String,String> props = name.getKeyPropertyList();
            // We need a hashtable since ObjectName requires one.
            Hashtable<String,String> escapedProps = new Hashtable<String, String>();
            boolean needsEscape = false;
            for (Map.Entry<String,String> entry : props.entrySet()) {
                String value = entry.getValue();
                if (INVALID_CHARS_PATTERN.matcher(entry.getValue()).find()) {
                    value = ObjectName.quote(value);
                    needsEscape = true;
                }
                escapedProps.put(entry.getKey(),value);
            }
            if (needsEscape) {
                try {
                    ret.add(new ObjectName(name.getDomain(),escapedProps).getCanonicalName());
                } catch (MalformedObjectNameException e) {
                    throw new MBeanException(e,"Cannot properly escape " + name.getCanonicalName());
                }
            } else {
                ret.add(name.getCanonicalName());
            }
        }
        return ret;
    }
}
