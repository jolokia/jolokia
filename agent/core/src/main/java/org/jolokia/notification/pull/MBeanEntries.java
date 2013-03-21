package org.jolokia.notification.pull;

import java.util.Map;

import javax.management.ObjectName;

/**
 * @author roland
 * @since 21.03.13
 */
public class MBeanEntries {

    private Map<ObjectName, FilterEntries> notifications;
}
