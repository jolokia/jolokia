package org.jolokia.handler;

import org.jolokia.JmxRequest;
import org.jolokia.config.Restrictor;

import javax.management.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

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
 * Handler responsible for searching for MBean names.
 * @author roland
 * @since Jun 18, 2009
 */
public class SearchHandler extends JsonRequestHandler {

    // Pattern for value in which case the value needs to be escaped
    private static final Pattern INVALID_CHARS_PATTERN = Pattern.compile(":\",=\\*?");

    public SearchHandler(Restrictor pRestrictor) {
        super(pRestrictor);
    }

    @Override
    public JmxRequest.Type getType() {
        return JmxRequest.Type.SEARCH;
    }

    @Override
    @SuppressWarnings("PMD.ReplaceHashtableWithMap")
    public Object doHandleRequest(MBeanServerConnection server, JmxRequest request)
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
