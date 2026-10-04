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

package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.runtime.ProtosDiagnosticTrace;
import java.io.PrintStream;

/**
 * D063 S3 guest stack presentation for an uncaught Error (CLI008-C1).
 *
 * <p>Kept apart from program output and from {@link ProtosDiagnosticInspector} value inspection:
 * it renders only the inert trace of the terminal occurrence, one line per semantic guest frame,
 * innermost first. A label is printed only when the trace genuinely carries one; no host, Java or
 * Truffle frame can appear because the trace contains none. Evolvable CLI presentation, not a
 * print/serialization contract.
 */
final class ProtosGuestStackRenderer {
    private ProtosGuestStackRenderer() {}

    static void render(ProtosDiagnosticTrace trace, PrintStream err) {
        if (trace == null) {
            return;
        }
        for (ProtosDiagnosticTrace.Frame frame : trace.frames()) {
            err.println(frameLine(frame));
        }
        if (trace.truncated()) {
            err.println("  ...");
        }
    }

    static String frameLine(ProtosDiagnosticTrace.Frame frame) {
        String position =
                frame.knownDisplayPath().orElse(frame.sourceName())
                        + ":"
                        + frame.line()
                        + ":"
                        + frame.column();
        return frame.knownLabel()
                .map(label -> "  at " + label + " (" + position + ")")
                .orElse("  at " + position);
    }
}
