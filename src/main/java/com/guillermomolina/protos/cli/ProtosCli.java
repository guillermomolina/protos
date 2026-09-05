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
            if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) { help(out); return 0; }
            if (args.length == 1 && (args[0].equals("--version") || args[0].equals("-v"))) {
                String v = getClass().getPackage().getImplementationVersion();
                out.println("Protos " + (v == null ? "development" : v)); return 0;
            }
            if (args[0].equals("-e")) {
                if (args.length != 2) return usage(err, "-e requires exactly one source argument");
                return eval(args[1], session(), out, err);
            }
            if (args[0].startsWith("-")) return usage(err, "unknown option: " + args[0]);
            if (args.length != 1) return usage(err, "program arguments are not available yet: Core args is closure-invocation-only");
            String src;
            try { src = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8); }
            catch (IOException e) { err.println("protos: cannot read " + args[0] + ": " + e.getMessage()); return 1; }
            return eval(src, session(), out, err);
        } catch (Throwable e) {
            err.println("Internal error: " + e); e.printStackTrace(err); return 70;
        }
    }

    private int repl(InputStream in, PrintStream out, PrintStream err) throws IOException {
        Session s = session();
        out.println("Protos REPL\nType :help for help, :quit to exit.");
        return in == System.in ? interactiveRepl(s, out, err) : streamRepl(in, s, out, err);
    }

    private int interactiveRepl(Session s, PrintStream out, PrintStream err) throws IOException {
        try (Terminal terminal = TerminalBuilder.builder().system(true).streams(System.in, System.out).build()) {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal)
                    .history(new ReplHistory())
                    .option(LineReader.Option.BRACKETED_PASTE, true).build();
            StringBuilder pending = new StringBuilder();
            for (;;) {
                try {
                    String input = reader.readLine(pending.isEmpty() ? "protos> " : "...> ");
                    ReplInputResult result = processInputChunk(input, pending, s, out, err);
                    if (result == ReplInputResult.EXIT) return 0;
                } catch (UserInterruptException e) {
                    // Ctrl-C abandons the whole incomplete syntactic unit.
                    pending.setLength(0);
                } catch (EndOfFileException e) {
                    out.println(); return 0;
                }
            }
        }
    }

    private int streamRepl(InputStream in, Session s, PrintStream out, PrintStream err) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder pending = new StringBuilder();
        for (;;) {
            out.print(pending.isEmpty() ? "protos> " : "...> "); out.flush();
            String line = reader.readLine();
            if (line == null) { out.println(); return 0; }
            ReplInputResult result = processInputChunk(line, pending, s, out, err);
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
            return pending.isEmpty() ? ReplInputResult.COMPLETE : ReplInputResult.INCOMPLETE;
        }

        String source = pending.isEmpty() ? input : pending + "\n" + input;
        ReplInputResult result = processReturnedInput(source, s, out, err);
        pending.setLength(0);
        if (result == ReplInputResult.INCOMPLETE) pending.append(source);
        return result;
    }

    private ReplInputResult processReturnedInput(String input, Session s, PrintStream out, PrintStream err) {
        if (input.isBlank()) return ReplInputResult.COMPLETE;
        if (isExit(input)) return ReplInputResult.EXIT;
        if (input.equals(":help")) {
            out.println(":help  show help\n:quit  exit\n:exit  exit\n"
                    + "Arrow keys edit lines and navigate session history.\n"
                    + "Ctrl-A/Ctrl-E move to start/end; Ctrl-C cancels; Ctrl-D exits.\n"
                    + "Top-level context persists between evaluations.");
            return ReplInputResult.COMPLETE;
        }
        try {
            out.println(renderer.render(s.compiler.compile(input).call(s.activation)));
            return ReplInputResult.COMPLETE;
        } catch (ParseError e) {
            if (e.isUnexpectedEndOfSource()) return ReplInputResult.INCOMPLETE;
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

    private static boolean isExit(String line) { return line.equals(":quit") || line.equals(":exit"); }

    private Session session() throws IOException {
        Path c = core();
        ProtosModuleResolver moduleResolver =
                new ProtosStandardLibraryModuleResolver(c.getParent());
        ProtosPrelude p = new ProtosCoreBootstrap().bootstrap(c, moduleResolver);
        return new Session(new ProtosSourceCompiler(), p.newModuleActivation());
    }

    private int eval(String src, Session s, PrintStream out, PrintStream err) {
        try { out.println(renderer.render(s.compiler.compile(src).call(s.activation))); return 0; }
        catch (ParseError e) { err.println("Syntax error: " + e.getMessage()); return 1; }
        catch (ProtosSignalException e) { err.println("Error: " + renderer.render(e.error())); return 1; }
        catch (RuntimeException e) { err.println("Runtime error: " + e.getMessage()); return 1; }
    }

    private static Path core() throws IOException {
        String h = System.getenv("PROTOS_HOME");
        Path b = h == null || h.isBlank() ? Path.of("").toAbsolutePath() : Path.of(h);
        Path c = b.resolve("protos/lib/core");
        if (!Files.isDirectory(c)) throw new IOException("cannot locate protos/lib/core; run via bin/protos or set PROTOS_HOME");
        return c;
    }

    private static int usage(PrintStream e, String m) { e.println("protos: " + m + "\nTry 'protos --help'."); return 2; }
    private static void help(PrintStream o) {
        o.println("Usage:\n  protos [file]\n  protos -e <source>\n  protos\n\nOptions:\n"
                + "  -e <source>\n  -h, --help\n  -v, --version\n\n"
                + "Interactive REPL: arrow-key editing/history; Ctrl-D exits.\n"
                + "Top-level program args are not exposed: Core args is Closure-invocation-only.");
    }

    static final class ReplHistory extends DefaultHistory {
        @Override
        public void add(Instant time, String input) {
            for (String line : input.split("\\R", -1)) {
                if (!line.isBlank()) super.add(time, line);
            }
        }
    }

    private enum ReplInputResult { COMPLETE, INCOMPLETE, EXIT }

    private record Session(ProtosSourceCompiler compiler, ProtosActivation activation) {}
}
