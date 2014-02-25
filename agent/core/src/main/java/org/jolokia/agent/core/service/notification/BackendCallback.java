package org.jolokia.agent.core.service.notification;

import javax.management.NotificationListener;

/**
 * Callback which receives the notification. This is not much more
 * than a notification listener for now.
 *
 * @author roland
 * @since 18.03.13
 */
public interface BackendCallback extends NotificationListener {
}
