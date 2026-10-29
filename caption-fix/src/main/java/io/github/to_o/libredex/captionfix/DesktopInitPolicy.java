package io.github.to_o.libredex.captionfix;

final class DesktopInitPolicy {
    private DesktopInitPolicy() {}

    static boolean shouldInitialize(boolean enabled, String firmware, boolean active,
            int managedDisplay, int requestedDisplay) {
        return enabled && active && "F7310ZCS7FZA1".equals(firmware) && managedDisplay > 1
                && requestedDisplay != managedDisplay;
    }

    static boolean isReady(int managedDisplay, int nativeDisplay, Object activeDesk) {
        return managedDisplay > 1 && nativeDisplay == managedDisplay && activeDesk != null;
    }
}
