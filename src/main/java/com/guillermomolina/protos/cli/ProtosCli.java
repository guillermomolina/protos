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
import com.guillermomolina.protos.lsp.ProtosLanguageServerMain;
import com.guillermomolina.protos.parser.ParseError;
import com.guillermomolina.protos.runtime.*;
import com.oracle.truffle.api.source.Source;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jline.reader.*;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.*;

public final class ProtosCli {
    private static final Set<String> PACKAGE_METADATA_FILES =
            Set.of("protos.toml", "protos.lock");
    private static final Set<String> PACKAGE_METADATA_STAGING_FILES =
            Set.of(".protos.toml.stage", ".protos.lock.stage");
    private static final Set<String> PACKAGE_METADATA_MUTABLE_FILES =
            Set.of("protos.toml", "protos.lock", ".protos.toml.stage", ".protos.lock.stage");

    private final ProtosValueRenderer renderer = new ProtosValueRenderer();
    private final ProtosDiagnosticInspector diagnosticInspector = new ProtosDiagnosticInspector();

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
            if (args[0].equals("language-server")) {
                if (args.length != 1) {
                    return usage(err, "language-server accepts no arguments");
                }
                ProtosLanguageServerMain.run(in, out);
                return 0;
            }
            if (args[0].equals("debug")) {
                if (args.length < 2) {
                    return usage(err, "debug requires a source file");
                }
                return debugFile(
                        args[1],
                        applicationArguments(args, 2),
                        in,
                        out,
                        err);
            }
            if (args[0].equals("run")) {
                if (args.length < 2) {
                    return usage(err, "run requires a root-package logical entry");
                }
                return runWorkspaceApplication(
                        Path.of("").toAbsolutePath().normalize(),
                        args[1],
                        applicationArguments(args, 2),
                        in,
                        out,
                        err);
            }
            if (args[0].equals("package")) {
                return runBundledPackageTool(args, in, out, err);
            }
            if (args[0].equals("test")) {
                return runBundledTestTool(args, in, out, err);
            }
            if (args[0].equals("-e")) {
                if (args.length < 2) return usage(err, "-e requires a source argument");
                return evalOneShot(
                        sourceFromCharacters(args[1], "<eval>"),
                        applicationArguments(args, 2),
                        in,
                        out,
                        err);
            }
            if (args[0].startsWith("-")) {
                return usage(err, "unknown option: " + args[0]);
            }

