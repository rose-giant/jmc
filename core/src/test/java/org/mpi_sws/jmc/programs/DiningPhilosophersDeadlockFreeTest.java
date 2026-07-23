package org.mpi_sws.jmc.programs;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.CommunicationModel;
import org.mpi_sws.jmc.api.mp.MpMessage;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Tid;

public class DiningPhilosophersDeadlockFreeTest {

    private static final CommunicationModel MODEL = CommunicationModel.P2P;
    private static final int CYCLES = 2;

    record WirePhil(Tid forkL, Tid forkR, boolean requestRightFirst) implements MpMessage {}
    record WireFork(Tid philL, Tid philR) implements MpMessage {}
    record Arrive() implements MpMessage {}
    record Permit(Tid sender) implements MpMessage {}
    record Eat() implements MpMessage {}
    record Leave() implements MpMessage {}
    record Request(Tid sender) implements MpMessage {}
    record Release(Tid sender) implements MpMessage {}
    record Done(Tid sender) implements MpMessage {}

    private static void runPhilosopher(Tid self) {
        WirePhil wire = Channel.recv(MODEL, WirePhil.class);
        Tid forkL = wire.forkL();
        Tid forkR = wire.forkR();
        Tid firstFork = wire.requestRightFirst() ? forkR : forkL;
        Tid secondFork = wire.requestRightFirst() ? forkL : forkR;

        for (int cycle = 0; cycle < CYCLES; cycle++) {
            boolean gotFirst = false, gotSecond = false, cycleDone = false;
            Channel.send(MODEL, self, new Arrive());
            while (!cycleDone) {
                Object msg = Channel.recv(MODEL);
                if (msg instanceof Arrive) {
                    Channel.send(MODEL, firstFork, new Request(self));
                } else if (msg instanceof Permit p) {
                    if (p.sender().equals(firstFork) && !gotFirst) {
                        gotFirst = true;
                        Channel.send(MODEL, secondFork, new Request(self));
                    } else if (p.sender().equals(secondFork) && gotFirst && !gotSecond) {
                        gotSecond = true;
                        Channel.send(MODEL, self, new Eat());
                    }
                } else if (msg instanceof Eat) {
                    Channel.send(MODEL, self, new Leave());
                } else if (msg instanceof Leave) {
                    Channel.send(MODEL, forkL, new Release(self));
                    Channel.send(MODEL, forkR, new Release(self));
                    cycleDone = true;
                }
            }
        }
        Channel.send(MODEL, forkL, new Done(self));
        Channel.send(MODEL, forkR, new Done(self));
    }

    private static void runFork(Tid self) {
        WireFork wire = Channel.recv(MODEL, WireFork.class);
        Tid philL = wire.philL();
        Tid philR = wire.philR();

        boolean lAssign = false, rAssign = false, leftReq = false, rightReq = false;
        boolean leftDone = false, rightDone = false;

        while (!leftDone || !rightDone) {
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
            } else if (msg instanceof Done d) {
                if (d.sender().equals(philL)) leftDone = true;
                else rightDone = true;
            }
        }
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 200)
    public void runDiningPhilosophers2DeadlockFree() {
        MpProcess.Handle phil0 = MpProcess.spawn(() -> runPhilosopher(Tid.current()));
        MpProcess.Handle phil1 = MpProcess.spawn(() -> runPhilosopher(Tid.current()));
        MpProcess.Handle fork0 = MpProcess.spawn(() -> runFork(Tid.current()));
        MpProcess.Handle fork1 = MpProcess.spawn(() -> runFork(Tid.current()));

        Channel.send(MODEL, phil0.tid(), new WirePhil(fork0.tid(), fork1.tid(), false));
        Channel.send(MODEL, phil1.tid(), new WirePhil(fork1.tid(), fork0.tid(), true));
        Channel.send(MODEL, fork0.tid(), new WireFork(phil0.tid(), phil1.tid()));
        Channel.send(MODEL, fork1.tid(), new WireFork(phil1.tid(), phil0.tid()));

        phil0.join();
        phil1.join();
        fork0.join();
        fork1.join();
    }
}
