package io.github.to_o.libredex.captionfix;

/** Keep the workaround confined to ordinary floating windows on the active wired desktop. */
final class CaptionPolicy {
    private CaptionPolicy() {}

    static boolean shouldUseHeader(boolean enabled, boolean sessionActive, int managedDisplay,
            int taskDisplay, int windowingMode, int currentCaptionType) {
        // Display 0 is the main screen and 1 is the Flip cover; neither may be changed.
        return enabled && sessionActive && managedDisplay > 1 && taskDisplay == managedDisplay
                && windowingMode == 5 && currentCaptionType == 0;
    }
}
