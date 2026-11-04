package com.google.android.datatransport.runtime;

/**
 * Same shape as the real transport-runtime interface, which Crocodyl excludes from every build.
 * See {@link TransportRuntime}.
 */
public interface Destination {
    String getName();

    byte[] getExtras();
}
