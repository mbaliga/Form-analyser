package com.google.android.datatransport.cct;

import com.google.android.datatransport.runtime.Destination;

/**
 * Stand-in for the "cct" destination that MediaPipe's usage-statistics client selects. It carries
 * no endpoint or API key, so nothing here can address a server. See
 * {@link com.google.android.datatransport.runtime.TransportRuntime}.
 */
public final class CCTDestination implements Destination {
    public static final CCTDestination INSTANCE = new CCTDestination();
    public static final CCTDestination LEGACY_INSTANCE = INSTANCE;

    private CCTDestination() {}

    @Override
    public String getName() {
        return "cct";
    }

    @Override
    public byte[] getExtras() {
        return null;
    }
}
