package io.github.to_o.libredex.captionfix;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CaptionPolicyTest {
    @Test public void activeWiredFloatingWindowUsesHeader() {
        assertTrue(CaptionPolicy.shouldUseHeader(true, true, 7, 7, 5, 0));
    }

    @Test public void mainAndCoverScreensAreNeverChanged() {
        assertFalse(CaptionPolicy.shouldUseHeader(true, true, 7, 0, 5, 0));
        assertFalse(CaptionPolicy.shouldUseHeader(true, true, 7, 1, 5, 0));
        assertFalse(CaptionPolicy.shouldUseHeader(true, true, 0, 0, 5, 0));
        assertFalse(CaptionPolicy.shouldUseHeader(true, true, 1, 1, 5, 0));
    }

    @Test public void anotherExternalOrVirtualDisplayIsNotChanged() {
        assertFalse(CaptionPolicy.shouldUseHeader(true, true, 7, 8, 5, 0));
        assertFalse(CaptionPolicy.shouldUseHeader(true, true, -1, 7, 5, 0));
    }

    @Test public void inactiveOrDisabledSessionIsNotChanged() {
        assertFalse(CaptionPolicy.shouldUseHeader(true, false, 7, 7, 5, 0));
        assertFalse(CaptionPolicy.shouldUseHeader(false, true, 7, 7, 5, 0));
    }

    @Test public void fullscreenSplitAndPictureInPictureAreNotChanged() {
        for (int mode : new int[]{0, 1, 2, 3, 4, 6}) {
            assertFalse(CaptionPolicy.shouldUseHeader(true, true, 7, 7, mode, 0));
        }
    }

    @Test public void existingHeadersAndUnknownCaptionTypesArePreserved() {
        for (int caption : new int[]{-1, 1, 2, 3}) {
            assertFalse(CaptionPolicy.shouldUseHeader(true, true, 7, 7, 5, caption));
        }
    }
}
