package org.mpi_sws.jmc.programs;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.CommunicationModel;
import org.mpi_sws.jmc.api.mp.MpMessage;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Tid;

public class DiningPhilosophersTest {

    private static final CommunicationModel MODEL = CommunicationModel.P2P;
    private static final int MAX_MESSAGES = 16; // demo bound; see note above

    record WirePhil(Tid forkL, Tid forkR) implements MpMessage {}
    record WireFork(Tid philL, Tid philR) implements MpMessage {}
    record Arrive() implements MpMessage {}
    record Permit(Tid sender) implements MpMessage {}
    record Eat() implements MpMessage {}
    record Leave() implements MpMessage {}
    record Request(Tid sender) implements MpMessage {}
    record Release(Tid sender) implements MpMessage {}

    private static void runPhilosopher(Tid self) {
        WirePhil wire = Channel.recv(MODEL, WirePhil.class);
        Tid forkL = wire.forkL();
        Tid forkR = wire.forkR();

        boolean fL = false, fR = false, eating = false;
        Channel.send(MODEL, self, new Arrive());

        for (int processed = 0; processed < MAX_MESSAGES; processed++) {
            Object msg = Channel.recv(MODEL);
            if (msg instanceof Arrive) {
                Channel.send(MODEL, forkL, new Request(self));
            } else if (msg instanceof Permit p) {
                if (p.sender().equals(forkL)) {
                    if (!fL) {
                        fL = true;
                        Channel.send(MODEL, forkR, new Request(self));
                    }
                } else if (fL && !fR) {
                    fR = true;
                    Channel.send(MODEL, self, new Eat());
                }
            } else if (msg instanceof Eat) {
                eating = true;
                Channel.send(MODEL, self, new Leave());
            } else if (msg instanceof Leave) {
                eating = false;
                fL = false;
                fR = false;
                Channel.send(MODEL, forkL, new Release(self));
                Channel.send(MODEL, forkR, new Release(self));
                Channel.send(MODEL, self, new Arrive());
            }
        }
    }

    private static void runFork(Tid self) {
        WireFork wire = Channel.recv(MODEL, WireFork.class);
        Tid philL = wire.philL();
        Tid philR = wire.philR();

        boolean lAssign = false, rAssign = false, leftReq = false, rightReq = false;

        for (int processed = 0; processed < MAX_MESSAGES; processed++) {
            Object msg = Channel.recv(MODEL);
            if (msg instanceof Request r) {
                if (r.sender().equals(philL)) {
                    if (!leftReq) {
                        leftReq = true;
                        if (!rAssign) {
                            lAssign = true;
                            Channel.send(MODEL, philL, new Permit(self));
                        }
                    }
                } else if (!rightReq) {
                    rightReq = true;
                    if (!lAssign) {
                        rAssign = true;
                        Channel.send(MODEL, philR, new Permit(self));
                    }
                }
            } else if (msg instanceof Release r) {
                if (r.sender().equals(philL) && lAssign) {
                    leftReq = false;
                    lAssign = false;
                    if (rightReq) {
                        rAssign = true;
                        Channel.send(MODEL, philR, new Permit(self));
                    }
                }
                if (r.sender().equals(philR) && rAssign) {
                    rAssign = false;
                    rightReq = false;
                    if (leftReq) {
                        lAssign = true;
                        Channel.send(MODEL, philL, new Permit(self));
                    }
                }
            }
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200)
    public void runDiningPhilosophers2() {
        MpProcess.Handle phil0 = MpProcess.spawn(() -> runPhilosopher(Tid.current()));
        MpProcess.Handle phil1 = MpProcess.spawn(() -> runPhilosopher(Tid.current()));
        MpProcess.Handle fork0 = MpProcess.spawn(() -> runFork(Tid.current()));
        MpProcess.Handle fork1 = MpProcess.spawn(() -> runFork(Tid.current()));

        Channel.send(MODEL, phil0.tid(), new WirePhil(fork0.tid(), fork1.tid()));
        Channel.send(MODEL, phil1.tid(), new WirePhil(fork1.tid(), fork0.tid()));
        Channel.send(MODEL, fork0.tid(), new WireFork(phil0.tid(), phil1.tid()));
        Channel.send(MODEL, fork1.tid(), new WireFork(phil1.tid(), phil0.tid()));

        phil0.join();
        phil1.join();
        fork0.join();
        fork1.join();
    }
}