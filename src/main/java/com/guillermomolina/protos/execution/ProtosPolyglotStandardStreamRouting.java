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

import com.guillermomolina.protos.runtime.ProtosProcessStandardStreamBinding;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Debug-only bridge from Protos Process standard-stream writes to the currently entered
 * Truffle context output channels.
 *
 * <p>GraalVM DAP observes language output through Instrumenter out/err consumers. Protos Process
 * standard streams are explicit runtime capabilities and ordinarily write directly to their host
 * backends, so debug invocations route only those guest writes through {@code Env.out()} and
 * {@code Env.err()}. Normal execution does not use this bridge.
 */
public final class ProtosPolyglotStandardStreamRouting {
    private ProtosPolyglotStandardStreamRouting() {}

    public static ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend() {
        return backend(false);
    }

    public static ProtosProcessStandardStreamBinding.WritableBackend stderrBackend() {
        return backend(true);
    }

    private static ProtosProcessStandardStreamBinding.WritableBackend backend(boolean stderr) {
        return (bytes, completion) -> {
            try {
                if (!ProtosPolyglotExecutionContext.hasEnteredContextForRuntime()) {
                    completion.failed(0);
                    return () -> {};
                }
                ProtosLanguageContext context = ProtosLanguageContext.current();
                OutputStream target = stderr ? context.env().err() : context.env().out();
                target.write(bytes, 0, bytes.length);
                completion.succeeded();
            } catch (IOException | RuntimeException failure) {
                completion.failed(0);
            }
            return () -> {};
        };
    }
}
