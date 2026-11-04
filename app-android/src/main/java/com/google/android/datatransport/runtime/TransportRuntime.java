package com.google.android.datatransport.runtime;

import android.content.Context;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.TransportScheduleCallback;
import java.util.concurrent.atomic.AtomicLong;

/**
 * No-op replacement for the datatransport runtime.
 *
 * <p>MediaPipe Tasks (tasks-core) contains a usage-statistics client, {@code TasksStatsProtoLogger}
 * to {@code RemoteLoggingClient}, that reports the app's package name and version, the task name
 * and running mode, and latency statistics to Google through datatransport's "cct" backend.
 * Crocodyl has no analytics in any channel, so {@code transport-runtime} and
 * {@code transport-backend-cct} are excluded from every configuration (see
 * {@code app-android/build.gradle.kts}) and this class supplies the only entry points
 * {@code RemoteLoggingClient} links against. It has the same public signatures as the real
 * runtime; every event handed to it is counted and discarded, never encoded, stored or sent, and
 * the transformer that would serialise the payload is never invoked.
 *
 * <p>{@code transport-api} stays on the classpath because MediaPipe needs its interfaces
 * ({@code Transport}, {@code TransportFactory}, {@code Event}, {@code Encoding}, ...).
 */
public final class TransportRuntime {
    /** Events handed to the sink and dropped. Read by tests; nothing else uses it. */
    public static final AtomicLong DROPPED_EVENTS = new AtomicLong();

    private static final TransportFactory DROPPING_FACTORY = new DroppingTransportFactory();
    private static final TransportRuntime INSTANCE = new TransportRuntime();

    private TransportRuntime() {}

    public static void initialize(Context context) {
        // Nothing to initialise: no scheduler, no uploader, no backend registry.
    }

    public static TransportRuntime getInstance() {
        return INSTANCE;
    }

    public TransportFactory newFactory(String backendName) {
        return DROPPING_FACTORY;
    }

    public TransportFactory newFactory(Destination destination) {
        return DROPPING_FACTORY;
    }

    private static final class DroppingTransportFactory implements TransportFactory {
        @Override
        public <T> Transport<T> getTransport(
                String name, Class<T> payloadType, Transformer<T, byte[]> payloadTransformer) {
            return new DroppingTransport<>();
        }

        @Override
        public <T> Transport<T> getTransport(
                String name,
                Class<T> payloadType,
                Encoding payloadEncoding,
                Transformer<T, byte[]> payloadTransformer) {
            return new DroppingTransport<>();
        }
    }

    private static final class DroppingTransport<T> implements Transport<T> {
        @Override
        public void send(Event<T> event) {
            DROPPED_EVENTS.incrementAndGet();
        }

        @Override
        public void schedule(Event<T> event, TransportScheduleCallback callback) {
            DROPPED_EVENTS.incrementAndGet();
            if (callback != null) {
                callback.onSchedule(null);
            }
        }
    }
}
