package com.dex.lspmirror;

/** Shell organizers own their desktop roots and must receive their native lifecycle. */
final class DpTaskCleanupPolicy {
    private DpTaskCleanupPolicy() {}

    static boolean shouldRemove(int retiredDisplayId, int taskDisplayId,
            int activityType, Boolean createdByOrganizer) {
        return retiredDisplayId > 0 && taskDisplayId == retiredDisplayId
                && activityType == 2 /* HOME */
                && Boolean.FALSE.equals(createdByOrganizer);
    }
}
