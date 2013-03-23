package org.jolokia.notification.pull;

import java.util.*;

import javax.management.Notification;

import org.jolokia.notification.NotificationSubscription;

/**
 * Store for a single subscription. It holds the notifications collected
 * and also a counter for dropped notifications.
 *
 * @author roland
 * @since 21.03.13
 */
public class NotificationStore {

    // maximum number of entries to hold
    private int maxEntries;

    // the set of notification, sorted by sequence number
    private SortedSet<Notification> entries;

    // number of dropped notifications because the max limit has been reached
    private int dropped;

    // subscription for this notification
    private final NotificationSubscription subscription;

    /**
     * Create a new notification store for holding concrete notifications which
     * can be fetched (and cleared).
     *
     * @param pSubscription
     * @param pMaxEntries how many to store maximal. Older entries will
     *                    be shifted out. The attribute skippedNotification contains the number
     */
    public NotificationStore(NotificationSubscription pSubscription, int pMaxEntries) {
        subscription = pSubscription;
        entries = Collections.synchronizedSortedSet(new TreeSet<Notification>(getComparator()));
        maxEntries = pMaxEntries;
        dropped = 0;
    }

    /**
     * Add a notification to this store
     * @param pNotification notification to add
     */
    synchronized void add(Notification pNotification) {
        if (entries.size() >= maxEntries) {
            entries.remove(entries.last());
            dropped++;
        }
        entries.add(pNotification);
    }

    /**
     * Fetch notification and clear the list
     *
     * @return list of notifications, ordered by sequence number
     */
    List<Notification> fetchAndClear() {
        ArrayList<Notification> notifs = new ArrayList<Notification>(entries);
        entries.clear();
        subscription.ping();
        return notifs;
    }

    /**
     * Get the number of dropped notification
     *
     * @return dropped notifications
     */
    int getDropped() {
        return dropped;
    }

    // ====================================================================================

    // Comparator based on sequence number
    private Comparator<? super Notification> getComparator() {
        return new Comparator<Notification>() {
            /** {@inheritDoc} */
            public int compare(Notification o1, Notification o2) {
                return (int) (o1.getSequenceNumber() - o2.getSequenceNumber());
            }
        };
    }
}
