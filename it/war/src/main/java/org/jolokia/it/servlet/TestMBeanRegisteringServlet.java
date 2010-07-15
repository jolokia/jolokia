/*
 * jmx4perl - Servlet for registering MBeans for jmx4perl integration test suite
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
 * A commercial license is available as well. You can either apply the GPL or
 * obtain a commercial license for closed source development. Please contact
 * roland@cpan.org for further information.
 */
package org.jolokia.it.servlet;


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.jolokia.it.ItSetup;


public class TestMBeanRegisteringServlet extends HttpServlet {

    ItSetup itSetup;

    @Override
    public void init(ServletConfig config) throws ServletException {
        itSetup = new ItSetup();
        itSetup.start();
    }

    @Override
    public void destroy() {
        itSetup.stop();
    }


}
