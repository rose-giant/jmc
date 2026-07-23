package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

/**
 * Builds and emits the observability events for this package's primitives: {@code MP_SEND_EVENT},
 * {@code MP_RECV_EVENT}, {@code MP_RECV_BLOCKED_EVENT} (added to {@code JmcRuntimeEvent.Type} —
 * see the enum edit below). These don't drive any control flow today — the actual mechanics live
 * in {@link Mailbox} — they exist so a future consistency-checking strategy has a uniform, minimal
 * vocabulary to intercept (source/destination/model/value), and so the currently-configured
 * strategy gets a scheduling point after every message-passing action, same as every other jmc
 * primitive.
 */
final class MpEvent {

    private MpEvent() {}

    static void send(Tid src, Tid dst, CommunicationModel model, Object value) {
        JmcRuntime.updateEventAndYield(
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.MP_SEND_EVENT)
                        .taskId(src.raw())
                        .param("src", src)
                        .param("dst", dst)
                        .param("model", model)
                        .param("value", value)
                        .build());
    }

    static void received(Tid at, Tid from, CommunicationModel model, Object value) {
        JmcRuntime.updateEventAndYield(
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.MP_RECV_EVENT)
                        .taskId(at.raw())
                        .param("at", at)
                        .param("from", from)
                        .param("model", model)
                        .param("value", value)
                        .build());
    }

    /** Emitted when a non-blocking receive found nothing (the "&perp;" case). */
    static void receivedBottom(Tid at, CommunicationModel model) {
        JmcRuntime.updateEventAndYield(
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.MP_RECV_EVENT)
                        .taskId(at.raw())
                        .param("at", at)
                        .param("model", model)
                        .param("bottom", true)
                        .build());
    }

    /** Emitted right before a blocking receive parks (may fire more than once per receive, once
     *  per failed poll — that's fine, it's observability only). No yield: the wait/notify protocol
     *  {@link Mailbox} calls immediately after already yields. */
    static void blocked(Tid at, CommunicationModel model) {
        JmcRuntime.updateEvent(
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.MP_RECV_BLOCKED_EVENT)
                        .taskId(at.raw())
                        .param("at", at)
                        .param("model", model)
                        .build());
    }
}