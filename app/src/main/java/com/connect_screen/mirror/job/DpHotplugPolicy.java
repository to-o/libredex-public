package com.connect_screen.mirror.job;

/** Tracks physical display identity, including a fast replug with a new ID. */
public final class DpHotplugPolicy {
    public enum Change { NONE, CONNECT, DISCONNECT, REPLACE }
    private int displayId = -1;

    public Change update(int nextDisplayId) {
        int next = nextDisplayId > 0 ? nextDisplayId : -1;
        if (next == displayId) return Change.NONE;
        boolean hadDisplay = displayId > 0;
        displayId = next;
        if (hadDisplay && next > 0) return Change.REPLACE;
        return next > 0 ? Change.CONNECT : Change.DISCONNECT;
    }
}
