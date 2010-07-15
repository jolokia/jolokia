package org.jolokia.osgi.bundle;

import org.jolokia.osgi.JolokiaActivator;
import org.ops4j.pax.web.service.jetty.internal.CompositeActivator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/*
 * osgish - An OSGi Shell
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
 * Activator for activation the embedded jolokia agent as well
 * as the Aries JMX bundle. So it's an aggregat activator.
 *
 * It also registers an (arbitrary) MBeanServer if not already
 * an MBeanServer is registered. This service is required by Aries JMX.

 * @author roland
 * @since Jan 9, 2010
 */
public class Activator implements BundleActivator {

    // Jolokia Activator
    private JolokiaActivator jolokiaActivator;

    // Pax-Web Activator
    private CompositeActivator paxWebActivator;

    public Activator() {
        jolokiaActivator = new JolokiaActivator();
        paxWebActivator = new CompositeActivator();
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public void start(BundleContext pContext) throws Exception {
        paxWebActivator.start(pContext);
        jolokiaActivator.start(pContext);
    }

    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public void stop(BundleContext pContext) throws Exception {
        jolokiaActivator.stop(pContext);
        paxWebActivator.stop(pContext);
    }

}
