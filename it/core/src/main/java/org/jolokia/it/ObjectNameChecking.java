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

package org.jolokia.it;

/**
 * We need to use MBeanRegisration because Websphere wont let us set our name
 * directly while registering (it always add some boilerplate to the name). Using this
 * way, it works (so the names under which we register correspond to those in the
 * integration test).
 *
 * @author roland
 * @since Jun 25, 2009
 */
public class ObjectNameChecking implements ObjectNameCheckingMBean {
    public String getOk() {
        return "OK";
    }
}
