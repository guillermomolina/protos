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

import com.oracle.truffle.api.source.Source;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;

/**
 * TEST009-V diagnostic-only stable identity of a semantic Truffle compilation unit.
 *
 * <p>The optimizing runtime names a call target after its root's {@code toString()}, and {@code
 * engine.CompileOnly} selects targets by substring of that name. The default {@code
 * ClassName@identityHash} text cannot select the same logical root in a later JVM, so while the
 * private JVM property {@value #PROPERTY}{@code =true} is set (only by {@code
 * tools/truffle_root_diagnostic.py}):
 *
 * <ul>
 *   <li>the lowerer records each semantic root's exact {@link Span} and prints one catalog line
 *       ({@value #CATALOG_PREFIX}...) to {@link System#err}, without compiling anything;
 *   <li>{@link ProtosSemanticBytecodeRootNode#toString()} returns {@link #targetName}: the
 *       {@link #selector} followed by the human-readable metadata it was derived from. Generated
 *       continuation roots append their resume index to that text.
 * </ul>
 *
 * <p>The selector digests only the root kind, source URI and exact span: never object identity,
 * generated node ids or the (quickened, mutable) instruction stream. A root without a recorded
 * span is named {@link #UNAVAILABLE}, which no selector matches: the diagnostic fails closed
 * instead of falling back to an address. When the property is absent, nothing is recorded or
 * printed and root naming is the Truffle default; {@link #ENABLED} is a static final.
 */
final class ProtosDiagnosticRootIdentity {
    static final String PROPERTY = "protos.diagnostic.stableRootIdentity";
    static final boolean ENABLED = isEnabled(System.getProperty(PROPERTY));

    static final String TOP_LEVEL = "TOP_LEVEL";
    static final String CLOSURE = "CLOSURE";

    static final String SELECTOR_PREFIX = "protos-root:";
    static final String CATALOG_PREFIX = "[protos-root] ";
    static final String UNAVAILABLE = SELECTOR_PREFIX + "unavailable[SEMANTIC_ROOT_SPAN_UNRECORDED]";
    private static final int SELECTOR_DIGEST_BYTES = 8;

    private ProtosDiagnosticRootIdentity() {}

    /**
     * The exact root span the lowerer knew when it ended a semantic root. Its own {@code
     * getSourceSection()} is not usable: source information is lazy and materializing it reparses
     * the root, which an observational diagnostic must never do. Immutable; never read by guest
     * execution.
     */
    record Span(Source source, int start, int length, String kind) {
        String sourceUri() {
            return source.getURI().toString();
        }
    }

    static boolean isEnabled(String propertyValue) {
        return "true".equals(propertyValue);
    }

    /** Records {@code span} on a freshly lowered root and catalogs it; a reparse keeps the first. */
    static void record(ProtosSemanticBytecodeRootNode root, Span span) {
        record(root, span, ProtosDiagnosticRootIdentity::writeLine);
    }

    static void record(ProtosSemanticBytecodeRootNode root, Span span, Consumer<String> catalog) {
        if (root.diagnosticRootSpan() == null) {
            root.recordDiagnosticRootSpan(span);
            catalog.accept(catalogLine(span));
        }
    }

    /** {@code protos-root:} plus a fixed-width digest, so no selector is a substring of another. */
    static String selector(Span span) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        String material = String.join("\u0000", span.kind(), span.sourceUri(),
                Integer.toString(span.start()), Integer.toString(span.length()));
        byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(SELECTOR_PREFIX);
        for (int index = 0; index < SELECTOR_DIGEST_BYTES; index++) {
            out.append(String.format("%02x", hash[index]));
        }
        return out.toString();
    }

    /**
     * The call-target name: selector plus {@code [kind|uri|start+length]}. It contains no space
     * (the URI is encoded), so it stays one token of the engine compilation trace.
     */
    static String targetName(Span span) {
        if (span == null) {
            return UNAVAILABLE;
        }
        return selector(span) + "[" + span.kind() + "|" + span.sourceUri() + "|" + span.start()
                + "+" + span.length() + "]";
    }

    static String catalogLine(Span span) {
        return CATALOG_PREFIX + "selector=" + selector(span) + " kind=" + span.kind() + " source="
                + span.sourceUri() + " start=" + span.start() + " length=" + span.length();
    }

    private static void writeLine(String line) {
        PrintStream err = System.err;
        synchronized (err) {
            err.println(line);
            err.flush();
        }
    }
}
