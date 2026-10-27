package io.github.to_o.libredex.captionfix;

/** Only bridge ordinary application windows on the currently managed wired desktop. */
final class TaskbarPolicy {
    private TaskbarPolicy() {}

    static boolean shouldBridge(boolean enabled, boolean active, int managedDisplay,
            int taskDisplay, int windowingMode, int activityType) {
        return enabled && active && managedDisplay > 1 && taskDisplay == managedDisplay
                && windowingMode == 5 && activityType == 1;
    }
}
