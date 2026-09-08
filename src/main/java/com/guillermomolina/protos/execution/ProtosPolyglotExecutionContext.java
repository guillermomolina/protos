/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */


package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import org.graalvm.polyglot.Context;

/**
 * One host-owned, thread-confined Polyglot context entered for Protos execution.
 *
 * <p>This is implementation machinery, not a Protos semantic context or an Actor. Keeping the
 * Polyglot context entered lets all parsing performed by this execution session resolve the exact
 * current {@link ProtosLanguageContext} without global mutable state or a host-side language
 * singleton. The semantic execution domain and {@link ProtosActivation} remain the existing Protos
 * runtime authorities.
 */
public final class ProtosPolyglotExecutionContext implements AutoCloseable {
    private final Context context;
    private final Thread ownerThread;
    private boolean closed;

    private ProtosPolyglotExecutionContext(Context context, Thread ownerThread) {
        this.context = Objects.requireNonNull(context, "context");
        this.ownerThread = Objects.requireNonNull(ownerThread, "ownerThread");
    }

    public static ProtosPolyglotExecutionContext open(
            InputStream in, OutputStream out, OutputStream err) {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");

        Context context =
                Context.newBuilder(ProtosLanguage.ID)
                        .in(in)
                        .out(out)
                        .err(err)
                        .build();
        boolean entered = false;
        try {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            entered = true;
            return new ProtosPolyglotExecutionContext(context, Thread.currentThread());
        } finally {
            if (!entered) {
                context.close();
            }
        }
    }

    /**
     * Parses through the registered public Protos language and runs one semantic root task.
     *
     * <p>The returned value is the existing inert {@link ProtosExecutionOutcome}; this bridge does
     * not invent a second Polyglot-visible representation for Protos runtime values before I026-D
     * defines their faithful interop view.
     */
    public ProtosExecutionOutcome execute(Source source, ProtosActivation activation) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(activation, "activation");

        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return ProtosRootTaskExecution.execute(target, activation);
    }

    private void requireOpenOnOwnerThread() {
        if (closed) {
            throw new IllegalStateException("Polyglot Protos execution context is closed");
        }
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "Polyglot Protos execution context is confined to its owner thread");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOpenOnOwnerThread();
        closed = true;

        RuntimeException failure = null;
        try {
            context.leave();
        } catch (RuntimeException leaveFailure) {
            failure = leaveFailure;
        }
        try {
            context.close();
        } catch (RuntimeException closeFailure) {
            if (failure == null) {
                failure = closeFailure;
            } else {
                failure.addSuppressed(closeFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
