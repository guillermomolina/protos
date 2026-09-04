/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.execution.*;
import com.guillermomolina.protos.parser.ParseError;
import com.guillermomolina.protos.runtime.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.jline.reader.*;
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
                    .option(LineReader.Option.BRACKETED_PASTE, true).build();
            for (;;) {
                try {
                    String input = reader.readLine("protos> ");
                    if (processReturnedInput(input, s, out, err)) return 0;
                } catch (UserInterruptException e) {
                    // Ctrl-C cancels the current line and returns to a clean prompt.
                } catch (EndOfFileException e) {
                    out.println(); return 0;
                }
            }
        }
    }

    private int streamRepl(InputStream in, Session s, PrintStream out, PrintStream err) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        for (;;) {
            out.print("protos> "); out.flush();
            String line = reader.readLine();
            if (line == null) { out.println(); return 0; }
            processLine(line, s, out, err);
            if (isExit(line)) return 0;
        }
    }

    private boolean processReturnedInput(String input, Session s, PrintStream out, PrintStream err) {
        String[] lines = input.split("\\R", -1);
        for (String line : lines) {
            processLine(line, s, out, err);
            if (isExit(line)) return true;
        }
        return false;
    }

    private void processLine(String line, Session s, PrintStream out, PrintStream err) {
        if (line.isBlank() || isExit(line)) return;
        if (line.equals(":help")) {
            out.println(":help  show help\n:quit  exit\n:exit  exit\n"
                    + "Arrow keys edit lines and navigate session history.\n"
                    + "Ctrl-A/Ctrl-E move to start/end; Ctrl-C cancels; Ctrl-D exits.\n"
                    + "Top-level context persists between evaluations.");
            return;
        }
        eval(line, s, out, err);
    }

    private static boolean isExit(String line) { return line.equals(":quit") || line.equals(":exit"); }

    private Session session() throws IOException {
        Path c = core();
        ProtosPrelude p = new ProtosCoreBootstrap().bootstrap(c);
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
    private record Session(ProtosSourceCompiler compiler, ProtosActivation activation) {}
}
