package org.mpi_sws.jmc.api.mp;

import java.util.function.Predicate;

/**
 * The monitor notification wrappers from the Must paper &sect;1.6 — a monitor is just an ordinary
 * process that receives {@link Notification}s under {@link CommunicationModel#CD} (required for
 * soundness: under {@link CommunicationModel#P2P}, notifications from different senders could be
 * reordered even when the monitored sends are causally related). {@link #sendNotified}/{@link
 * #recvNotified} are the generic wrappers, verbatim:
 *
 * <pre>
 * send_notᴹ(T′, m, Tm) ≜ send^cd(Tm, Send(T, T′, m)); sendᴹ(T′, m)
 * recv_notᴹ(λx : e, Tm) ≜ m := recvᴹ(λx : e); send^cd(Tm, Recv(T, m))
 * </pre>
 *
 * <p>A monitor needs no special infrastructure: spawn it with {@link MpProcess#spawn} like any
 * other process, and have its body {@code recv} {@link Notification}s under {@link
 * CommunicationModel#CD} in a loop, asserting on unexpected orderings via {@code
 * org.mpi_sws.jmc.api.util.statements.JmcAssert}.
 */
public final class Monitors {

    private Monitors() {}

    /** A notification a monitor observes. */
    public sealed interface Notification extends MpMessage
            permits Notification.Send, Notification.Recv {

        /** {@code from} sent {@code message} to {@code to}. */
        record Send(Tid from, Tid to, Object message) implements Notification {}

        /** {@code at} received {@code message}. */
        record Recv(Tid at, Object message) implements Notification {}
    }

    /** {@code send_notᴹ(dst, value, monitor)}. */
    public static void sendNotified(CommunicationModel model, Tid dst, MpMessage value, Tid monitor) {
        Channel.send(CommunicationModel.CD, monitor, new Notification.Send(Tid.current(), dst, value));
        Channel.send(model, dst, value);
    }

    /** {@code recv_notᴹ(λx : e, monitor)}. */
    public static Object recvNotified(CommunicationModel model, Predicate<Object> guard, Tid monitor) {
        Object value = Channel.recv(model, guard);
        Channel.send(CommunicationModel.CD, monitor, new Notification.Recv(Tid.current(), value));
        return value;
    }
}