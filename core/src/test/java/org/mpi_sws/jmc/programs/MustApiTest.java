package org.mpi_sws.jmc.programs;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.CommunicationModel;
import org.mpi_sws.jmc.api.mp.Monitors;
import org.mpi_sws.jmc.api.mp.MpMessage;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.api.mp.Tid;
import org.mpi_sws.jmc.api.util.statements.JmcAssert;
//the CommunicationModel, MpMessage, Nondet, and Tid files haven't been added yet,

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Worked examples of the Must API port (org.mpi_sws.jmc.api.mp), meant to be read top-to-bottom as
 * documentation as much as run as tests. None of these assert exhaustive-exploration guarantees —
 * that's the deliberately-deferred DPOR/state-space work (see the package documentation on
 * org.mpi_sws.jmc.api.mp) — they only check the API behaves correctly for whatever executions the
 * configured strategy actually produces.
 */
public class MustApiTest {

    record Ping(int seq) implements MpMessage {}
    record Pong(int seq) implements MpMessage {}
    record Tagged(int value) implements MpMessage {}
    record Note(String who) implements MpMessage {}

    // 1. Basic send/recv: a two-process ping-pong under ASYN.
    private void pingPong() {
        Tid main = Tid.current();
        MpProcess.Handle ponger = MpProcess.spawn(() -> {
            Ping ping = Channel.recv(CommunicationModel.ASYN, Ping.class);
            Channel.send(CommunicationModel.ASYN, main, new Pong(ping.seq()));
        });
        Channel.send(CommunicationModel.ASYN, ponger.tid(), new Ping(1));
        Pong pong = Channel.recv(CommunicationModel.ASYN, Pong.class);
        JmcAssert.check(pong.seq() == 1, "expected the ponger to echo seq 1");
        ponger.join();
    }

    // 2. P2P is FIFO per sender: one sender's two messages must arrive in send order.
    private void p2pFifoOrdering() {
        Tid main = Tid.current();
        MpProcess.Handle sender = MpProcess.spawn(() -> {
            Channel.send(CommunicationModel.P2P, main, new Tagged(1));
            Channel.send(CommunicationModel.P2P, main, new Tagged(2));
        });
        Tagged first = Channel.recv(CommunicationModel.P2P, Tagged.class);
        Tagged second = Channel.recv(CommunicationModel.P2P, Tagged.class);
        JmcAssert.check(first.value() == 1 && second.value() == 2,
                "P2P must deliver one sender's messages in send order");
        sender.join();
    }

    // 3. nondet() plus a selective receive predicate.
    private void selectiveReceiveAndNondet() {
        Tid main = Tid.current();
        MpProcess.Handle sender = MpProcess.spawn(() -> {
            int choice = Nondet.chooseIndex(2); // 0 or 1
            Channel.send(CommunicationModel.ASYN, main, new Tagged(choice));
        });
        Tagged received = Channel.recv(
                CommunicationModel.ASYN, Tagged.class, t -> t.value() == 0 || t.value() == 1);
        JmcAssert.check(received.value() == 0 || received.value() == 1, "nondet chose 0 or 1");
        sender.join();
    }

    // 4. Non-blocking receive must return promptly (the "bottom" case) when nothing is pending.
    private void nonBlockingReceiveReturnsEmpty() {
        Optional<Tagged> result = Channel.tryRecv(CommunicationModel.ASYN, Tagged.class);
        JmcAssert.check(result.isEmpty(), "tryRecv must not block when no message is pending");
    }

    // 5. A monitor observing two ASYN sends via CD-delivered notifications (Must paper §1.6).
    // NOTE: this only checks the plumbing works end-to-end; it deliberately does not assert a
    // specific observed order, since that would require the exhaustive exploration this port does
    // not yet provide (a future MUST-aware strategy is exactly what would let this assert "both
    // orders are explored, and the assertion fails in the one that violates the property").
    private void monitorExample() {
        Tid main = Tid.current();
        CopyOnWriteArrayList<String> observed = new CopyOnWriteArrayList<>();
        MpProcess.Handle monitor = MpProcess.spawn(() -> {
            for (int i = 0; i < 2; i++) {
                Monitors.Notification.Send note =
                        Channel.recv(CommunicationModel.CD, Monitors.Notification.Send.class);
                observed.add(((Note) note.message()).who());
            }
        });
        MpProcess.Handle senderA = MpProcess.spawn(() ->
                Monitors.sendNotified(CommunicationModel.ASYN, main, new Note("A"), monitor.tid()));
        MpProcess.Handle senderB = MpProcess.spawn(() ->
                Monitors.sendNotified(CommunicationModel.ASYN, main, new Note("B"), monitor.tid()));
        senderA.join();
        senderB.join();
        Channel.recv(CommunicationModel.ASYN, Note.class); // drain A's payload to main
        Channel.recv(CommunicationModel.ASYN, Note.class); // drain B's payload to main
        monitor.join();
        JmcAssert.check(observed.size() == 2, "monitor should observe exactly 2 sends");
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200)
    public void runPingPong() {
        pingPong();
    }

    //    todo: fix this test
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200)
    public void runP2pFifoOrdering() {
        p2pFifoOrdering();
    }

    //    todo: fix this test
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200)
    public void runSelectiveReceiveAndNondet() {
        selectiveReceiveAndNondet();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 50)
    public void runNonBlockingReceiveReturnsEmpty() {
        nonBlockingReceiveReturnsEmpty();
    }

//    todo: fix this test
    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200)
    public void runMonitorExample() {
        monitorExample();
    }
}