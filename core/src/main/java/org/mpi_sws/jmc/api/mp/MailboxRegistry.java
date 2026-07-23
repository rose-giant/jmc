package main.java.org.mpi_sws.jmc.api.mp;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-iteration registry mapping each destination {@link Tid} to its {@link Mailbox}, plus the
 * single global sequence counter used to give every {@link Envelope} a real arrival order (which
 * {@link CommunicationModel#MBOX} delivery reads directly).
 *
 * <p>Not synchronized, for the same reason as {@link Mailbox}: jmc fully serializes task
 * execution, so there is never real concurrent access to this state.
 *
 * <p>Reset between {@code @JmcCheck} iterations: {@link Channel}'s static initializer registers
 * {@link #reset()} with {@code JmcRuntimeUtils.registerIterationResetHook} (see the edit to
 * {@code JmcRuntimeUtils.java} below) — otherwise mailboxes and sequence numbers from a previous
 * iteration would leak into the next, since this static state lives in the (non-instrumented)
 * core module and jmc's own static-init replay machinery only re-runs {@code <clinit>} for
 * instrumented target-code classes, not core library classes like this one.
 */
final class MailboxRegistry {

    private static final Map<Long, Mailbox> mailboxes = new HashMap<>();
    private static long seqCounter = 0;

    private MailboxRegistry() {}

    static Mailbox get(Tid owner) {
        return mailboxes.computeIfAbsent(owner.raw(), raw -> new Mailbox());
    }

    static long nextSeq() {
        return seqCounter++;
    }

    static void reset() {
        mailboxes.clear();
        seqCounter = 0;
    }
}
