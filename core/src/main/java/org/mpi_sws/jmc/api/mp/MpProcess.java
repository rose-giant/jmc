package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.api.util.concurrent.JmcThread;

/**
 * Spawns and joins Must processes on top of jmc's existing task machinery — {@link JmcThread}, the
 * same drop-in {@code Thread} replacement bytecode instrumentation already substitutes for
 * {@code Thread}, reused directly rather than re-implemented, since it already handles task-ID
 * minting and {@code START}/{@code FINISH}/join event bookkeeping correctly.
 */
public final class MpProcess {

    private MpProcess() {}

    /** Spawns {@code body} as a new process and starts it immediately. */
    public static Handle spawn(Runnable body) {
        JmcThread thread = new JmcThread(body);
        thread.start();
        return new Handle(Tid.of(thread.getTaskId()), thread);
    }

    /** A handle to a spawned process. */
    public static final class Handle {
        private final Tid tid;
        private final JmcThread thread;

        private Handle(Tid tid, JmcThread thread) {
            this.tid = tid;
            this.thread = thread;
        }

        /** This process's {@link Tid}, for addressing {@code send}s to it. */
        public Tid tid() {
            return tid;
        }

        /** Blocks (cooperatively, like every other jmc join) until the process terminates. */
        public void join() {
            try {
                thread.join1();
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while joining an MpProcess", e);
            }
        }
    }
}