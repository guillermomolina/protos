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

import com.guillermomolina.protos.execution.*;
import com.guillermomolina.protos.parser.ParseError;
import com.guillermomolina.protos.runtime.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jline.reader.*;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.*;

public final class ProtosCli {
    private final ProtosValueRenderer renderer = new ProtosValueRenderer();

    public static void main(String[] args) {
        int code = new ProtosCli().run(args, System.in, System.out, System.err);
        if (code != 0) System.exit(code);
    }

    public int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0) return repl(in, out, err);
            if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
                help(out);
                return 0;
            }
            if (args.length == 1 && (args[0].equals("--version") || args[0].equals("-v"))) {
                String v = getClass().getPackage().getImplementationVersion();
                out.println("Protos " + (v == null ? "development" : v));
                return 0;
            }
            if (args[0].equals("-e")) {
                if (args.length < 2) return usage(err, "-e requires a source argument");
                return evalOneShot(
                        args[1],
                        applicationArguments(args, 2),
                        in,
                        out,
                        err);
            }
            if (args[0].startsWith("-")) {
                return usage(err, "unknown option: " + args[0]);
            }

            String src;
            try {
                src = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
            } catch (IOException e) {
                err.println(
                        "protos: cannot read "
                                + args[0]
                                + ": "
                                + e.getMessage());
                return 1;
            }
            return evalOneShot(
                    src,
                    applicationArguments(args, 1),
                    in,
                    out,
                    err);
        } catch (Throwable e) {
            err.println("Internal error: " + e);
            e.printStackTrace(err);
            return 70;
        }
    }

    private static List<String> applicationArguments(String[] args, int start) {
        return List.copyOf(
                Arrays.asList(Arrays.copyOfRange(args, start, args.length)));
    }

    private int evalOneShot(
            String source,
            List<String> applicationArguments,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        Session session = session(applicationArguments, in, out, err);
        try {
            return eval(source, session, out, err);
        } finally {
            session.terminate();
        }
    }

    private int repl(InputStream in, PrintStream out, PrintStream err) throws IOException {
        Session s = session(List.of(), in, out, err);
        try {
            out.println("Protos REPL\nType :help for help, :quit to exit.");
            return in == System.in
                    ? interactiveRepl(s, out, err)
                    : streamRepl(in, s, out, err);
        } finally {
            s.terminate();
        }
    }

    private int interactiveRepl(Session s, PrintStream out, PrintStream err)
            throws IOException {
        try (Terminal terminal =
                TerminalBuilder.builder()
                        .system(true)
                        .streams(System.in, System.out)
                        .build()) {
            LineReader reader =
                    LineReaderBuilder.builder()
                            .terminal(terminal)
                            .history(new ReplHistory())
                            .option(LineReader.Option.BRACKETED_PASTE, true)
                            .build();
            StringBuilder pending = new StringBuilder();
            for (;;) {
                try {
                    String input =
                            reader.readLine(
                                    pending.isEmpty()
                                            ? "protos> "
                                            : "...> ");
                    ReplInputResult result =
                            processInputChunk(input, pending, s, out, err);
                    if (result == ReplInputResult.EXIT) return 0;
                } catch (UserInterruptException e) {
                    pending.setLength(0);
                } catch (EndOfFileException e) {
                    out.println();
                    return 0;
                }
            }
        }
    }

    private int streamRepl(
            InputStream in, Session s, PrintStream out, PrintStream err)
            throws IOException {
        var reader =
                new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder pending = new StringBuilder();
        for (;;) {
            out.print(pending.isEmpty() ? "protos> " : "...> ");
            out.flush();
            String line = reader.readLine();
            if (line == null) {
                out.println();
                return 0;
            }
            ReplInputResult result =
                    processInputChunk(line, pending, s, out, err);
            if (result == ReplInputResult.EXIT) return 0;
        }
    }

    private ReplInputResult processInputChunk(
            String input,
            StringBuilder pending,
            Session s,
            PrintStream out,
            PrintStream err) {
        if (isExit(input)) {
            pending.setLength(0);
            return ReplInputResult.EXIT;
        }
        if (input.equals(":help")) {
            processReturnedInput(input, s, out, err);
            return pending.isEmpty()
                    ? ReplInputResult.COMPLETE
                    : ReplInputResult.INCOMPLETE;
        }

        String source =
                pending.isEmpty() ? input : pending + "\n" + input;
        ReplInputResult result =
                processReturnedInput(source, s, out, err);
        pending.setLength(0);
        if (result == ReplInputResult.INCOMPLETE) pending.append(source);
        return result;
    }

    private ReplInputResult processReturnedInput(
            String input, Session s, PrintStream out, PrintStream err) {
        if (input.isBlank()) return ReplInputResult.COMPLETE;
        if (isExit(input)) return ReplInputResult.EXIT;
        if (input.equals(":help")) {
            out.println(
                    ":help  show help\n"
                            + ":quit  exit\n"
                            + ":exit  exit\n"
                            + "Arrow keys edit lines and navigate session history.\n"
                            + "Ctrl-A/Ctrl-E move to start/end; Ctrl-C cancels; Ctrl-D exits.\n"
                            + "Top-level context persists between evaluations.\n"
                            + "The REPL Process has an empty process.args() snapshot.");
            return ReplInputResult.COMPLETE;
        }
        try {
            out.println(
                    renderer.render(
                            s.compiler.compile(input).call(s.activation)));
            return ReplInputResult.COMPLETE;
        } catch (ParseError e) {
            if (e.isUnexpectedEndOfSource()) {
                return ReplInputResult.INCOMPLETE;
            }
            err.println("Syntax error: " + e.getMessage());
            return ReplInputResult.COMPLETE;
        } catch (ProtosSignalException e) {
            err.println("Error: " + renderer.render(e.error()));
            return ReplInputResult.COMPLETE;
        } catch (RuntimeException e) {
            err.println("Runtime error: " + e.getMessage());
            return ReplInputResult.COMPLETE;
        }
    }

    private static boolean isExit(String line) {
        return line.equals(":quit") || line.equals(":exit");
    }

    /*
     * Kept for existing REPL reflection/conformance tests. Production entry paths use the
     * argument/stream-aware overload below.
     */
    private Session session() throws IOException {
        return session(
                List.of(),
                InputStream.nullInputStream(),
                new PrintStream(OutputStream.nullOutputStream()),
                new PrintStream(OutputStream.nullOutputStream()));
    }

    private Session session(
            List<String> applicationArguments,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        Path core = core();
        ProtosModuleResolver moduleResolver =
                new ProtosStandardLibraryModuleResolver(core.getParent());
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(core, moduleResolver);
        ProtosEncodingValue utf8 = utf8(prelude);

        ProtosStandaloneProcessBootstrap.Result bootstrap =
                ProtosStandaloneProcessBootstrap.create(
                        prelude,
                        applicationArguments,
                        HOST_ENVIRONMENT_NAME_DOMAIN,
                        hostEnvironmentEntries(),
                        readableBackend(in),
                        writableBackend(out),
                        writableBackend(err),
                        utf8,
                        utf8,
                        utf8,
                        null);

        return new Session(
                new ProtosSourceCompiler(),
                bootstrap.activation(),
                bootstrap.process());
    }

    private static ProtosEncodingValue utf8(ProtosPrelude prelude) {
        Object value =
                prelude.encodingPrototype()
                        .readLocalSlot("UTF8")
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Core Encoding.UTF8 is missing"));
        if (!(value instanceof ProtosEncodingValue encoding)) {
            throw new IllegalStateException(
                    "Core Encoding.UTF8 is not an Encoding descriptor");
        }
        return encoding;
    }

    private static List<ProtosEnvironmentValue.NativeEntry>
            hostEnvironmentEntries() {
        ArrayList<ProtosEnvironmentValue.NativeEntry> entries =
                new ArrayList<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            entries.add(
                    new ProtosEnvironmentValue.NativeEntry(
                            entry.getKey(), entry.getValue()));
        }
        return List.copyOf(entries);
    }

    /**
     * Use the JDK's native ProcessBuilder environment map itself as the probe for native
     * environment-name representability and name identity. This avoids inventing a POSIX/Windows
     * Unicode case-folding policy in Core and does not mutate this JVM's real environment.
     */
    private static final ProtosEnvironmentValue.NativeNameDomain
            HOST_ENVIRONMENT_NAME_DOMAIN =
                    new ProtosEnvironmentValue.NativeNameDomain() {
                        @Override
                        public boolean sameCapturedName(
                                String left, String right) {
                            return nativeEnvironmentNameMatches(
                                    left, right);
                        }

                        @Override
                        public boolean isQueryRepresentable(String name) {
                            Map<String, String> probe =
                                    new ProcessBuilder().environment();
                            probe.clear();
                            try {
                                probe.put(name, "");
                                return probe.size() == 1
                                        && probe.containsKey(name);
                            } catch (IllegalArgumentException
                                    | NullPointerException invalid) {
                                return false;
                            }
                        }

                        @Override
                        public boolean matchesQuery(
                                String captured, String query) {
                            return nativeEnvironmentNameMatches(
                                    captured, query);
                        }
                    };

    private static boolean nativeEnvironmentNameMatches(
            String captured, String query) {
        Map<String, String> probe = new ProcessBuilder().environment();
        probe.clear();
        try {
            probe.put(captured, "");
            return probe.containsKey(query);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return false;
        }
    }

    private static ProtosProcessStandardStreamBinding.ReadableBackend
            readableBackend(InputStream in) {
        return (maxBytes, completion) -> {
            Thread worker =
                    Thread.ofVirtual()
                            .name("protos-stdin-read")
                            .start(
                                    () -> {
                                        ByteArrayOutputStream captured =
                                                new ByteArrayOutputStream(
                                                        Math.min(
                                                                maxBytes,
                                                                8192));
                                        try {
                                            int first = in.read();
                                            if (first < 0) {
                                                completion.eof();
                                                return;
                                            }
                                            captured.write(first);

                                            while (captured.size() < maxBytes) {
                                                int available = in.available();
                                                if (available <= 0) break;
                                                int wanted =
                                                        Math.min(
                                                                maxBytes
                                                                        - captured
                                                                                .size(),
                                                                available);
                                                byte[] more =
                                                        in.readNBytes(wanted);
                                                if (more.length == 0) break;
                                                captured.write(
                                                        more,
                                                        0,
                                                        more.length);
                                            }
                                            completion.data(
                                                    captured.toByteArray());
                                        } catch (IOException failure) {
                                            /*
                                             * Preserve any already consumed prefix as progress.
                                             * If cancellation has already won, the Process-stream
                                             * binding will put that prefix back in its semantic
                                             * unread buffer instead of committing it.
                                             */
                                            if (captured.size() > 0) {
                                                completion.data(
                                                        captured.toByteArray());
                                            } else {
                                                completion.failed();
                                            }
                                        }
                                    });
            return worker::interrupt;
        };
    }

    private static ProtosProcessStandardStreamBinding.WritableBackend
            writableBackend(PrintStream stream) {
        return (bytes, completion) -> {
            try {
                /*
                 * Keep the portable write commitment synchronous with this call. PrintStream
                 * write(byte[],off,len) does not imply an explicit Protos flush operation.
                 */
                stream.write(bytes, 0, bytes.length);
                completion.succeeded();
            } catch (RuntimeException failure) {
                completion.failed(0);
            }
            return () -> {};
        };
    }

    private int eval(String src, Session s, PrintStream out, PrintStream err) {
        try {
            out.println(
                    renderer.render(
                            s.compiler.compile(src).call(s.activation)));
            return 0;
        } catch (ParseError e) {
            err.println("Syntax error: " + e.getMessage());
            return 1;
        } catch (ProtosSignalException e) {
            err.println("Error: " + renderer.render(e.error()));
            return 1;
        } catch (RuntimeException e) {
            err.println("Runtime error: " + e.getMessage());
            return 1;
        }
    }

    private static Path core() throws IOException {
        String home = System.getenv("PROTOS_HOME");
        Path base =
                home == null || home.isBlank()
                        ? Path.of("").toAbsolutePath()
                        : Path.of(home);
        Path core = base.resolve("protos/lib/core");
        if (!Files.isDirectory(core)) {
            throw new IOException(
                    "cannot locate protos/lib/core; run via bin/protos or set PROTOS_HOME");
        }
        return core;
    }

    private static int usage(PrintStream err, String message) {
        err.println("protos: " + message + "\nTry 'protos --help'.");
        return 2;
    }

    private static void help(PrintStream out) {
        out.println(
                "Usage:\n"
                        + "  protos <file> [args...]\n"
                        + "  protos -e <source> [args...]\n"
                        + "  protos\n\n"
                        + "Options:\n"
                        + "  -e <source> [args...]\n"
                        + "  -h, --help\n"
                        + "  -v, --version\n\n"
                        + "Application arguments are available through process.args(); "
                        + "the file/source launcher identity is excluded.\n"
                        + "The CLI provisions stdin/stdout/stderr as byte streams with "
                        + "UTF-8 host-selected Encoding associations.\n"
                        + "Interactive REPL: arrow-key editing/history; Ctrl-D exits.");
    }

    static final class ReplHistory extends DefaultHistory {
        @Override
        public void add(Instant time, String input) {
            for (String line : input.split("\\R", -1)) {
                if (!line.isBlank()) super.add(time, line);
            }
        }
    }

    private enum ReplInputResult {
        COMPLETE,
        INCOMPLETE,
        EXIT
    }

    private record Session(
            ProtosSourceCompiler compiler,
            ProtosActivation activation,
            ProtosProcessRuntime process) {
        void terminate() {
            process.requestTerminationForRuntime();
        }
    }
}
