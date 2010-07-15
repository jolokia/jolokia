package org.jolokia;

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
 * Class holding the version of this agent. This gets updated automatically
 * when jmx4perl is build.
 *
 * @author roland
 * @since Jun 11, 2009
 */
public final class Version {

    private static final String VERSION = "0.70.0";

    // Major.Minor version of protocol
    private static final String PROTOCOL = "3.1";

    private Version() {}

    public static String getAgentVersion() {
        return VERSION;
    }

    public static String getProtocolVersion() {
        return PROTOCOL;
    }
}
