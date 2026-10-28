package com.dex.lspmirror;

import java.util.HashMap;
import java.util.Map;

/** Tracks retired displays independently of the currently configured display. */
final class DpDisplayCleanupState {
    static final class Request {
        final int displayId;

        private Request(int displayId) {
            this.displayId = displayId;
        }
    }

    private int currentDisplayId = -1;
    private final Map<Integer, Request> pending = new HashMap<>();

    synchronized Request observe(int configuredDisplayId) {
        int next = configuredDisplayId > 0 ? configuredDisplayId : -1;
        if (next == currentDisplayId) return null;
        int previous = currentDisplayId;
        currentDisplayId = next;
        // Reactivating an ID cancels every cleanup from its previous session.
        pending.remove(next);
        if (previous <= 0) return null;
        Request request = new Request(previous);
        pending.put(previous, request);
        return request;
    }

    synchronized boolean isPending(Request request, int liveConfiguredDisplayId) {
        return request != null && request.displayId > 0
                && request.displayId != liveConfiguredDisplayId
                && request.displayId != currentDisplayId
                && pending.get(request.displayId) == request;
    }

    synchronized void finish(Request request) {
        if (request != null && pending.get(request.displayId) == request) {
            pending.remove(request.displayId);
        }
    }
}
