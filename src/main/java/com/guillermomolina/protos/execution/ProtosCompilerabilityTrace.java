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

import com.oracle.truffle.api.Truffle;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TEST009-T activation of the diagnostic-only compilerability causal trace.
 *
 * <p>The trace is enabled only by the private JVM property {@value #PROPERTY}{@code =true}, which
 * {@code tools/truffle_compilation_gate.py diagnose} passes and no strict gate or ordinary launch
 * does. When the property is absent, nothing is registered and nothing is printed; the remaining
 * cost is one constant check per Context creation and per lowered semantic root ({@link
 * #ENABLED} is a static final, folded by the JIT).
 *
 * <p>When enabled, the listener is installed once per JVM, on the first Protos Context creation
 * (no compilation can precede it), and its events are written to {@link System#err}: the same
 * stream as the engine's textual compilation traces, so the harness can order both.
 */
final class ProtosCompilerabilityTrace {
    static final String PROPERTY = "protos.compilerability.causalTrace";
    static final boolean ENABLED = isEnabled(System.getProperty(PROPERTY));

    private static final Activation ACTIVATION =
            new Activation(ENABLED, ProtosCompilerabilityTrace::install);

    private ProtosCompilerabilityTrace() {}

    static boolean isEnabled(String propertyValue) {
        return "true".equals(propertyValue);
    }

    static void activateIfEnabled() {
        ACTIVATION.activate();
    }

    /** Runs {@code installer} at most once, and never when disabled; safe under concurrent use. */
    static final class Activation {
        private final boolean enabled;
        private final Runnable installer;
        private final AtomicBoolean installed = new AtomicBoolean();

        Activation(boolean enabled, Runnable installer) {
            this.enabled = enabled;
            this.installer = installer;
        }

        void activate() {
            if (enabled && installed.compareAndSet(false, true)) {
                installer.run();
            }
        }
    }

    private static void install() {
        ProtosCompilerabilityListenerBridge.install(
                Truffle.getRuntime(),
                new ProtosCompilerabilityRecorder(ProtosCompilerabilityTrace::writeLine));
    }

    private static void writeLine(String line) {
        PrintStream err = System.err;
        synchronized (err) {
            err.println(line);
            err.flush();
        }
    }
}