            Path sourcePath = Path.of(args[0]).toAbsolutePath().normalize();
            String src;
            try {
                src = Files.readString(sourcePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                err.println(
                        "protos: cannot read "
                                + args[0]
                                + ": "
                                + e.getMessage());
                return 1;
            }
            return evalFileOneShot(
                    sourcePath,
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

    private int debugFile(
            String sourceArgument,
            List<String> applicationArguments,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        Path sourcePath = Path.of(sourceArgument).toAbsolutePath().normalize();
        String sourceText;
        try {
            sourceText = Files.readString(sourcePath, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            err.println(
                    "protos debug: cannot read "
                            + sourceArgument
                            + ": "
                            + failure.getMessage());
            return 1;
        }

        try (Session session =
                debugSession(applicationArguments, in, out, err)) {
            return evalFile(
                    sourcePath,
                    sourceText,
                    session,
                    err);
        } catch (IOException | RuntimeException failure) {
            err.println("protos debug: " + failure.getMessage());
            return 1;
        }
    }

    private Session debugSession(
            List<String> applicationArguments,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        Path core = core();
        Session session =
                createDebugSession(
                        core,
                        new ProtosStandardLibraryModuleResolver(core.getParent()),
                        applicationArguments,
                        in,
                        out,
                        err);
        ProtosCliPrintFacility.install(
                session.activation(), session.process(), renderer);
        return session;
    }

    int runWorkspaceApplication(
            Path projectRoot,
            String entryLogicalModule,
            List<String> applicationArguments,
            InputStream in,
            PrintStream out,
            PrintStream err) {
        try {
            Path core = core();
            Path distributionRoot = core.getParent().getParent();
            ProtosExecutionOutcome outcome =
                    ProtosWorkspaceRunDriver.execute(
                            new ProtosWorkspaceRunDriver.Request(
                                    core,
                                    distributionRoot.resolve("tools").resolve("package"),
                                    projectRoot,
                                    new ProtosStandardLibraryModuleResolver(core.getParent()),
                                    entryLogicalModule,
                                    applicationArguments,
                                    HOST_ENVIRONMENT_NAME_DOMAIN,
                                    hostEnvironmentEntries(),
                                    readableBackend(in),
                                    writableBackend(out),
                                    writableBackend(err),
                                    "UTF8",
                                    "UTF8",
                                    "UTF8"));

            return switch (outcome.state()) {
                case COMPLETED -> 0;
                case FAILED -> {
                    err.println("Error: " + diagnosticInspector.render(outcome.error()));
                    yield 1;
                }
                case CANCELLED -> {
                    err.println(
                            "Runtime error: workspace application root task was cancelled "
                                    + "before entry completion");
                    yield 1;
                }
            };
        } catch (IOException failure) {
            err.println("protos run: " + failure.getMessage());
            return 1;
        }
    }

    private int runBundledTestTool(
            String[] args,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws Exception {
        Path invocationWorkingDirectory =
                Path.of("").toAbsolutePath().normalize();
        Path core = core();
        Path distributionRoot = core.getParent().getParent();
        Path testsRoot = distributionRoot.resolve("tests");
        Path conformanceRoot = testsRoot.resolve("conformance");
        Path packageTomlRoot =
                testsRoot.resolve("package-tool").resolve("toml-syntax");
        Path actorRoot = conformanceRoot.resolve("actor");
        Path actorModulesRoot = actorRoot.resolve("modules");
        Path groupRoot = conformanceRoot.resolve("group");
        Path groupModulesRoot = groupRoot.resolve("modules");
        Path packageToolRoot = distributionRoot.resolve("tools").resolve("package");
        ProtosModuleResolver standardLibraryResolver =
                new ProtosStandardLibraryModuleResolver(core.getParent());
        ProtosPrelude packagePrelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                core,
                                new ProtosBundledToolModuleResolver(
                                        "package",
                                        packageToolRoot, (packageToolRoot).resolveSibling("shared"),
                                        standardLibraryResolver));
        ProtosPrelude actorPrelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                core,
                                new ProtosExactModuleOverlayResolver(
                                        Map.of(
                                                "workers",
                                                new ProtosExactModuleOverlayResolver.ExactModule(
                                                        new ProtosModuleKey("tool002-actor:workers"),
                                                        actorModulesRoot.resolve("workers.protos"))),
                                        standardLibraryResolver));
        ProtosPrelude groupPrelude =
                new ProtosCoreBootstrap()
                        .bootstrap(
                                core,
                                new ProtosExactModuleOverlayResolver(
                                        Map.of(
                                                "workers",
                                                new ProtosExactModuleOverlayResolver.ExactModule(
                                                        new ProtosModuleKey("tool002-group:workers"),
                                                        groupModulesRoot.resolve("workers.protos"))),
                                        standardLibraryResolver));
        try (ProtosNioReadOnlyTreeFilesystemBackend filesystemBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(conformanceRoot);
                ProtosNioReadOnlyTreeFilesystemBackend packageTomlFilesystemBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(packageTomlRoot);
                ProtosNioReadOnlyTreeFilesystemBackend actorFilesystemBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(actorRoot);
                ProtosNioReadOnlyTreeFilesystemBackend groupFilesystemBackend =
                        new ProtosNioReadOnlyTreeFilesystemBackend(groupRoot)) {
            return runBundledTool(
                    "test",
                    "Test",
                    args,
                    in,
                    out,
                    err,
                    session -> {
                        ProtosTestToolAsyncExecutionScope executionScope =
                                ProtosTestToolAsyncExecutionScope.install(
                                        session.activation,
                                        session.runtimeHost,
                                        actorPrelude,
                                        groupPrelude,
                                        packagePrelude);
                        boolean provisioned = false;
                        try {
                            installBundledToolFilesystem(
                                    session, "filesystem", filesystemBackend);
                            installBundledToolFilesystem(
                                    session, "actorFilesystem", actorFilesystemBackend);
                            installBundledToolFilesystem(
                                    session, "groupFilesystem", groupFilesystemBackend);
                            installBundledToolFilesystem(
                                    session,
                                    "packageTomlFilesystem",
                                    packageTomlFilesystemBackend);
                            ProtosTestToolCatalogAcquisitionFacility.install(
                                    session.activation,
                                    invocationWorkingDirectory);
                            provisioned = true;
                            return executionScope::close;
                        } finally {
                            if (!provisioned) {
                                executionScope.close();
                            }
                        }
                    });
        }
    }

    private int runBundledPackageTool(
            String[] args,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws Exception {
        String entryModuleName =
                args.length == 2 && args[1].equals("manifest")
                        ? "ManifestMain"
                        : "Main";
        try (ProtosNioConfinedFilesystemBackend filesystemBackend =
                new ProtosNioConfinedFilesystemBackend(
                        Path.of("").toAbsolutePath().normalize(),
                        PACKAGE_METADATA_FILES,
                        PACKAGE_METADATA_STAGING_FILES,
                        PACKAGE_METADATA_MUTABLE_FILES)) {
            return runBundledTool(
                    "package",
                    "Package",
                    entryModuleName,
                    args,
                    in,
                    out,
                    err,
                    session -> {
                        installBundledToolFilesystem(
                                session, "filesystem", filesystemBackend);
                        return NOOP_BUNDLED_TOOL_CLEANUP;
                    });
        }
    }

    private int runBundledTool(
            String toolName,
            String diagnosticName,
            String[] args,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws Exception {
        return runBundledTool(
                toolName, diagnosticName, args, in, out, err, session -> NOOP_BUNDLED_TOOL_CLEANUP);
    }

    private int runBundledTool(
            String toolName,
            String diagnosticName,
            String[] args,
            InputStream in,
            PrintStream out,
            PrintStream err,
            BundledToolSessionProvisioner provisioner)
            throws Exception {
        return runBundledTool(
                toolName,
                diagnosticName,
                "Main",
                args,
                in,
                out,
                err,
                provisioner);
    }

    private int runBundledTool(
            String toolName,
            String diagnosticName,
            String entryModuleName,
            String[] args,
            InputStream in,
            PrintStream out,
            PrintStream err,
            BundledToolSessionProvisioner provisioner)
            throws Exception {
        Path core = core();
        Path toolRoot = core.getParent().getParent().resolve("tools").resolve(toolName);
        ProtosBundledToolModuleResolver resolver =
                new ProtosBundledToolModuleResolver(
                        toolName,
                        toolRoot,
                        toolRoot.resolveSibling("shared"),
                        new ProtosStandardLibraryModuleResolver(core.getParent()));
        Session session =
                session(
                        core,
                        resolver,
                        applicationArguments(args, 0),
                        in,
                        out,
                        err);
        BundledToolSessionCleanup cleanup = NOOP_BUNDLED_TOOL_CLEANUP;
        try {
            cleanup =
                    Objects.requireNonNull(
                            provisioner.provision(session),
                            "bundled tool provisioner returned null cleanup");
            ProtosModuleKey entryModule = resolver.entryModule(entryModuleName);
            ProtosModuleSource source = resolver.loadSource(entryModule).requireKey(entryModule);
            executeStandaloneRootTask(session.executeModuleSource(source));
            return 0;
        } catch (ParseError e) {
            err.println(diagnosticName + " tool syntax error: " + e.getMessage());
            return 1;
        } catch (ProtosSignalException e) {
            err.println(diagnosticName + " tool error: " + diagnosticInspector.render(e.error()));
            return 1;
        } catch (RuntimeException e) {
            err.println(diagnosticName + " tool runtime error: " + e.getMessage());
            return 1;
        } finally {
            try {
                cleanup.close();
            } finally {
                session.terminate();
            }
        }
    }

    private static void installBundledToolFilesystem(
            Session session,
            String slotName,
            ProtosStandardFilesystemProtocol.Backend filesystemBackend) {
        ProtosObjectValue rawFilesystem =
                ProtosStandardFilesystemProtocol.createCapability(
                        session.activation
                                .prelude()
                                .orElseThrow()
                                .bytesPrototypeForRuntime(),
                        session.activation,
                        filesystemBackend);
        if (!(rawFilesystem instanceof ProtosFilesystemValue filesystem)) {
            throw new IllegalStateException(
                    "standard Filesystem bridge returned the wrong value family");
        }
        if (session.activation.context().hasLocalSlot(slotName)) {
            throw new IllegalStateException(
                    "bundled tool Filesystem bootstrap slot already exists: " + slotName);
        }
        session.activation.context().createLocalSlot(slotName, filesystem);
    }

    @FunctionalInterface
    private interface BundledToolSessionCleanup {
        void close();
    }

    private static final BundledToolSessionCleanup NOOP_BUNDLED_TOOL_CLEANUP = () -> {};

    @FunctionalInterface
    private interface BundledToolSessionProvisioner {
        BundledToolSessionCleanup provision(Session session);
    }

    private static List<String> applicationArguments(String[] args, int start) {
        return List.copyOf(
                Arrays.asList(Arrays.copyOfRange(args, start, args.length)));
    }

    private int evalOneShot(
            Source source,
            List<String> applicationArguments,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        try (Session session = session(applicationArguments, in, out, err)) {
            return eval(source, session, err);
        }
    }

    private int evalFileOneShot(
            Path sourcePath,
            String characters,
            List<String> applicationArguments,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        try (Session session = session(applicationArguments, in, out, err)) {
            return evalFile(sourcePath, characters, session, err);
        }
    }

    private int repl(InputStream in, PrintStream out, PrintStream err) throws IOException {
        try (Session s = session(List.of(), in, out, err)) {
            out.println("Protos REPL\nType :help for help, :quit to exit.");
            return in == System.in
                    ? interactiveRepl(s, out, err)
                    : streamRepl(in, s, out, err);
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
                    diagnosticInspector.render(
                            s.evaluatePersistent(sourceFromCharacters(input, "<repl>"))));
            return ReplInputResult.COMPLETE;
        } catch (ParseError e) {
            if (e.isUnexpectedEndOfSource()) {
                return ReplInputResult.INCOMPLETE;
            }
            err.println("Syntax error: " + e.getMessage());
            return ReplInputResult.COMPLETE;
        } catch (ProtosSignalException e) {
            err.println("Error: " + diagnosticInspector.render(e.error()));
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
        Session session =
                session(
                        core,
                        new ProtosStandardLibraryModuleResolver(core.getParent()),
                        applicationArguments,
                        in,
                        out,
                        err);
        ProtosCliPrintFacility.install(session.activation(), session.process(), renderer);
        return session;
    }

    private Session session(
            Path core,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        return createSession(
                core,
                moduleResolver,
                applicationArguments,
                in,
                out,
                err);
    }



    private Session createSession(
            Path core,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        ProtosStandaloneProcessBootstrap.Result bootstrap =
                bootstrapStandaloneProcess(
                        core,
                        moduleResolver,
                        applicationArguments,
                        readableBackend(in),
                        writableBackend(out),
                        writableBackend(err));
        return bindStandaloneProcess(
                bootstrap,
                ProtosPolyglotRuntimeHost.open(),
                in,
                out,
                err);
    }

    private Session createDebugSession(
            Path core,
            ProtosModuleResolver moduleResolver,
            List<String> applicationArguments,
            InputStream in,
            PrintStream controlOut,
            PrintStream diagnostics)
            throws IOException {
        ProtosStandaloneProcessBootstrap.Result bootstrap =
                bootstrapStandaloneProcess(
                        core,
                        moduleResolver,
                        applicationArguments,
                        readableBackend(in),
                        ProtosPolyglotStandardStreamRouting.stdoutBackend(),
                        ProtosPolyglotStandardStreamRouting.stderrBackend());

        ProtosPolyglotRuntimeHost runtimeHost =
                ProtosPolyglotRuntimeHost.openDebug(diagnostics);
        boolean handedToBinding = false;
        try {
            publishDebugReadiness(controlOut, runtimeHost.debugEndpoint());
            handedToBinding = true;
            return bindStandaloneProcess(
                    bootstrap,
                    runtimeHost,
                    in,
                    OutputStream.nullOutputStream(),
                    OutputStream.nullOutputStream());
        } finally {
            if (!handedToBinding) {
                bootstrap.process().requestTerminationForRuntime();
                runtimeHost.close();
            }
        }
    }

    private static ProtosStandaloneProcessBootstrap.Result
            bootstrapStandaloneProcess(
                    Path core,
                    ProtosModuleResolver moduleResolver,
                    List<String> applicationArguments,
                    ProtosProcessStandardStreamBinding.ReadableBackend stdinBackend,
                    ProtosProcessStandardStreamBinding.WritableBackend stdoutBackend,
                    ProtosProcessStandardStreamBinding.WritableBackend stderrBackend)
                    throws IOException {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(core, moduleResolver);
        ProtosEncodingValue utf8 = utf8(prelude);
        return ProtosStandaloneProcessBootstrap.create(
                prelude,
                applicationArguments,
                HOST_ENVIRONMENT_NAME_DOMAIN,
                hostEnvironmentEntries(),
                stdinBackend,
                stdoutBackend,
                stderrBackend,
                utf8,
                utf8,
                utf8,
                null);
    }

    private static Session bindStandaloneProcess(
            ProtosStandaloneProcessBootstrap.Result bootstrap,
            ProtosPolyglotRuntimeHost runtimeHost,
            InputStream in,
            OutputStream out,
            OutputStream err) {
        boolean bound = false;
        try {
            ProtosPolyglotProcessContext processContext =
                    runtimeHost.hostProcess(bootstrap.process(), in, out, err);
            bound = true;
            return new Session(
                    bootstrap.activation(),
                    bootstrap.process(),
                    runtimeHost,
                    processContext);
        } finally {
            if (!bound) {
                bootstrap.process().requestTerminationForRuntime();
                runtimeHost.close();
            }
        }
    }

    private static void publishDebugReadiness(
            PrintStream out,
            ProtosPolyglotRuntimeHost.DebugEndpoint endpoint)
            throws IOException {
        String record =
                "PROTOS_DEBUG_READY "
                        + "{\"version\":1,"
                        + "\"protocol\":\"dap\","
                        + "\"transport\":\"tcp\","
                        + "\"host\":\""
                        + endpoint.host()
                        + "\","
                        + "\"port\":"
                        + endpoint.port()
                        + "}";
        out.println(record);
        out.flush();
        if (out.checkError()) {
            throw new IOException(
                    "cannot publish debugger readiness on stdout");
        }
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

    private int eval(Source source, Session s, PrintStream err) {
        try {
            executeStandaloneRootTask(s.execute(source));
            return 0;
        } catch (ParseError e) {
            err.println("Syntax error: " + e.getMessage());
            return 1;
        } catch (ProtosSignalException e) {
            err.println("Error: " + diagnosticInspector.render(e.error()));
            return 1;
        } catch (RuntimeException e) {
            err.println("Runtime error: " + e.getMessage());
            return 1;
        }
    }

    private int evalFile(
            Path sourcePath,
            String characters,
            Session s,
            PrintStream err) {
        try {
            executeStandaloneRootTask(s.executeFile(sourcePath, characters));
            return 0;
        } catch (ParseError e) {
            err.println("Syntax error: " + e.getMessage());
            return 1;
        } catch (ProtosSignalException e) {
            err.println("Error: " + diagnosticInspector.render(e.error()));
            return 1;
        } catch (RuntimeException e) {
            err.println("Runtime error: " + e.getMessage());
            return 1;
        }
    }

    /*
     * CLI policy translates the shared mechanical terminal outcome into the historical
     * standalone command behavior. The cooperative RootActor task execution itself is owned by
     * ProtosRootTaskExecution so test/tool consumers do not need a CLI-specific executor.
     */


    private static Object executeStandaloneRootTask(ProtosExecutionOutcome outcome) {
        return switch (outcome.state()) {
            case COMPLETED -> outcome.value();
            case FAILED -> throw new ProtosSignalException(outcome.error());
            case CANCELLED ->
                    throw new IllegalStateException(
                            "standalone root task was cancelled before entry completion");
        };
    }

    private static Source sourceFromCharacters(String characters, String name) {
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(name, "name");
        return Source.newBuilder(ProtosLanguage.ID, characters, name)
                .mimeType(ProtosLanguage.MIME_TYPE)
                .build();
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
                        + "  protos debug <file> [args...]\n"
                        + "  protos language-server\n"
                        + "  protos run <entry> [args...]\n"
                        + "  protos package [args...]\n"
                        + "  protos test [--jobs N] [args...]\n"
                        + "  protos\n\n"
                        + "Options:\n"
                        + "  -e <source> [args...]\n"
                        + "  -h, --help\n"
                        + "  -v, --version\n\n"
                        + "Workspace run executes the explicit root-package logical <entry> "
                        + "from the current directory; neither 'run' nor <entry> is included "
                        + "in process.args().\n"
                        + "Debug executes one explicit file through the standard DAP debugger; "
                        + "its one PROTOS_DEBUG_READY JSON record is emitted on stdout before "
                        + "guest execution and guest output then travels through DAP.\n"
                        + "Language-server starts the toolchain-matched static service using "
                        + "standard LSP over stdin/stdout; stdout is protocol-only while active.\n"
                        + "Test Tool --jobs N selects positive logical execution capacity; "
                        + "without --jobs the Test Tool uses jobs = 1.\n"
                        + "Application arguments are available through process.args(); "
                        + "the file/source launcher identity is excluded.\n"
                        + "The CLI provisions stdin/stdout/stderr as byte streams with "
                        + "UTF-8 host-selected Encoding associations.\n"
                        + "File and -e execution write only explicit program output; "
                        + "the interactive REPL also displays evaluation results.\n"
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
            ProtosActivation activation,
            ProtosProcessRuntime process,
            ProtosPolyglotRuntimeHost runtimeHost,
            ProtosPolyglotProcessContext processContext) implements AutoCloseable {
        ProtosExecutionOutcome execute(Source source) {
            if (processContext == null) {
                throw new IllegalStateException(
                        "session is not bound to a Polyglot Process Context");
            }
            return processContext.execute(
                    Objects.requireNonNull(source, "source"), activation);
        }

        ProtosExecutionOutcome executeFile(Path path, CharSequence characters) {
            if (processContext == null) {
                throw new IllegalStateException(
                        "session is not bound to a Polyglot Process Context");
            }
            return processContext.executeFile(
                    Objects.requireNonNull(path, "path"),
                    Objects.requireNonNull(characters, "characters"),
                    activation);
        }

        ProtosExecutionOutcome executeModuleSource(ProtosModuleSource source) {
            if (processContext == null) {
                throw new IllegalStateException(
                        "session is not bound to a Polyglot Process Context");
            }
            return processContext.executeModuleSource(
                    Objects.requireNonNull(source, "source"), activation);
        }

        Object evaluatePersistent(Source source) {
            if (processContext == null) {
                throw new IllegalStateException(
                        "session is not bound to a Polyglot Process Context");
            }
            return processContext.evaluatePersistent(
                    Objects.requireNonNull(source, "source"), activation);
        }

        void terminate() {
            process.requestTerminationForRuntime();
            if (runtimeHost != null) {
                runtimeHost.close();
            }
        }

        @Override
        public void close() {
            terminate();
        }
    }
}
