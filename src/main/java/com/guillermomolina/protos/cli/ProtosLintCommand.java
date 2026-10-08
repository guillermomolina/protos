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

import com.guillermomolina.protos.analysis.ProtosDocumentSnapshot;
import com.guillermomolina.protos.analysis.ProtosStaticAnalysisCore;
import com.guillermomolina.protos.analysis.ProtosStaticLint;
import com.guillermomolina.protos.analysis.ProtosStaticLintDiagnostic;
import com.guillermomolina.protos.analysis.ProtosStaticParseResult;
import com.guillermomolina.protos.source.SourcePosition;
import com.guillermomolina.protos.source.SourceSpan;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * D195 {@code protos lint [--output text|json] [--fail-on-warning] [<file>]}.
 *
 * <p>A purely static adapter over {@link ProtosStaticAnalysisCore}: it parses the one
 * source exactly once and runs {@link ProtosStaticLint} only on a successful parse. It
 * creates no Polyglot Context, Process, guest carrier, or project index, so
 * {@link ProtosCli#run} dispatches it on the calling thread. It never writes files.</p>
 *
 * <p>Exit codes: 0 valid source (warnings allowed); 1 parser error, or warnings under
 * {@code --fail-on-warning}; 2 usage error; 3 source acquisition or UTF-8 failure. Unexpected
 * failures propagate to the caller's internal-error boundary (70). Diagnostics are written to
 * stdout only after analysis completes, so every operational failure leaves stdout empty.</p>
 */
final class ProtosLintCommand {
    static final String STDIN_SOURCE = "<stdin>";

    private ProtosLintCommand() {}

    private enum Output {
        TEXT,
        JSON
    }

    /** One reported diagnostic; {@code code} is {@code null} for parser errors. */
    private record Report(
            String origin, String code, String severity, String message, SourceSpan span) {}

    /** @param args the arguments following {@code lint} */
    static int run(List<String> args, InputStream in, PrintStream out, PrintStream err)
            throws IOException {
        Output output = null;
        boolean failOnWarning = false;
        String sourceArgument = null;
        for (int index = 0; index < args.size(); index++) {
            String arg = args.get(index);
            if (arg.equals("--output")) {
                if (output != null) {
                    return ProtosCli.usage(err, "lint: --output given more than once");
                }
                if (index + 1 >= args.size()) {
                    return ProtosCli.usage(err, "lint: --output requires text or json");
                }
                String value = args.get(++index);
                if (value.equals("text")) {
                    output = Output.TEXT;
                } else if (value.equals("json")) {
                    output = Output.JSON;
                } else {
                    return ProtosCli.usage(err, "lint: invalid --output value: " + value);
                }
            } else if (arg.equals("--fail-on-warning")) {
                if (failOnWarning) {
                    return ProtosCli.usage(err, "lint: --fail-on-warning given more than once");
                }
                failOnWarning = true;
            } else if (arg.startsWith("-")) {
                return ProtosCli.usage(err, "lint: unknown option: " + arg);
            } else if (sourceArgument != null) {
                return ProtosCli.usage(err, "lint accepts at most one source file");
            } else {
                sourceArgument = arg;
            }
        }
        if (output == null) {
            output = Output.TEXT;
        }

        String source = sourceArgument == null ? STDIN_SOURCE : sourceArgument;
        String characters;
        try {
            byte[] bytes = sourceArgument == null ? in.readAllBytes() : readFile(sourceArgument);
            characters = decodeUtf8(bytes);
        } catch (CharacterCodingException failure) {
            return inputFailure(source, "malformed UTF-8 input", err);
        } catch (IOException | InvalidPathException failure) {
            return inputFailure(source, failure.getMessage(), err);
        }

        // The source identity is a report label only; it is never a module identity.
        ProtosDocumentSnapshot snapshot = new ProtosDocumentSnapshot(source, 0L, characters);
        ProtosStaticParseResult parsed = new ProtosStaticAnalysisCore().parse(snapshot);
        boolean valid;
        List<Report> reports;
        if (parsed instanceof ProtosStaticParseResult.Parsed success) {
            valid = true;
            // ProtosStaticLint owns the canonical order: start, end, then rule ID.
            reports = ProtosStaticLint.check(success).stream()
                    .map(ProtosLintCommand::lintReport)
                    .toList();
        } else {
            ProtosStaticParseResult.Failed failure = (ProtosStaticParseResult.Failed) parsed;
            valid = false;
            reports = List.of(
                    new Report("parser", null, "error", failure.message(), failure.span()));
        }

        String rendered = output == Output.JSON
                ? json(source, characters, valid, reports)
                : text(source, characters, reports);
        out.writeBytes(rendered.getBytes(StandardCharsets.UTF_8));
        out.flush();
        if (!valid || (failOnWarning && !reports.isEmpty())) {
            return 1;
        }
        return 0;
    }

    private static byte[] readFile(String sourceArgument) throws IOException {
        Path path = Path.of(sourceArgument);
        // Both checks follow symbolic links, so a link to a regular file is accepted.
        if (!Files.exists(path)) {
            throw new IOException("no such file");
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("not a regular file");
        }
        return Files.readAllBytes(path);
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static int inputFailure(String source, String reason, PrintStream err) {
        err.println("protos lint: cannot read " + source + ": " + reason);
        return 3;
    }

    private static Report lintReport(ProtosStaticLintDiagnostic finding) {
        String severity = switch (finding.severity()) {
            case WARNING -> "warning";
        };
        return new Report("lint", finding.ruleId(), severity, finding.message(), finding.span());
    }

    private static String text(String source, String characters, List<Report> reports) {
        StringBuilder text = new StringBuilder();
        for (Report report : reports) {
            SourcePosition start = SourcePosition.at(characters, report.span().startOffset());
            SourcePosition end = SourcePosition.at(characters, report.span().endOffset());
            text.append(source)
                    .append(':').append(start.line()).append(':').append(start.character())
                    .append('-').append(end.line()).append(':').append(end.character())
                    .append(": ").append(report.severity())
                    .append(" [").append(report.origin());
            if (report.code() != null) {
                text.append(' ').append(report.code());
            }
            text.append("]: ").append(report.message()).append('\n');
        }
        return text.toString();
    }

    private static String json(
            String source, String characters, boolean valid, List<Report> reports) {
        StringBuilder json = new StringBuilder();
        json.append("{\"schemaVersion\":1,\"source\":");
        appendString(json, source);
        json.append(",\"status\":\"").append(valid ? "valid" : "invalid").append("\"");
        json.append(",\"diagnostics\":[");
        for (int index = 0; index < reports.size(); index++) {
            Report report = reports.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"origin\":\"").append(report.origin()).append("\",\"code\":");
            if (report.code() == null) {
                json.append("null");
            } else {
                appendString(json, report.code());
            }
            json.append(",\"severity\":\"").append(report.severity()).append("\",\"message\":");
            appendString(json, report.message());
            json.append(",\"range\":{\"start\":");
            appendPosition(json, SourcePosition.at(characters, report.span().startOffset()));
            json.append(",\"end\":");
            appendPosition(json, SourcePosition.at(characters, report.span().endOffset()));
            json.append("}}");
        }
        return json.append("]}\n").toString();
    }

    private static void appendPosition(StringBuilder json, SourcePosition position) {
        json.append("{\"line\":").append(position.line())
                .append(",\"character\":").append(position.character()).append('}');
    }

    /**
     * Appends an RFC 8259 string. Non-ASCII text is emitted verbatim (the stream is UTF-8);
     * an unpaired surrogate, which UTF-8 cannot carry, is escaped instead.
     */
    static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                default -> {
                    if (Character.isHighSurrogate(current)
                            && index + 1 < value.length()
                            && Character.isLowSurrogate(value.charAt(index + 1))) {
                        json.append(current).append(value.charAt(++index));
                    } else if (current < 0x20 || Character.isSurrogate(current)) {
                        json.append(String.format("\\u%04x", (int) current));
                    } else {
                        json.append(current);
                    }
                }
            }
        }
        json.append('"');
    }
}
