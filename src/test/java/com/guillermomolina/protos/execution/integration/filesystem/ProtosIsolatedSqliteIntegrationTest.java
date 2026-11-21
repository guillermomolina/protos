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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

/** Public installed-distribution acceptance: all SQL and assertions execute in Protos. */
final class ProtosIsolatedSqliteIntegrationTest {
    @TempDir Path work;
    private Path distribution;
    private Path databaseDirectory;
    private Path identityDirectory;
    private int serial;
    // Diagnostic progress only: test and process boundaries, never SQL or values.
    private String testName = "?";
    private long testStartNanos;
    private final List<Process> startedChildren = new ArrayList<>();

    private static void progress(String line) {
        System.err.println(line);
        System.err.flush();
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String category(List<String> args) {
        String program = Path.of(args.getFirst()).getFileName().toString();
        if (program.equals("sandbox-exec")) return "launcher";
        if (program.equals("protos")) return "CLI";
        if (program.equals("java")) return "helper-probe";
        return "other";
    }

    private void childStarted(Process process, String category) {
        synchronized (startedChildren) { startedChildren.add(process); }
        progress("PLAT074_CHILD_START test=" + testName + " pid=" + process.pid() + " category=" + category
                + " atMs=" + elapsedMillis(testStartNanos));
    }

    private void childEnded(Process process, long startNanos) {
        progress("PLAT074_CHILD_END test=" + testName + " pid=" + process.pid()
                + " exit=" + (process.isAlive() ? "running" : String.valueOf(process.exitValue()))
                + " durationMs=" + elapsedMillis(startNanos)
                + " descendants=" + process.descendants().filter(ProcessHandle::isAlive).count());
    }

    /** Bounded tail of a child log: at most the last 16 KiB and 40 lines. */
    private static List<String> tail(Path log) {
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(log.toFile(), "r")) {
            long length = file.length();
            long start = Math.max(0, length - 16384);
            byte[] bytes = new byte[(int) (length - start)];
            file.seek(start);
            file.readFully(bytes);
            List<String> lines = List.of(new String(bytes, StandardCharsets.UTF_8).split("\n"));
            return lines.subList(Math.max(0, lines.size() - 40), lines.size());
        } catch (java.io.IOException failure) {
            return List.of("[log unavailable: " + failure.getClass().getSimpleName() + "]");
        }
    }

    private void childTimedOut(Process process, long startNanos, Path log) {
        StringBuilder report = new StringBuilder("PLAT074_CHILD_TIMEOUT test=").append(testName)
                .append(" pid=").append(process.pid()).append(" durationMs=").append(elapsedMillis(startNanos))
                .append(" alive=").append(process.isAlive())
                .append(" cpu=").append(process.info().totalCpuDuration().map(Object::toString).orElse("n/a"));
        process.descendants().forEach(child -> report.append("\n  descendant pid=").append(child.pid())
                .append(" alive=").append(child.isAlive())
                .append(" cpu=").append(child.info().totalCpuDuration().map(Object::toString).orElse("n/a")));
        List<String> lines = tail(log);
        String stage = "none";
        for (String line : lines) {
            if (line.startsWith("PLAT074_STAGE ") || line.startsWith("SQLITE_STAGE_TRAIL ")) stage = line;
        }
        report.append("\n  last guest stage: ").append(stage).append("\n  output tail:");
        for (String line : lines) report.append("\n    ").append(line);
        progress(report.toString());
    }

    @AfterEach
    void reportTestEnd() {
        long remaining;
        synchronized (startedChildren) {
            remaining = startedChildren.stream()
                    .mapToLong(child -> (child.isAlive() ? 1 : 0) + child.descendants().filter(ProcessHandle::isAlive).count())
                    .sum();
        }
        progress("PLAT074_TEST_END test=" + testName + " durationMs=" + elapsedMillis(testStartNanos)
                + " liveChildren=" + remaining);
    }

    @BeforeEach
    void installedDistribution(TestInfo info) throws Exception {
        testName = info.getTestMethod().map(java.lang.reflect.Method::getName).orElse(info.getDisplayName());
        testStartNanos = System.nanoTime();
        progress("PLAT074_TEST_START test=" + testName + " monotonicNanos=" + testStartNanos
                + " thread=" + Thread.currentThread().threadId());
        String selected = System.getProperty("protos.sqlite.distribution");
        assumeTrue(selected != null, "UNSUPPORTED: set -Dprotos.sqlite.distribution to an extracted portable distribution");
        assumeTrue(System.getProperty("os.name").equals("Linux"), "UNSUPPORTED: Linux is required");
        distribution = Path.of(selected).toRealPath();
        assertTrue(Files.isExecutable(distribution.resolve("bin/protos")), "installed launcher missing");
        assertTrue(Files.isExecutable(distribution.resolve("lib/sqlite/sandbox-exec")), "sandbox launcher missing");
        Result abi = command(List.of(distribution.resolve("lib/sqlite/sandbox-exec").toString(), "--abi-only"));
        if (abi.code() == 69) {
            assumeTrue(false, "UNSUPPORTED: " + abi.output());
        }
        assertEquals(0, abi.code(), abi.output());
        assertTrue(abi.output().contains("LANDLOCK_ABI"), abi.output());
        databaseDirectory = Files.createDirectory(work.resolve("databases"));
        Files.setPosixFilePermissions(databaseDirectory, PosixFilePermissions.fromString("rwx------"));
        identityDirectory = Files.createDirectory(work.resolve("databases.sqlite-identities"));
        Files.setPosixFilePermissions(identityDirectory, PosixFilePermissions.fromString("rwx------"));
        manifest("application.db", "101", "202", "CREATE_NEW");
    }

    private void manifest(String name, String hi, String lo, String mode) throws Exception {
        Path manifest = identityDirectory.resolve(name + ".identity");
        Files.writeString(manifest, "identity_hi=" + hi + "\nidentity_lo=" + lo + "\nprovision=" + mode + "\n");
        Files.setPosixFilePermissions(manifest, PosixFilePermissions.fromString("rw-------"));
    }

    private static final String PRELUDE = """
            Assertions: import("std:test/assertions")
            SQLite: import("std:sqlite")
            // Handler-style category test over the delegation chain. Error
            // prototypes define no `recognizes`; sending it signals SlotNotFound.
            isKind: (error, prototype) => {
                found: false
                current: error
                while(!found && (current !== Object)) {
                    found = current === prototype
                    current = current.parent()
                }
                found
            }
            expectFailure: (block) => {
                failed: false
                Error.handle(block, (error) => { failed = true })
                Assertions.require(failed)
            }
            // Harness-only stage trail: the last three stage names, never SQL
            // text or values. Printed only when a program fails (see success).
            sqliteStage: "start"
            sqliteStage1: "-"
            sqliteStage2: "-"
            stageMark: (name) => {
                sqliteStage2 = sqliteStage1
                sqliteStage1 = sqliteStage
                sqliteStage = name
            }
            stageTrail: () => sqliteStage2 + " > " + sqliteStage1 + " > " + sqliteStage
            """;
    private static final String DATABASE = """
            stageMark("openDurable:before")
            db: SQLite.openDurable(sqlite, "application.db")
            stageMark("openDurable:after")
            exec: (sql) => {
                stageMark("prepare:before")
                statement: db.prepare(sql)
                stageMark("prepare:after")
                (() => {
                    stageMark("step:before")
                    Assertions.require(!statement.step())
                    stageMark("step:after")
                }).ensure(() => {
                    stageMark("finalize:before")
                    statement.close().value()
                    stageMark("finalize:after")
                })
            }
            scalar: (sql) => {
                stageMark("prepare:before")
                statement: db.prepare(sql)
                stageMark("prepare:after")
                value: null
                (() => {
                    stageMark("step:before")
                    Assertions.require(statement.step())
                    value = statement.column(0)
                    Assertions.require(!statement.step())
                    stageMark("step:after")
                }).ensure(() => {
                    stageMark("finalize:before")
                    statement.close().value()
                    stageMark("finalize:after")
                })
                value
            }
            """;

    private Result program(String body, boolean granted, boolean memoryProvider) throws Exception {
        Path source = work.resolve("program-" + serial++ + ".protos");
        Files.writeString(source, PRELUDE + body);
        return runSource(source, granted, memoryProvider);
    }

    /** Runs one guest source file unchanged through the installed launcher. */
    private Result runSource(Path source, boolean granted, boolean memoryProvider) throws Exception {
        List<String> args = new ArrayList<>(List.of(distribution.resolve("bin/protos").toString()));
        if (granted) args.add("--allow-sqlite=" + databaseDirectory);
        if (memoryProvider) args.addAll(List.of("--foreign-provider-path",
                distribution.resolve("lib/sqlite/protos-ffm-provider.jar").toString()));
        args.add(source.toString());
        return command(args);
    }

    /**
     * Runs {@code body} against the durable database. The original guest Error is never
     * handled here: an ensure only reports the stage trail reached before it propagates.
     */
    private void success(String body) throws Exception {
        Result result = program("sqliteCompleted: false\n(() => {\n" + DATABASE
                + "(() => {\n" + body + "\n}).ensure(() => {\n"
                + "stageMark(\"close:before\")\ndb.close().value()\nstageMark(\"close:after\")\nprint(\"PLAT074_STAGE close:completed\")\n})\n"
                + "sqliteCompleted = true\n}).ensure(() => {\n"
                + "if(!sqliteCompleted) { print(\"SQLITE_STAGE_TRAIL \" + stageTrail()) }\n})\n", true, false);
        assertEquals(0, result.code(), "exit=" + result.code() + "\n" + result.output());
    }

    private record Result(int code, String output) {}

    private Result command(List<String> args) throws Exception {
        Path log = work.resolve("output-" + serial++ + ".txt");
        ProcessBuilder builder = new ProcessBuilder(args).directory(work.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("PROTOS_HOME");
        builder.environment().remove("CLASSPATH");
        long startNanos = System.nanoTime();
        Process process = builder.start();
        childStarted(process, category(args));
        process.getOutputStream().close();
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                childTimedOut(process, startNanos, log);
                org.junit.jupiter.api.Assertions.fail("FAIL: process timeout: " + args.getFirst()
                        + "\npartial output:\n" + Files.readString(log));
            }
            childEnded(process, startNanos);
            return new Result(process.exitValue(), Files.readString(log));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test void importGrantsNoDurableAuthority() throws Exception {
        Result result = program("expectFailure(() => SQLite.openDurable({}, \"application.db\"))", false, false);
        assertEquals(0, result.code(), result.output());
        assertFalse(Files.exists(databaseDirectory.resolve("application.db")));
    }

    @Test void memoryStillRequiresExplicitProvider() throws Exception {
        Result result = program("expectFailure(() => SQLite.openMemory())", false, false);
        assertEquals(0, result.code(), result.output());
    }

    @Test void memoryRetainsPublicExampleBehavior() throws Exception {
        // The public example declares its own imports; run the installed file verbatim, adding
        // only the explicit provider. Prepending PRELUDE would duplicate its declarations.
        Result result = runSource(distribution.resolve("protos/examples/ffi/sqlite-library.protos"), false, true);
        assertEquals(0, result.code(), "sqlite-library.protos: exit=" + result.code() + "\n" + result.output());
    }

    @Test void explicitGrantStartsRealHelper() throws Exception {
        success("Assertions.require(scalar(\"SELECT sqlite_version()\") !== null)");
        assertTrue(Files.size(databaseDirectory.resolve("application.db")) > 0);
        assertTrue(Files.readString(identityDirectory.resolve("application.db.identity")).endsWith("provision=VERIFY\n"));
    }

    @Test void fabricatedGrantIsRejectedEvenInsideAuthorizedProcess() throws Exception {
        Result result = program("expectFailure(() => SQLite.openDurable({}, \"application.db\"))", true, false);
        assertEquals(0, result.code(), result.output());
        assertFalse(Files.exists(databaseDirectory.resolve("application.db")));
    }

    @Test void invalidNamesAreRejectedBeforeLaunch() throws Exception {
        Result result = program("""
                for(name in Array("", ".hidden", "../outside.db", "/tmp/outside.db", "a/b", "bad name", "x-wal", "x-shm", "x-journal", "x.identity")) {
                    expectFailure(() => SQLite.openDurable(sqlite, name))
                }
                """, true, false);
        assertEquals(0, result.code(), result.output());
        assertFalse(Files.exists(databaseDirectory.resolve("application.db")));
    }

    @Test void preparedBindingsPreserveSignedI64Limits() throws Exception {
        success("""
                query: db.prepare("SELECT ?1, ?2")
                (() => {
                    query.bind(1, -9223372036854775808).bind(2, 9223372036854775807)
                    Assertions.require(query.step())
                    Assertions.require(query.column(0) == -9223372036854775808)
                    Assertions.require(query.column(1) == 9223372036854775807)
                }).ensure(() => query.close().value())
                """);
    }

    @Test void floatCodecCoversBinary64Domain() throws Exception {
        success("""
                smallest: 1.0
                n: 0
                while(n < 1074) { smallest = smallest / 2.0; n = n + 1 }
                largestPower: 1.0
                n = 0
                while(n < 1023) { largestPower = largestPower * 2.0; n = n + 1 }
                for(value in Array(0.0, -0.0, 1.0, -1.5, smallest, -smallest, largestPower, -largestPower,
                    1.7976931348623157e308, 1.0 / 0.0, -1.0 / 0.0)) {
                    query: db.prepare("SELECT ?1")
                    (() => {
                        query.bind(1, value)
                        Assertions.require(query.step())
                        Assertions.require(query.column(0) === value)
                    }).ensure(() => query.close().value())
                }
                // SQLite normalizes a NaN binding to SQL NULL.
                query: db.prepare("SELECT ?1")
                (() => {
                    query.bind(1, 0.0 / 0.0)
                    Assertions.require(query.step())
                    Assertions.require(query.column(0) === null)
                }).ensure(() => query.close().value())
                """);
    }

    @Test void textAndBlobPreserveUnicodeNulAndAllOctets() throws Exception {
        success("""
                text: "caf\\u{E9} \\u{1F642}\\u{0}end"
                bytes: Encoding.Latin1.encode("")
                n: 0
                while(n < 256) { bytes.add(n); n = n + 1 }
                query: db.prepare("SELECT ?1, ?2, NULL, '', x''")
                (() => {
                    query.bind(1, text).bind(2, bytes)
                    Assertions.require(query.step())
                    row: query.row()
                    Assertions.require(row[0] == text)
                    Assertions.require(row[1].size() == 256)
                    n = 0
                    while(n < 256) { Assertions.require(row[1][n] == n); n = n + 1 }
                    Assertions.require(row[2] === null)
                    Assertions.require(row[3] == "")
                    Assertions.require(row[4] !== null)
                    Assertions.require(row[4].size() == 0)
                }).ensure(() => query.close().value())
                """);
    }

    @Test void fragmentedMessagesAndPublicValueBoundary() throws Exception {
        // The first and last octets differ from the zero interior, so truncation,
        // loss or reordering of streamed fragments is visible at both ends.
        success("""
                expected: "CAST(x'01' || zeroblob(16777214) || x'FF' AS BLOB)"
                query: db.prepare("SELECT " + expected + ", zeroblob(16777217)")
                (() => {
                    print("PLAT074_STAGE step:started")
                    Assertions.require(query.step())
                    print("PLAT074_STAGE step:completed")
                    bytes: query.column(0)
                    Assertions.require(!String.recognizes(bytes))
                    Assertions.require(bytes.size() == 16777216)
                    Assertions.require((bytes[0] == 1) && (bytes[1] == 0) && (bytes[16777214] == 0) && (bytes[16777215] == 255))
                    print("PLAT074_STAGE value:received")
                    expectFailure(() => query.column(1))
                    // Exactly one row: the stream did not advance the statement twice.
                    Assertions.require(!query.step())
                    query.close().value()
                    bound: db.prepare("SELECT typeof(?1), length(?1), ?1 = " + expected)
                    (() => {
                        print("PLAT074_STAGE bind:started")
                        bound.bind(1, bytes)
                        print("PLAT074_STAGE bind:completed")
                        Assertions.require(bound.step())
                        print("PLAT074_STAGE check:completed")
                        Assertions.require(bound.column(0) == "blob")
                        Assertions.require(bound.column(1) == 16777216)
                        Assertions.require(bound.column(2) == 1)
                        Assertions.require(!bound.step())
                        bound.reset()
                        bytes.add(0)
                        // 16 MiB + 1 is refused before any frame: the earlier binding
                        // and the connection are unchanged.
                        expectFailure(() => bound.bind(1, bytes))
                        Assertions.require(bound.step())
                        Assertions.require(bound.column(0) == "blob")
                        Assertions.require(bound.column(1) == 16777216)
                        Assertions.require(bound.column(2) == 1)
                    }).ensure(() => bound.close().value())
                    // A later small reply on the same connection uses the ordinary message path.
                    Assertions.require(scalar("SELECT 42") == 42)
                }).ensure(() => query.close().value())
                """);
    }

    @Test void completeSingleStatementAdmissionIsPreserved() throws Exception {
        success("""
                for(sql in Array("SELECT", "SELECT 1; SELECT 2", "SELECT 1; invalid", "SELECT 1\\u{0}")) {
                    expectFailure(() => db.prepare(sql))
                }
                Assertions.require(scalar("SELECT 42; -- trailing comment") == 42)
                """);
    }

    @Test void resetKeepsBindingsAndClearBindingsSetsNull() throws Exception {
        success("""
                query: db.prepare("SELECT ?1")
                (() => {
                    query.bind(1, 42)
                    Assertions.require(query.step())
                    Assertions.require(query.column(0) == 42)
                    query.reset()
                    Assertions.require(query.step())
                    Assertions.require(query.column(0) == 42)
                    query.reset().clearBindings()
                    Assertions.require(query.step())
                    Assertions.require(query.column(0) === null)
                }).ensure(() => query.close().value())
                """);
    }

    @Test void rowAndEachKeepIndependentByteCopies() throws Exception {
        success("""
                query: db.prepare("SELECT x'00ff' UNION ALL SELECT x'0102'")
                saved: null
                (() => {
                    Assertions.require(query.step())
                    saved = query.row()
                    copy: query.row()
                    copy[0][0] = 99
                    Assertions.require(saved[0][0] == 0)
                    query.reset()
                    query.each((row) => { row[0][0] = 88 })
                    Assertions.require(saved[0][0] == 0)
                }).ensure(() => query.close().value())
                Assertions.require(saved[0][1] == 255)
                """);
    }

    @Test void commitPersistsAcrossIndependentJvms() throws Exception {
        success("exec(\"CREATE TABLE sample(n)\"); db.begin(); exec(\"INSERT INTO sample VALUES (42)\"); db.commit()");
        success("Assertions.require(scalar(\"SELECT n FROM sample\") == 42)");
    }

    @Test void rollbackDoesNotPersist() throws Exception {
        success("exec(\"CREATE TABLE sample(n)\"); db.begin(); exec(\"INSERT INTO sample VALUES (42)\"); db.rollback()");
        success("Assertions.require(scalar(\"SELECT count(*) FROM sample\") == 0)");
    }

    @Test void cooperativeReceiptReplayHasOneEffect() throws Exception {
        success("""
                exec("CREATE TABLE sample(n)")
                key: Array(7, 9)
                Assertions.require(db.applyOnce(key, "INSERT INTO sample VALUES (?1)", Array(42)).status == "APPLIED")
                Assertions.require(db.applyOnce(key, "INSERT INTO sample VALUES (?1)", Array(42)).status == "REPLAYED")
                Assertions.require(scalar("SELECT count(*) FROM sample") == 1)
                Assertions.require(db.lookup(key).status == "FOUND")
                Assertions.require(db.lookup(Array(7, 10)).status == "ABSENT")
                """);
    }

    @Test void absentDatabaseIsNeverRecreatedOnReopen() throws Exception {
        success("Assertions.require(scalar(\"SELECT 1\") == 1)");
        Files.delete(databaseDirectory.resolve("application.db"));
        Result result = program("expectFailure(() => SQLite.openDurable(sqlite, \"application.db\"))", true, false);
        assertEquals(0, result.code(), result.output());
        assertFalse(Files.exists(databaseDirectory.resolve("application.db")));
    }

    @Test void wrongIdentityIsRefusedBeforeConnectionPublication() throws Exception {
        success("Assertions.require(scalar(\"SELECT 1\") == 1)");
        manifest("application.db", "303", "404", "VERIFY");
        Result result = program("expectFailure(() => SQLite.openDurable(sqlite, \"application.db\"))", true, false);
        assertEquals(0, result.code(), result.output());
    }

    @Test void populatedDatabaseCannotBeAdopted() throws Exception {
        success("exec(\"CREATE TABLE existing(n)\"); exec(\"DROP TABLE protos_database_identity\")");
        Result result = program("expectFailure(() => SQLite.openDurable(sqlite, \"application.db\"))", true, false);
        assertEquals(0, result.code(), result.output());
    }

    @Test void closeRetiresStatementsAndTerminalFuture() throws Exception {
        success("""
                query: db.prepare("SELECT 1")
                first: db.close()
                Assertions.require(first.value() === db)
                Assertions.require(db.close().value() === db)
                expectFailure(() => query.step())
                expectFailure(() => db.prepare("SELECT 2"))
                """);
    }

    @Test void portableExamplesRunOutsideCheckout() throws Exception {
        copyDistribution();
        for (String name : List.of("sqlite-durable-confined.protos", "sqlite-durable-reopen.protos")) {
            Result result = command(List.of(distribution.resolve("bin/protos").toString(),
                    "--allow-sqlite=" + databaseDirectory,
                    distribution.resolve("protos/examples/ffi/" + name).toString()));
            assertEquals(0, result.code(), result.output());
        }
    }

    @Test void distributionProfileAndProviderArePresent() throws Exception {
        Path launcher = distribution.resolve("lib/sqlite/sandbox-exec");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(launcher)));
        assertEquals("profile=linux-landlock-abi10\nlauncher_sha256=" + digest + "\n",
                Files.readString(distribution.resolve("lib/sqlite/PROFILE.txt")));
        assertTrue(Files.isRegularFile(distribution.resolve("lib/sqlite/protos-ffm-provider.jar")));
        assertTrue(Files.isRegularFile(distribution.resolve("protos/lib/sqlite/isolated-helper.protos")));
    }

    @Test void insufficientAbiHasNoFallback() throws Exception {
        Result result = command(List.of(distribution.resolve("lib/sqlite/sandbox-exec").toString(),
                "--require-abi", "2147483647", "--abi-only"));
        assertEquals(69, result.code(), result.output());
        assertTrue(result.output().contains("LANDLOCK_ABI_INSUFFICIENT")
                || result.output().contains("PROFILE_DOES_NOT_COVER_ABI"), result.output());
    }

    private void copyDistribution() throws Exception {
        Path copied = work.resolve("installation");
        try (var paths = Files.walk(distribution)) {
            for (Path source : paths.toList()) {
                Path target = copied.resolve(distribution.relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(target);
                else Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        distribution = copied;
    }

    @Test void missingLauncherIsRefused() throws Exception {
        copyDistribution();
        Files.delete(distribution.resolve("lib/sqlite/sandbox-exec"));
        Result result = program("expectFailure(() => SQLite.openDurable(sqlite, \"application.db\"))", true, false);
        assertEquals(0, result.code(), result.output());
        assertFalse(Files.exists(databaseDirectory.resolve("application.db")));
    }

    @Test void changedLauncherDigestIsRefused() throws Exception {
        copyDistribution();
        Path launcher = distribution.resolve("lib/sqlite/sandbox-exec");
        byte[] contents = Files.readAllBytes(launcher);
        contents[contents.length - 1] ^= 1;
        Files.write(launcher, contents);
        Result result = program("expectFailure(() => SQLite.openDurable(sqlite, \"application.db\"))", true, false);
        assertEquals(0, result.code(), result.output());
        assertFalse(Files.exists(databaseDirectory.resolve("application.db")));
    }

    private static String guestString(Path path) {
        return "\"" + path.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Path exteriorDatabase() throws Exception {
        // Positive control without Landlock: real SQLite through the authorized FFM provider.
        // openMemory() passes SQLITE_OPEN_MEMORY, and SQLite opens every ATTACHed database of
        // such a connection in memory, so it cannot create the exterior file. The control opens
        // ":memory:" by name with READWRITE|CREATE only, using the production ABI and the shared
        // Resources engine copied unchanged; ATTACH then creates a real on-disk database.
        Path outside = work.resolve("outside.db");
        Files.createDirectories(work.resolve("internal"));
        Files.copy(distribution.resolve("protos/lib/sqlite/abi.protos"), work.resolve("abi.protos"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(distribution.resolve("protos/lib/sqlite/internal/Borrowed.protos"),
                work.resolve("internal/Borrowed.protos"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(distribution.resolve("protos/lib/sqlite/internal/Resources.protos"),
                work.resolve("Resources.protos"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Result result = program("""
                controlCompleted: false
                (() => {
                stageMark("control:imports")
                ABI: import("./abi.protos")
                Resources: import("./Resources.protos")
                ControlError: Error {}
                terminal: {
                    closeAllowed: () => true
                    resolved: (value) => (() => value).future()
                    failed: (error) => (() => error.signal()).future()
                }
                stageMark("control:open")
                db: Resources.openConnection(ABI, terminal, ControlError, ":memory:",
                    ABI.SQLITE_OPEN_READWRITE + ABI.SQLITE_OPEN_CREATE)
                exec: (sql, value) => {
                    statement: db.prepare(sql)
                    (() => {
                        if(value !== null) { statement.bind(1, value) }
                        Assertions.require(!statement.step())
                    }).ensure(() => statement.close().value())
                }
                scalar: (sql) => {
                    statement: db.prepare(sql)
                    value: null
                    (() => {
                        Assertions.require(statement.step())
                        value = statement.column(0)
                        Assertions.require(!statement.step())
                    }).ensure(() => statement.close().value())
                    value
                }
                stageMark("control:attach")
                exec("ATTACH DATABASE ?1 AS exterior", %s)
                stageMark("control:database-list")
                Assertions.require(scalar("SELECT count(*) FROM pragma_database_list WHERE name = 'exterior'") == 1)
                stageMark("control:create")
                exec("CREATE TABLE exterior.sample(n)", null)
                stageMark("control:insert")
                exec("INSERT INTO exterior.sample VALUES (73)", null)
                stageMark("control:select")
                Assertions.require(scalar("SELECT n FROM exterior.sample") == 73)
                stageMark("control:close")
                Assertions.require(db.close().value() === db)
                stageMark("control:report")
                Streams: import("std:io/process_streams")
                out: Streams.stdoutWriter(process)
                out.writeLine("EXTERIOR_CONTROL_READY").value()
                out.flush().value()
                controlCompleted = true
                }).ensure(() => {
                    if(!controlCompleted) { print("SQLITE_STAGE_TRAIL " + stageTrail()) }
                })
                """.formatted(guestString(outside)), false, true);
        String evidence = "exterior positive control: exit=" + result.code() + "\n" + result.output();
        assertEquals(0, result.code(), evidence);
        assertTrue(result.output().contains("EXTERIOR_CONTROL_READY"), evidence);
        assertTrue(Files.exists(outside), "positive control must create a real SQLite database file\n" + evidence);
        byte[] header = Files.readAllBytes(outside);
        assertTrue(header.length >= 16 && new String(header, 0, 16, StandardCharsets.ISO_8859_1)
                .equals("SQLite format 3\0"), "exterior file is not an SQLite database\n" + evidence);
        return outside;
    }

    @Test void exteriorNativeAttachIsDeniedWithPositiveControl() throws Exception {
        Path outside = exteriorDatabase();
        byte[] before = Files.readAllBytes(outside);
        success("""
                attach: db.prepare("ATTACH DATABASE ?1 AS exterior")
                attach.bind(1, %s)
                denied: false
                Error.handle(() => attach.step(), (error) => {
                    Assertions.require(isKind(error, SQLite.SQLiteError))
                    Assertions.require(error.code == 14)
                    denied = true
                })
                Assertions.require(denied)
                // sqlite3_finalize preserves the failed step's SQLite status.
                Error.handle(() => attach.close().value(), (error) => null)
                Assertions.require(scalar("SELECT count(*) FROM pragma_database_list") == 1)
                """.formatted(guestString(outside)));
        assertArrayEquals(before, Files.readAllBytes(outside));
    }

    @Test void interiorHardlinkDemonstratesPathRatherThanInodeGuarantee() throws Exception {
        Path outside = exteriorDatabase();
        Path link = databaseDirectory.resolve("alias.db");
        Files.createLink(link, outside);
        assertTrue(Files.isSameFile(link, outside));
        success("""
                attach: db.prepare("ATTACH DATABASE ?1 AS exterior")
                attach.bind(1, %s)
                Assertions.require(!attach.step())
                attach.close().value()
                Assertions.require(scalar("SELECT n FROM exterior.sample") == 73)
                """.formatted(guestString(link)));
    }

    @Test void untrustedDirectoryPermissionsAreRejected() throws Exception {
        Files.setPosixFilePermissions(databaseDirectory, PosixFilePermissions.fromString("rwxrwxrwx"));
        Result result = program("Error().signal()", true, false);
        assertNotEquals(0, result.code());
        assertTrue(result.output().contains("must not be writable by group or others"), result.output());
    }

    private void lifecycle(String ending, boolean killOwner) throws Exception {
        Path source = work.resolve("lifecycle.protos");
        Files.writeString(source, PRELUDE + DATABASE + """
                Streams: import("std:io/process_streams")
                input: Streams.stdinReader(process)
                print("READY")
                input.readLine().value()
                """ + ending);
        ProcessBuilder builder = new ProcessBuilder(distribution.resolve("bin/protos").toString(),
                "--allow-sqlite=" + databaseDirectory, source.toString()).directory(work.toFile())
                .redirectError(work.resolve("lifecycle-errors.txt").toFile());
        builder.environment().remove("PROTOS_HOME");
        long startNanos = System.nanoTime();
        Process owner = builder.start();
        childStarted(owner, "CLI");
        List<ProcessHandle> children = List.of();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(owner.getInputStream(), StandardCharsets.UTF_8))) {
            String ready;
            try {
                ready = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try { return reader.readLine(); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }).get(60, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException timeout) {
                // The reading thread still holds the reader lock inside readLine(); closing the
                // reader first would block forever. End the child so that readLine() returns.
                childTimedOut(owner, startNanos, work.resolve("lifecycle-errors.txt"));
                owner.descendants().forEach(ProcessHandle::destroyForcibly);
                owner.destroyForcibly();
                throw timeout;
            }
            assertEquals("READY", ready, Files.readString(work.resolve("lifecycle-errors.txt")));
            children = owner.descendants().filter(handle -> handle.info().commandLine().orElse("")
                    .contains("isolated-helper.protos")).toList();
            assertEquals(1, children.size(), "READY must precede observation of exactly one real helper JVM");
            if (killOwner) owner.destroyForcibly();
            else {
                owner.getOutputStream().write('\n');
                owner.getOutputStream().flush();
                owner.getOutputStream().close();
            }
            assertTrue(owner.waitFor(30, TimeUnit.SECONDS), "owner did not terminate");
            for (ProcessHandle helper : children) {
                helper.onExit().get(15, TimeUnit.SECONDS);
                assertFalse(helper.isAlive(), "helper survived owner retirement");
            }
            if (ending.contains("db.close")) assertEquals(0, owner.exitValue());
            childEnded(owner, startNanos);
        } finally {
            for (ProcessHandle helper : children) if (helper.isAlive()) helper.destroyForcibly();
            if (owner.isAlive()) owner.destroyForcibly();
        }
    }

    @Test void closingConnectionTerminatesHelper() throws Exception {
        lifecycle("db.close().value()\n", false);
    }

    @Test void rootActorFailureRetiresHelper() throws Exception {
        lifecycle("Error().signal()\n", false);
    }

    @Test void osProcessDeathRetiresHelperWithoutGuestCleanup() throws Exception {
        lifecycle("", true);
    }

    @Test void inMemoryLifecycleFailureModelsRemainApplicable() throws Exception {
        var regression = new ProtosLib029SqliteLifecycleTest();
        regression.acquisitionReuseAndReleaseFailuresPreserveCustodyAndFirstError();
        regression.borrowedColumnsCopyInOrderAndRejectUncertainNullAndOversizedResults();
    }


    private void protocolProgram(String assertions) throws Exception {
        Files.copy(distribution.resolve("protos/lib/sqlite/internal/IsolatedProtocol.protos"), work.resolve("Protocol.protos"));
        Result result = program("Protocol: import(\"./Protocol.protos\")\n" + assertions, false, false);
        assertEquals(0, result.code(), result.output());
    }

    @Test void binary64WireCodecPreservesSpecialValuesAndSignedZero() throws Exception {
        protocolProgram("""
                smallest: 1.0
                index: 0
                while(index < 1074) { smallest = smallest / 2.0; index = index + 1 }
                for(value in Array(0.0, -0.0, 1.0, -1.5, smallest, -smallest,
                    1.7976931348623157e308, -1.7976931348623157e308,
                    1.0 / 0.0, -1.0 / 0.0, 0.0 / 0.0)) {
                    Assertions.require(Protocol.decodeValue(Protocol.encodeValue(value)) === value)
                }
                """);
    }

    @Test void framingRejectsRepetitionReorderingOversizeInvalidUtf8AndPartialEof() throws Exception {
        protocolProgram("""
                Arrays: import("std:collections/array")
                JSON: import("std:json/json")
                Base64url: import("std:text/base64url")
                frames: Array()
                completed: () => { { value: () => null } }
                writer: {
                    writeLine: (line) => { frames = Arrays.append(frames, line); completed() }
                    flush: () => completed()
                }
                text: ""
                index: 0
                while(index < 5000) { text = text + "a"; index = index + 1 }
                Protocol.writeMessage(writer, 1, text)
                Assertions.require(frames.size() > 1)
                read: (lines) => {
                    position: 0
                    reader: {
                        readLine: (limit) => {
                            line: null
                            if(position < lines.size()) { line = lines[position]; position = position + 1 }
                            { value: () => line }
                        }
                    }
                    Protocol.readMessage(reader, 1)
                }
                Assertions.require(read(frames) == text)
                Assertions.require(read(Array(frames[0])) === null)
                expectFailure(() => read(Array(frames[1], frames[0])))
                expectFailure(() => read(Array(frames[0], frames[0])))
                longLine: frames[0]
                while(Encoding.UTF8.encode(longLine).size() <= 4096) { longLine = longLine + " " }
                expectFailure(() => read(Array(longLine)))
                chunk: Encoding.UTF8.encode("")
                index = 0
                while(index < 2304) { chunk.add(97); index = index + 1 }
                oversized: JSON.encode(JSON.object("v", JSON.number(4, 0), "s", JSON.number(1, 0),
                    "i", JSON.number(0, 0), "n", JSON.number((50331649 + 2303).div(2304), 0),
                    "o", JSON.number(50331649, 0), "d", JSON.string(Base64url.encode(chunk))))
                expectFailure(() => read(Array(oversized)))
                invalid: Encoding.Latin1.encode("")
                invalid.add(255)
                invalidFrame: JSON.encode(JSON.object("v", JSON.number(4, 0), "s", JSON.number(1, 0),
                    "i", JSON.number(0, 0), "n", JSON.number(1, 0), "o", JSON.number(1, 0),
                    "d", JSON.string(Base64url.encode(invalid))))
                expectFailure(() => read(Array(invalidFrame)))
                """);
    }

    @Test void responsesRequireExactSessionAndCorrelation() throws Exception {
        protocolProgram("""
                JSON: import("std:json/json")
                response: Protocol.encodeResponse(7, 9, "BIND", Array(JSON.number(0, 0)))
                Protocol.decodeResponse(response, 7, 9, "BIND")
                expectFailure(() => Protocol.decodeResponse(response, 8, 9, "BIND"))
                expectFailure(() => Protocol.decodeResponse(response, 7, 10, "BIND"))
                rejection: Protocol.encodeRejection(7, 9, "INVALID_ARGUMENT")
                Assertions.require(Protocol.decodeRejection(rejection, 7, 9) == "INVALID_ARGUMENT")
                expectFailure(() => Protocol.decodeResponse(rejection, 7, 9, "BIND"))
                failure: Protocol.encodeSqliteFailure(7, 9, 1)
                Assertions.require(Protocol.decodeSqliteFailure(failure, 7, 9) == 1)
                expectFailure(() => Protocol.decodeSqliteFailure(failure, 8, 9))
                expectFailure(() => Protocol.decodeResponse(failure, 7, 9, "LOOKUP"))
                """);
    }

    @Test void streamedStepRowsAreBoundedOrderedCorrelatedAndAtomic() throws Exception {
        protocolProgram("""
                Arrays: import("std:collections/array")
                JSON: import("std:json/json")
                completed: () => { { value: () => null } }
                capture: (body) => {
                    lines: Array()
                    sink: {
                        writeLine: (line) => { lines = Arrays.append(lines, line); completed() }
                        flush: () => completed()
                    }
                    body(sink)
                    lines
                }
                read: (lines, seq, session, re) => {
                    position: 0
                    reader: {
                        readLine: (limit) => {
                            line: null
                            if(position < lines.size()) { line = lines[position]; position = position + 1 }
                            { value: () => line }
                        }
                    }
                    Protocol.readStepReply(reader, seq, session, re)
                }
                edit: (line, name, node) => {
                    pairs: Array()
                    JSON.parse(line).value.each((key, value) => {
                        pairs = Arrays.append(pairs, key)
                        if(key == name) { pairs = Arrays.append(pairs, node) } else { pairs = Arrays.append(pairs, value) }
                    })
                    JSON.encode(JSON.object(...pairs))
                }
                replaced: (lines, index, line) => {
                    copy: Array(...lines)
                    copy[index] = line
                    copy
                }
                // Raw-line edits of canonical frames (fast-path bypass attempts).
                afterOctets: (line, start) => {
                    octets: Encoding.UTF8.encode(line)
                    rest: Encoding.Latin1.encode("")
                    position: start
                    while(position < octets.size()) { rest.add(octets[position]); position = position + 1 }
                    Encoding.UTF8.decode(rest)
                }
                inserted: (line, text) => "{" + text + afterOctets(line, 1)
                omitted: (line, name) => {
                    pairs: Array()
                    JSON.parse(line).value.each((key, value) => { if(key != name) { pairs = Arrays.append(Arrays.append(pairs, key), value) } })
                    JSON.encode(JSON.object(...pairs))
                }
                payloadFirst: (line) => {
                    members: JSON.parse(line).value
                    pairs: Array("d", members["d"])
                    members.each((key, value) => { if(key != "d") { pairs = Arrays.append(Arrays.append(pairs, key), value) } })
                    JSON.encode(JSON.object(...pairs))
                }
                hexDigits: Encoding.UTF8.encode("0123456789abcdef")
                // The same JSON value with its first payload character as a \u escape.
                escapedPayload: (line) => {
                    first: payloadFirst(line)
                    code: Encoding.UTF8.encode(first)[6]
                    escape: Encoding.Latin1.encode("")
                    escape.add(hexDigits[code.div(16)])
                    escape.add(hexDigits[code % 16])
                    "{\\"d\\":\\"\\\\u00" + Encoding.Latin1.decode(escape) + afterOctets(first, 7)
                }
                // TEXT whose UTF-8 is split inside a 2-octet scalar at the first
                // fragment boundary and inside a 4-octet scalar at the second.
                text: ""
                n: 0
                while(n < 2303) { text = text + "a"; n = n + 1 }
                text = text + "\\u{E9}"
                n = 0
                while(n < 2302) { text = text + "b"; n = n + 1 }
                text = text + "\\u{1F642}z"
                Assertions.require(Encoding.UTF8.encode(text).size() == 4612)
                blob: Encoding.Latin1.encode("")
                n = 0
                while(n < 5000) { blob.add(n % 256); n = n + 1 }
                single: Encoding.Latin1.encode("")
                single.add(0)
                columns: Array(Protocol.rowColumn(Protocol.encodeValue(42), false, null),
                    Protocol.rowColumn(Protocol.encodeValue(null), false, null),
                    Protocol.rowColumn(null, true, Encoding.UTF8.encode(text)),
                    Protocol.rowColumn(null, false, blob),
                    Protocol.rowColumn(null, false, single))
                frames: capture((sink) => Protocol.writeRow(sink, 2, 7, 1, columns))
                // header, two cells, 3 TEXT fragments, 3 BLOB fragments, 1 fragment, terminator
                Assertions.require(frames.size() == 11)
                for(line in frames) { Assertions.require(Encoding.UTF8.encode(line).size() <= 4096) }
                row: read(frames, 2, 7, 1)
                Assertions.require(row.code == 100)
                Assertions.require(row.columns.size() == 5)
                Assertions.require(Protocol.decodeValue(row.columns[0].node) == 42)
                Assertions.require(Protocol.decodeValue(row.columns[1].node) === null)
                Assertions.require(row.columns[2].value == text)
                received: row.columns[3].value
                Assertions.require(!String.recognizes(received) && (received.size() == 5000))
                n = 0
                while(n < 5000) { Assertions.require(received[n] == (n % 256)); n = n + 1 }
                Assertions.require((row.columns[4].value.size() == 1) && (row.columns[4].value[0] == 0))
                // EOF after a partial value publishes nothing.
                Assertions.require(read(Arrays.slice(frames, 0, 4), 2, 7, 1) === null)
                Assertions.require(read(Arrays.slice(frames, 0, 10), 2, 7, 1) === null)
                // Reordered, duplicated, missing and trailing fragments.
                expectFailure(() => read(replaced(replaced(frames, 6, frames[7]), 7, frames[6]), 2, 7, 1))
                expectFailure(() => read(Arrays.concat(Arrays.slice(frames, 0, 7), Arrays.slice(frames, 6, 11)), 2, 7, 1))
                expectFailure(() => read(Arrays.concat(Arrays.slice(frames, 0, 8), Arrays.slice(frames, 9, 11)), 2, 7, 1))
                expectFailure(() => read(Arrays.concat(Arrays.slice(frames, 0, 10), Arrays.slice(frames, 9, 11)), 2, 7, 1))
                // Premature terminator.
                expectFailure(() => read(Arrays.concat(Arrays.slice(frames, 0, 9), Arrays.slice(frames, 10, 11)), 2, 7, 1))
                // Incorrect declared total, beyond the value limit, and wrong column count.
                expectFailure(() => read(replaced(frames, 6, edit(frames[6], "o", JSON.number(5001, 0))), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 9, edit(edit(frames[9], "o", JSON.number(16777217, 0)),
                    "n", JSON.number((16777217 + 2303).div(2304), 0))), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 10, edit(frames[10], "columns", JSON.number(4, 0))), 2, 7, 1))
                // Another session, another correlation, another reply sequence.
                expectFailure(() => read(frames, 2, 8, 1))
                expectFailure(() => read(frames, 2, 7, 2))
                expectFailure(() => read(frames, 3, 7, 1))
                expectFailure(() => read(replaced(frames, 7, edit(frames[7], "session", JSON.number(8, 0))), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, edit(frames[7], "re", JSON.number(2, 0))), 2, 7, 1))
                // Non-canonical Base64url for the one-octet fragment ("AA" is canonical).
                expectFailure(() => read(replaced(frames, 9, edit(frames[9], "d", JSON.string("AB"))), 2, 7, 1))
                // Strict UTF-8 after reassembly.
                invalid: Encoding.Latin1.encode("")
                invalid.add(255)
                invalidText: capture((sink) => Protocol.writeRow(sink, 2, 7, 1, Array(Protocol.rowColumn(null, true, invalid))))
                expectFailure(() => read(invalidText, 2, 7, 1))
                // TEXT/BLOB values never travel as a single cell line.
                expectFailure(() => capture((sink) => Protocol.writeRow(sink, 2, 7, 1,
                    Array(Protocol.rowColumn(Protocol.encodeValue("x"), false, null)))))
                // Canonical fast path versus full validation of a later fragment (frames[7]).
                expectFailure(() => read(replaced(frames, 7, inserted(frames[7], "\\"i\\":1,")), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, inserted(frames[7], "\\"x\\":0,")), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, omitted(frames[7], "n")), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, omitted(frames[7], "re")), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, inserted(frames[7], "\\"q\\":\\"\\\\q\\",")), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, edit(frames[7], "i", JSON.number(2, 0))), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, edit(frames[7], "c", JSON.number(2, 0))), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, edit(frames[7], "s", JSON.number(3, 0))), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, edit(frames[7], "d", JSON.string("AAAA"))), 2, 7, 1))
                // A reordered or escaped but schema-valid line takes the full
                // validation path: still accepted with exact values, while a
                // reordered line with another correlation is still refused.
                Assertions.require(read(replaced(frames, 7, payloadFirst(frames[7])), 2, 7, 1).columns[3].value.size() == 5000)
                Assertions.require(read(replaced(frames, 7, escapedPayload(frames[7])), 2, 7, 1).columns[3].value.size() == 5000)
                expectFailure(() => read(replaced(frames, 7, payloadFirst(edit(frames[7], "re", JSON.number(2, 0)))), 2, 7, 1))
                expectFailure(() => read(replaced(frames, 7, payloadFirst(edit(frames[7], "session", JSON.number(8, 0)))), 2, 7, 1))
                // A fragment after the terminator is not part of the completed row.
                late: read(Arrays.concat(frames, Arrays.slice(frames, 7, 8)), 2, 7, 1)
                Assertions.require((late.columns.size() == 5) && (late.columns[3].value.size() == 5000))
                Assertions.require(late.columns[2].value == text)
                // A later small reply of a new session uses the ordinary message path.
                small: Protocol.encodeResponse(8, 1, "STEP", Array(JSON.number(101, 0), JSON.nullValue()))
                ordinary: read(capture((sink) => Protocol.writeMessage(sink, 2, small)), 2, 8, 1)
                Assertions.require(ordinary == small)
                Protocol.decodeResponse(ordinary, 8, 1, "STEP")
                """);
    }


    @Test void streamedParameterRequestsAreCompleteCorrelatedAndUnambiguous() throws Exception {
        protocolProgram("""
                Arrays: import("std:collections/array")
                JSON: import("std:json/json")
                completed: () => { { value: () => null } }
                capture: (body) => {
                    lines: Array()
                    sink: {
                        writeLine: (line) => { lines = Arrays.append(lines, line); completed() }
                        flush: () => completed()
                    }
                    body(sink)
                    lines
                }
                reader: (lines) => {
                    position: 0
                    {
                        readLine: (limit) => {
                            line: null
                            if(position < lines.size()) { line = lines[position]; position = position + 1 }
                            { value: () => line }
                        }
                    }
                }
                read: (lines, seq) => Protocol.readRequest(reader(lines), seq)
                edit: (line, name, node) => {
                    pairs: Array()
                    JSON.parse(line).value.each((key, value) => {
                        pairs = Arrays.append(pairs, key)
                        if(key == name) { pairs = Arrays.append(pairs, node) } else { pairs = Arrays.append(pairs, value) }
                    })
                    JSON.encode(JSON.object(...pairs))
                }
                extended: (line, name, node) => {
                    pairs: Array()
                    JSON.parse(line).value.each((key, value) => { pairs = Arrays.append(Arrays.append(pairs, key), value) })
                    JSON.encode(JSON.object(...Arrays.append(Arrays.append(pairs, name), node)))
                }
                replaced: (lines, index, line) => {
                    copy: Array(...lines)
                    copy[index] = line
                    copy
                }
                everyLine: (lines, name, node) => {
                    copy: Array()
                    for(line in lines) { copy = Arrays.append(copy, edit(line, name, node)) }
                    copy
                }
                // Raw-line edits of canonical frames (fast-path bypass attempts).
                afterOctets: (line, start) => {
                    octets: Encoding.UTF8.encode(line)
                    rest: Encoding.Latin1.encode("")
                    position: start
                    while(position < octets.size()) { rest.add(octets[position]); position = position + 1 }
                    Encoding.UTF8.decode(rest)
                }
                inserted: (line, text) => "{" + text + afterOctets(line, 1)
                omitted: (line, name) => {
                    pairs: Array()
                    JSON.parse(line).value.each((key, value) => { if(key != name) { pairs = Arrays.append(Arrays.append(pairs, key), value) } })
                    JSON.encode(JSON.object(...pairs))
                }
                payloadFirst: (line) => {
                    members: JSON.parse(line).value
                    pairs: Array("d", members["d"])
                    members.each((key, value) => { if(key != "d") { pairs = Arrays.append(Arrays.append(pairs, key), value) } })
                    JSON.encode(JSON.object(...pairs))
                }
                hexDigits: Encoding.UTF8.encode("0123456789abcdef")
                // The same JSON value with its first payload character as a \u escape.
                escapedPayload: (line) => {
                    first: payloadFirst(line)
                    code: Encoding.UTF8.encode(first)[6]
                    escape: Encoding.Latin1.encode("")
                    escape.add(hexDigits[code.div(16)])
                    escape.add(hexDigits[code % 16])
                    "{\\"d\\":\\"\\\\u00" + Encoding.Latin1.decode(escape) + afterOctets(first, 7)
                }
                // TEXT of 131072 octets: NUL and multi-octet scalars cross fragment boundaries.
                text: "a\\u{0}\\u{E9}\\u{1F642}"
                while(Encoding.UTF8.encode(text).size() < 65537) { text = text + text }
                blob: Encoding.Latin1.encode("")
                n: 0
                while(n < 70000) { blob.add(n % 251); n = n + 1 }
                blob[0] = 1
                blob[69999] = 255
                statement: JSON.number(3, 0)
                index: JSON.number(1, 0)
                bindLines: (value) => capture((sink) => Protocol.writeRequest(sink, 4, 7, 4, "BIND",
                    Array(statement, index), Array(Protocol.requestColumn(value))))
                frames: bindLines(blob)
                // header, ceil(70000 / 2304) = 31 fragments, terminator
                Assertions.require(frames.size() == 33)
                for(line in frames) { Assertions.require(Encoding.UTF8.encode(line).size() <= 4096) }
                request: read(frames, 4)
                Assertions.require((request.id == 4) && (request.session == 7) && (request.operation == "BIND"))
                Assertions.require((Protocol.id(request.members["statement"]) == 3) && (Protocol.i64(request.members["index"]) == 1))
                Assertions.require(request.values.size() == 1)
                received: request.values[0]
                Assertions.require(!String.recognizes(received) && (received.size() == 70000))
                n = 0
                while(n < 70000) { Assertions.require(received[n] == blob[n]); n = n + 1 }
                // TEXT stays TEXT, decoded only after reassembly of split UTF-8.
                textFrames: bindLines(text)
                Assertions.require(read(textFrames, 4).values[0] == text)
                // A later mutation of the caller's Bytes cannot reach a written request.
                column: Protocol.requestColumn(blob)
                blob[1] = 77
                Assertions.require(column.octets[1] == 1)
                blob[1] = 1
                // EOF anywhere before the terminator admits nothing.
                Assertions.require(read(Array(frames[0]), 4) === null)
                Assertions.require(read(Arrays.slice(frames, 0, 17), 4) === null)
                Assertions.require(read(Arrays.slice(frames, 0, 32), 4) === null)
                // Reordered, repeated and missing fragments.
                expectFailure(() => read(replaced(replaced(frames, 5, frames[6]), 6, frames[5]), 4))
                expectFailure(() => read(Arrays.concat(Arrays.slice(frames, 0, 6), Arrays.slice(frames, 5, 33)), 4))
                expectFailure(() => read(Arrays.concat(Arrays.slice(frames, 0, 5), Arrays.slice(frames, 6, 33)), 4))
                // Premature and duplicated terminators.
                expectFailure(() => read(Arrays.concat(Arrays.slice(frames, 0, 31), Arrays.slice(frames, 32, 33)), 4))
                duplicated: Arrays.concat(frames, Arrays.slice(frames, 32, 33))
                duplicatedReader: reader(duplicated)
                Assertions.require(Protocol.readRequest(duplicatedReader, 4) !== null)
                // The extra terminator cannot become the next request (sequence 5).
                expectFailure(() => Protocol.readRequest(duplicatedReader, 5))
                // Another session or request id on one line; another reply sequence.
                expectFailure(() => read(replaced(frames, 9, edit(frames[9], "session", JSON.number(8, 0))), 4))
                expectFailure(() => read(replaced(frames, 9, edit(frames[9], "id", JSON.number(5, 0))), 4))
                expectFailure(() => read(frames, 5))
                // Consistent foreign session/id lines are admitted only as such:
                // the helper compares them with its own session and next id.
                Assertions.require(read(everyLine(frames, "session", JSON.number(8, 0)), 4).session == 8)
                Assertions.require(read(everyLine(frames, "id", JSON.number(9, 0)), 4).id == 9)
                // A statement id or parameter index cannot change between lines.
                expectFailure(() => read(replaced(frames, 9, extended(frames[9], "statement", JSON.number(4, 0))), 4))
                expectFailure(() => read(replaced(frames, 9, edit(frames[9], "c", JSON.number(1, 0))), 4))
                expectFailure(() => read(replaced(frames, 32, extended(frames[32], "index", JSON.number(2, 0))), 4))
                // Raw-line bypass attempts on a later fragment (frames[9]).
                expectFailure(() => read(replaced(frames, 9, inserted(frames[9], "\\"i\\":8,")), 4))
                expectFailure(() => read(replaced(frames, 9, inserted(frames[9], "\\"x\\":0,")), 4))
                expectFailure(() => read(replaced(frames, 9, omitted(frames[9], "id")), 4))
                expectFailure(() => read(replaced(frames, 9, inserted(frames[9], "\\"q\\":\\"\\\\q\\",")), 4))
                expectFailure(() => read(replaced(frames, 9, edit(frames[9], "d", JSON.string("AAAA"))), 4))
                Assertions.require(read(replaced(frames, 9, payloadFirst(frames[9])), 4).values[0].size() == 70000)
                Assertions.require(read(replaced(frames, 9, escapedPayload(frames[9])), 4).values[0].size() == 70000)
                expectFailure(() => read(replaced(frames, 9, payloadFirst(edit(frames[9], "id", JSON.number(5, 0)))), 4))
                // Incorrect declared total and type, and a value beyond 16 MiB.
                expectFailure(() => read(replaced(frames, 1, edit(frames[1], "o", JSON.number(70001, 0))), 4))
                expectFailure(() => read(replaced(frames, 9, edit(frames[9], "t", JSON.string("text"))), 4))
                expectFailure(() => read(replaced(frames, 1, edit(edit(frames[1], "o", JSON.number(16777217, 0)),
                    "n", JSON.number((16777217 + 2303).div(2304), 0))), 4))
                expectFailure(() => read(replaced(frames, 32, edit(frames[32], "values", JSON.number(2, 0))), 4))
                // Non-canonical Base64url in the last fragment (70000 - 30 * 2304 = 880 octets).
                // Its final octet 255 encodes as "...w"; "x" sets a padding bit.
                lastPayload: JSON.parse(frames[31]).value["d"].value
                tampered: Encoding.UTF8.encode(lastPayload)
                Assertions.require(tampered[tampered.size() - 1] == 119)
                tampered[tampered.size() - 1] = 120
                expectFailure(() => read(replaced(frames, 31, edit(frames[31], "d",
                    JSON.string(Encoding.UTF8.decode(tampered)))), 4))
                // Strict UTF-8 after reassembly: the BLOB octets relabelled as TEXT
                // (lone continuation octets) are refused.
                invalidLines: Array(...frames)
                n = 1
                while(n < 32) { invalidLines[n] = edit(invalidLines[n], "t", JSON.string("text")); n = n + 1 }
                expectFailure(() => read(invalidLines, 4))
                // 16 MiB + 1 is refused before the first line is written.
                zeros: "\\u{0}"
                n = 0
                while(n < 24) { zeros = zeros + zeros; n = n + 1 }
                tooLarge: Encoding.Latin1.encode(zeros + "\\u{0}")
                Assertions.require(tooLarge.size() == 16777217)
                expectFailure(() => Protocol.requestColumn(tooLarge))
                // One encoding per request: no streamed form without a large
                // parameter, on either side.
                small: Encoding.Latin1.encode("")
                small.add(1)
                expectFailure(() => capture((sink) => Protocol.writeRequest(sink, 4, 7, 4, "BIND",
                    Array(statement, index), Array(Protocol.requestColumn(small)))))
                expectFailure(() => capture((sink) => Protocol.writeRequest(sink, 4, 7, 4, "BIND",
                    Array(statement, index), Array(Protocol.requestColumn(42)))))
                Assertions.require(!Protocol.streams(Array(Protocol.requestColumn(small)), 0))
                Assertions.require(Protocol.streams(Array(Protocol.requestColumn(blob)), 0))
                // APPLY_ONCE: SQL as TEXT value 0, then its parameters, after one terminator.
                sql: "INSERT INTO s VALUES (?1, ?2)"
                apply: capture((sink) => Protocol.writeRequest(sink, 4, 7, 4, "APPLY_ONCE",
                    Array(JSON.number(7, 0), JSON.number(9, 0)),
                    Array(Protocol.requestColumn(sql), Protocol.requestColumn(42), Protocol.requestColumn(blob))))
                applied: read(apply, 4)
                Assertions.require(applied.operation == "APPLY_ONCE")
                Assertions.require((Protocol.id(applied.members["client"]) == 7) && (Protocol.id(applied.members["operation"]) == 9))
                Assertions.require((applied.values.size() == 3) && (applied.values[0] == sql) && (applied.values[1] == 42))
                Assertions.require(applied.values[2].size() == 70000)
                Assertions.require(read(Arrays.slice(apply, 0, apply.size() - 1), 4) === null)
                // The SQL slot must be TEXT.
                expectFailure(() => capture((sink) => Protocol.writeRequest(sink, 4, 7, 4, "APPLY_ONCE",
                    Array(JSON.number(7, 0), JSON.number(9, 0)),
                    Array(Protocol.requestColumn(small), Protocol.requestColumn(blob)))))
                // A small ordinary request is unchanged and still decodes.
                ordinary: Protocol.encodeRequest(4, 7, "BIND", Array(statement, index, Protocol.encodeValue(5)))
                text4: read(capture((sink) => Protocol.writeMessage(sink, 4, ordinary)), 4)
                Assertions.require(text4 == ordinary)
                Assertions.require(Protocol.decodeRequest(text4).values === null)
                """);
    }

    private static final String HELPER_SESSIONS = """
            Helper: import("./sqlitecopy/internal/IsolatedHelper.protos")
            Protocol: import("./sqlitecopy/internal/IsolatedProtocol.protos")
            Arrays: import("std:collections/array")
            JSON: import("std:json/json")
            directory: "@DIRECTORY@"
            completed: () => { { value: () => null } }
            lineReader: (lines) => {
                position: 0
                {
                    readLine: (limit) => {
                        line: null
                        if(position < lines.size()) { line = lines[position]; position = position + 1 }
                        { value: () => line }
                    }
                }
            }
            capture: (body) => {
                lines: Array()
                sink: {
                    writeLine: (line) => { lines = Arrays.append(lines, line); completed() }
                    flush: () => completed()
                }
                body(sink)
                lines
            }
            edit: (line, name, node) => {
                pairs: Array()
                JSON.parse(line).value.each((key, value) => {
                    pairs = Arrays.append(pairs, key)
                    if(key == name) { pairs = Arrays.append(pairs, node) } else { pairs = Arrays.append(pairs, value) }
                })
                JSON.encode(JSON.object(...pairs))
            }
            extended: (line, name, node) => {
                pairs: Array()
                JSON.parse(line).value.each((key, value) => { pairs = Arrays.append(Arrays.append(pairs, key), value) })
                JSON.encode(JSON.object(...Arrays.append(Arrays.append(pairs, name), node)))
            }
            replaced: (lines, index, line) => {
                copy: Array(...lines)
                copy[index] = line
                copy
            }
            everyLine: (lines, name, node) => {
                copy: Array()
                for(line in lines) { copy = Arrays.append(copy, edit(line, name, node)) }
                copy
            }
            ordinary: (id, operation, nodes) => capture((sink) => Protocol.writeMessage(sink, id,
                Protocol.encodeRequest(id, 7, operation, nodes)))
            streamed: (id, operation, nodes, values) => {
                columns: Array()
                for(value in values) { columns = Arrays.append(columns, Protocol.requestColumn(value)) }
                capture((sink) => Protocol.writeRequest(sink, id, 7, id, operation, nodes, columns))
            }
            // One session of the production helper module over the concatenated
            // request lines; answers {clean, replies}, READY being replies[0].
            serveSession: (requests, provision) => {
                lines: Array()
                for(request in requests) { lines = Arrays.concat(lines, request) }
                out: Array()
                writer: {
                    writeLine: (line) => { out = Arrays.append(out, line); completed() }
                    flush: () => completed()
                }
                clean: Helper.serve(lineReader(lines), writer, (text) => null, directory, "probe.db",
                    7, 101, 202, provision)
                replies: Array()
                replyReader: lineReader(out)
                seq: 1
                reading: true
                while(reading) {
                    text: Protocol.readMessage(replyReader, seq)
                    if(text === null) { reading = false } else { replies = Arrays.append(replies, text); seq = seq + 1 }
                }
                { clean: clean; replies: replies }
            }
            statement: JSON.number(1, 0)
            prepare: (id, sql) => ordinary(id, "PREPARE", Array(JSON.string(sql)))
            onStatement: (id, operation) => ordinary(id, operation, Array(statement))
            close: (id) => ordinary(id, "CLOSE", Array())
            okMembers: (text, re, operation) => Protocol.decodeResponse(text, 7, re, operation)
            okCode: (text, re, operation) => Protocol.i64(okMembers(text, re, operation)["code"])
            rowValues: (text, re) => {
                values: Array()
                for(node in okMembers(text, re, "STEP")["row"].value) { values = Arrays.append(values, Protocol.decodeValue(node)) }
                values
            }
            """;

    /**
     * Runs {@code body} with the production helper module serving scripted request lines in
     * this guest process, against real SQLite through the explicit provider. Replies are read
     * back with the production protocol reader. Java creates only the empty database file.
     */
    private void helperSessions(String body) throws Exception {
        Path copy = work.resolve("sqlitecopy");
        Files.createDirectories(copy.resolve("internal"));
        Files.copy(distribution.resolve("protos/lib/sqlite/abi.protos"), copy.resolve("abi.protos"));
        try (var files = Files.list(distribution.resolve("protos/lib/sqlite/internal"))) {
            for (Path file : files.toList()) Files.copy(file, copy.resolve("internal").resolve(file.getFileName()));
        }
        Path directory = Files.createDirectory(work.resolve("helper-databases"));
        Files.createFile(directory.resolve("probe.db"));
        Result result = program(HELPER_SESSIONS.replace("@DIRECTORY@", directory.toString()) + body, false, true);
        assertEquals(0, result.code(), result.output());
    }

    @Test void helperDispatchesStreamedBindOnlyAfterCompleteValidRequest() throws Exception {
        helperSessions("""
                setup: serveSession(Array(prepare(1, "CREATE TABLE s(v)"), onStatement(2, "STEP"),
                    onStatement(3, "FINALIZE"), close(4)), true)
                Assertions.require(setup.clean && (setup.replies.size() == 5))
                Assertions.require(okCode(setup.replies[2], 2, "STEP") == 101)
                blob: Encoding.Latin1.encode("")
                n: 0
                while(n < 70000) { blob.add(n % 251); n = n + 1 }
                blob[0] = 1
                blob[69999] = 255
                text: "a\\u{0}\\u{E9}\\u{1F642}"
                while(Encoding.UTF8.encode(text).size() < 65537) { text = text + text }
                // A small ordinary BIND precedes each streamed one on the same statement.
                prefix: Array(prepare(1, "SELECT typeof(?1), length(CAST(?1 AS BLOB)), hex(substr(CAST(?1 AS BLOB), 1, 1)) || hex(substr(CAST(?1 AS BLOB), -1, 1))"),
                    ordinary(2, "BIND", Array(statement, JSON.number(1, 0), Protocol.encodeValue(5))))
                big: streamed(3, "BIND", Array(statement, JSON.number(1, 0)), Array(blob))
                // Valid BLOB and TEXT, each followed by a small STEP reply.
                valid: serveSession(Arrays.concat(prefix, Array(big, onStatement(4, "STEP"), onStatement(5, "FINALIZE"), close(6))), false)
                Assertions.require(valid.clean && (valid.replies.size() == 7))
                Assertions.require((okCode(valid.replies[2], 2, "BIND") == 0) && (okCode(valid.replies[3], 3, "BIND") == 0))
                row: rowValues(valid.replies[4], 4)
                Assertions.require((row[0] == "blob") && (row[1] == 70000) && (row[2] == "01FF"))
                textual: serveSession(Arrays.concat(prefix, Array(streamed(3, "BIND", Array(statement, JSON.number(1, 0)), Array(text)),
                    onStatement(4, "STEP"), close(5))), false)
                Assertions.require(textual.clean && (textual.replies.size() == 6))
                row = rowValues(textual.replies[4], 4)
                Assertions.require((row[0] == "text") && (row[1] == Encoding.UTF8.encode(text).size()))
                // Every refused request is answered only by the rejection: no BIND
                // reply precedes it, so the streamed BIND was never dispatched.
                refused: (lines, reason, re) => {
                    session: serveSession(Arrays.concat(prefix, Array(lines, onStatement(4, "STEP"))), false)
                    Assertions.require(!session.clean && (session.replies.size() == 4))
                    Assertions.require(okCode(session.replies[2], 2, "BIND") == 0)
                    Assertions.require(Protocol.decodeRejection(session.replies[3], 7, re) == reason)
                }
                refused(replaced(replaced(big, 5, big[6]), 6, big[5]), "MALFORMED_FRAME", null)
                refused(Arrays.concat(Arrays.slice(big, 0, 6), Arrays.slice(big, 5, 33)), "MALFORMED_FRAME", null)
                refused(Arrays.concat(Arrays.slice(big, 0, 5), Arrays.slice(big, 6, 33)), "MALFORMED_FRAME", null)
                refused(Arrays.concat(Arrays.slice(big, 0, 31), Arrays.slice(big, 32, 33)), "MALFORMED_FRAME", null)
                refused(replaced(big, 9, edit(big[9], "session", JSON.number(8, 0))), "MALFORMED_FRAME", null)
                refused(replaced(big, 9, edit(big[9], "id", JSON.number(4, 0))), "MALFORMED_FRAME", null)
                refused(replaced(big, 9, extended(big[9], "statement", JSON.number(2, 0))), "MALFORMED_FRAME", null)
                refused(replaced(big, 9, edit(big[9], "c", JSON.number(1, 0))), "MALFORMED_FRAME", null)
                refused(replaced(big, 1, edit(big[1], "o", JSON.number(70001, 0))), "MALFORMED_FRAME", null)
                refused(everyLine(big, "session", JSON.number(8, 0)), "WRONG_SESSION", null)
                refused(everyLine(big, "id", JSON.number(4, 0)), "NON_CONSECUTIVE_ID", null)
                // A complete request naming another statement or index 0 is refused
                // before any SQLite binding.
                refused(replaced(big, 0, edit(big[0], "statement", JSON.number(2, 0))), "INVALID_ARGUMENT", 3)
                refused(replaced(big, 0, edit(big[0], "index", JSON.number(0, 0))), "INVALID_ARGUMENT", 3)
                // The same large value as one ordinary message has no admitted encoding.
                refused(ordinary(3, "BIND", Array(statement, JSON.number(1, 0), Protocol.encodeValue(blob))), "MALFORMED_REQUEST", null)
                // EOF before the terminator: nothing dispatched, nothing answered.
                partial: serveSession(Arrays.concat(prefix, Array(Arrays.slice(big, 0, 17))), false)
                Assertions.require(partial.clean && (partial.replies.size() == 3))
                // A duplicated terminator follows a complete BIND: the BIND is answered
                // once, and the extra line is refused as the next request.
                duplicated: serveSession(Arrays.concat(prefix, Array(Arrays.concat(big, Arrays.slice(big, 32, 33)))), false)
                Assertions.require(!duplicated.clean && (duplicated.replies.size() == 5))
                Assertions.require(okCode(duplicated.replies[3], 3, "BIND") == 0)
                Assertions.require(Protocol.decodeRejection(duplicated.replies[4], 7, null) == "MALFORMED_FRAME")
                """);
    }

    @Test void helperAppliesStreamedApplyOnceOnlyWhenCompleteAndReplaysByReceipt() throws Exception {
        helperSessions("""
                setup: serveSession(Array(prepare(1, "CREATE TABLE s(v)"), onStatement(2, "STEP"),
                    onStatement(3, "FINALIZE"), close(4)), true)
                Assertions.require(setup.clean && (setup.replies.size() == 5))
                blob: Encoding.Latin1.encode("")
                n: 0
                while(n < 70000) { blob.add(n % 251); n = n + 1 }
                text: "a\\u{0}\\u{E9}\\u{1F642}"
                while(Encoding.UTF8.encode(text).size() < 65537) { text = text + text }
                applyLines: (id, operation, value) => streamed(id, "APPLY_ONCE",
                    Array(JSON.number(7, 0), JSON.number(operation, 0)), Array("INSERT INTO s VALUES (?1)", value))
                rows: () => {
                    session: serveSession(Array(prepare(1, "SELECT count(*), coalesce(sum(length(CAST(v AS BLOB))), 0) FROM s"),
                        onStatement(2, "STEP"), onStatement(3, "FINALIZE"), close(4)), false)
                    Assertions.require(session.clean)
                    rowValues(session.replies[2], 2)
                }
                outcome: (text, re) => Protocol.string(okMembers(text, re, "APPLY_ONCE")["outcome"])
                lookup: (id, operation) => ordinary(id, "LOOKUP", Array(JSON.number(7, 0), JSON.number(operation, 0)))
                lines: applyLines(1, 9, blob)
                // Partial receptions start no transaction and leave no effect.
                partial: serveSession(Array(Arrays.slice(lines, 0, lines.size() - 1)), false)
                Assertions.require(partial.clean && (partial.replies.size() == 1))
                premature: serveSession(Array(Arrays.concat(Arrays.slice(lines, 0, 10), Arrays.slice(lines, lines.size() - 1, lines.size()))), false)
                Assertions.require(!premature.clean && (premature.replies.size() == 2))
                Assertions.require(Protocol.decodeRejection(premature.replies[1], 7, null) == "MALFORMED_FRAME")
                counted: rows()
                Assertions.require((counted[0] == 0) && (counted[1] == 0))
                // The complete request commits; its acknowledgement is discarded
                // unread, as when it is lost on the way to the client.
                serveSession(Array(lines, close(2)), false)
                // A later session over the verified identity reconciles from the
                // durable receipt: the same request replays and has one effect.
                reconciled: serveSession(Array(lookup(1, 9), applyLines(2, 9, blob), applyLines(3, 9, text), close(4)), false)
                Assertions.require(reconciled.clean && (reconciled.replies.size() == 5))
                Assertions.require(Protocol.string(okMembers(reconciled.replies[1], 1, "LOOKUP")["outcome"]) == "FOUND")
                Assertions.require(outcome(reconciled.replies[2], 2) == "REPLAYED")
                Assertions.require(outcome(reconciled.replies[3], 3) == "CONFLICT")
                textual: serveSession(Array(applyLines(1, 10, text), close(2)), false)
                Assertions.require(outcome(textual.replies[1], 1) == "APPLIED")
                counted = rows()
                Assertions.require((counted[0] == 2) && (counted[1] == (70000 + Encoding.UTF8.encode(text).size())))
                """);
    }

    @Test void streamedBindAndApplyOnceThroughRealHelper() throws Exception {
        success("""
                blob: Encoding.Latin1.encode("")
                n: 0
                while(n < 70000) { blob.add(n % 251); n = n + 1 }
                blob[0] = 1
                blob[69999] = 255
                text: "a\\u{0}\\u{E9}\\u{1F642}"
                while(Encoding.UTF8.encode(text).size() < 65537) { text = text + text }
                query: db.prepare("SELECT typeof(?1), length(?1), hex(substr(?1, 1, 1)) || hex(substr(?1, -1, 1)), typeof(?2), CAST(?2 AS BLOB) = CAST(?3 AS BLOB), length(CAST(?2 AS BLOB))")
                (() => {
                    query.bind(1, blob).bind(2, text).bind(3, text)
                    Assertions.require(query.step())
                    row: query.row()
                    Assertions.require((row[0] == "blob") && (row[1] == 70000) && (row[2] == "01FF"))
                    Assertions.require((row[3] == "text") && (row[4] == 1) && (row[5] == Encoding.UTF8.encode(text).size()))
                    Assertions.require(!query.step())
                }).ensure(() => query.close().value())
                // A large BIND followed by a small STEP on the same connection.
                Assertions.require(scalar("SELECT 42") == 42)
                exec("CREATE TABLE s(v)")
                Assertions.require(db.applyOnce(Array(3, 4), "INSERT INTO s VALUES (?1)", Array(blob)).status == "APPLIED")
                Assertions.require(db.applyOnce(Array(3, 4), "INSERT INTO s VALUES (?1)", Array(blob)).status == "REPLAYED")
                Assertions.require(db.applyOnce(Array(3, 5), "INSERT INTO s VALUES (?1)", Array(text)).status == "APPLIED")
                Assertions.require(scalar("SELECT count(*) FROM s") == 2)
                Assertions.require(scalar("SELECT count(*) FROM s WHERE typeof(v) = 'blob' AND substr(v, 1, 1) = x'01' AND substr(v, -1, 1) = x'FF' AND length(v) = 70000") == 1)
                Assertions.require(db.lookup(Array(3, 4)).status == "FOUND")
                """);
    }

    @Test void failedLargeBindIsKnownAndAnIndependentSessionReopens() throws Exception {
        success("""
                blob: Encoding.Latin1.encode("")
                n: 0
                while(n < 70000) { blob.add(n % 251); n = n + 1 }
                query: db.prepare("SELECT ?1")
                (() => {
                    // SQLITE_RANGE after the complete streamed request: a known failure.
                    failure: null
                    Error.handle(() => query.bind(2, blob), (error) => { failure = error })
                    Assertions.require(isKind(failure, SQLite.SQLiteError))
                    Assertions.require(!isKind(failure, SQLite.SQLiteUncertainError))
                    Assertions.require(failure.code == 25)
                }).ensure(() => query.close().value())
                // The known failure released admission; the connection still works.
                Assertions.require(scalar("SELECT 7") == 7)
                // An independent session (its own helper) over the same database.
                reopened: SQLite.openDurable(sqlite, "application.db")
                (() => {
                    again: reopened.prepare("SELECT length(?1)")
                    (() => {
                        again.bind(1, blob)
                        Assertions.require(again.step())
                        Assertions.require(again.column(0) == 70000)
                    }).ensure(() => again.close().value())
                }).ensure(() => reopened.close().value())
                """);
    }


    @Test void commitWithoutAcknowledgementRemainsUncertainAndIsNeverRetried() throws Exception {
        // A deterministic transport-loss model using production Protos modules.
        // Java only routes opaque producer output into stdin; it interprets no protocol or SQL.
        for (String name : List.of("IsolatedProtocol.protos", "IsolatedConnection.protos", "Resources.protos")) {
            Files.copy(distribution.resolve("protos/lib/sqlite/internal/" + name), work.resolve(name));
        }
        Result ready = program("""
                Protocol: import("./IsolatedProtocol.protos")
                Streams: import("std:io/process_streams")
                Protocol.writeMessage(Streams.stdoutWriter(process), 1, Protocol.encodeReady(7, 101, 202))
                """, false, false);
        assertEquals(0, ready.code(), ready.output());
        Path input = work.resolve("ready-then-eof.txt");
        Files.writeString(input, ready.output());
        Path source = work.resolve("lost-commit.protos");
        Files.writeString(source, PRELUDE + """
                Client: import("./IsolatedConnection.protos")
                releases: 0
                bridge: {
                    launch: (authority, name) => {
                        { session: 7; identityHi: 101; identityLo: 202; reader: process.stdin(); writer: process.stdout() }
                    }
                    owns: (session) => session == 7
                    release: (session) => { releases = releases + 1; 0 }
                }
                uncertain: SQLite.SQLiteUncertainError
                db: Client.open(bridge, {}, "application.db", {}, SQLite.SQLiteError, uncertain)
                observed: false
                Error.handle(() => db.commit(), (error) => {
                    Assertions.require(isKind(error, uncertain))
                    Assertions.require(error.operation == "commit")
                    Assertions.require(error.outcome == "UNCERTAIN")
                    Assertions.require(error.reason == "CONNECTION_LOST")
                    observed = true
                })
                Assertions.require(observed && (releases == 1))
                expectFailure(() => db.rollback())
                Assertions.require(releases == 1)
                """);
        Path log = work.resolve("lost-commit-output.txt");
        ProcessBuilder builder = new ProcessBuilder(distribution.resolve("bin/protos").toString(), source.toString())
                .directory(work.toFile()).redirectInput(input.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("PROTOS_HOME");
        Process client = builder.start();
        try {
            assertTrue(client.waitFor(60, TimeUnit.SECONDS), "FAIL: lost-acknowledgement model timed out");
            assertEquals(0, client.exitValue(), Files.readString(log));
        } finally {
            if (client.isAlive()) client.destroyForcibly();
        }
    }


    @Test void errorCategoriesKeepIdentityAndCleanupNeverReplacesPrimary() throws Exception {
        // Real Protos signal/handle selection over the delegation chain, the production
        // handleNotCancelled helper, and the production client closing over a lost channel
        // whose helper release also fails. Java only routes the READY frame into stdin.
        for (String name : List.of("IsolatedProtocol.protos", "IsolatedConnection.protos", "Resources.protos")) {
            Files.copy(distribution.resolve("protos/lib/sqlite/internal/" + name), work.resolve(name));
        }
        Result ready = program("""
                Protocol: import("./IsolatedProtocol.protos")
                Streams: import("std:io/process_streams")
                Protocol.writeMessage(Streams.stdoutWriter(process), 1, Protocol.encodeReady(7, 101, 202))
                """, false, false);
        assertEquals(0, ready.code(), ready.output());
        Path input = work.resolve("taxonomy-ready-then-eof.txt");
        Files.writeString(input, ready.output());
        Path source = work.resolve("error-taxonomy.protos");
        Files.writeString(source, PRELUDE + """
                Client: import("./IsolatedConnection.protos")
                SQLiteError: SQLite.SQLiteError
                Uncertain: SQLite.SQLiteUncertainError
                cancelled: Cancelled {}
                known: SQLiteError { operation: "probe"; code: 1 }
                unknown: Uncertain { operation: "probe"; code: null; outcome: "UNCERTAIN"; reason: "RECONCILE_REQUIRED" }
                generic: Error {}
                seen: null
                // Cancelled is selected as Cancelled, never by a SQLiteError handler.
                Cancelled.handle(() => {
                    SQLiteError.handle(() => cancelled.signal(), (error) => { Assertions.require(false) })
                }, (error) => { seen = error })
                Assertions.require(seen === cancelled)
                // SQLiteError keeps its identity and is not an uncertain outcome.
                seen = null
                SQLiteError.handle(() => known.signal(), (error) => { seen = error })
                Assertions.require(seen === known)
                seen = null
                Error.handle(() => {
                    Uncertain.handle(() => known.signal(), (error) => { Assertions.require(false) })
                }, (error) => { seen = error })
                Assertions.require(seen === known)
                // SQLiteUncertainError keeps its identity under both its own and its parent handler.
                seen = null
                Uncertain.handle(() => unknown.signal(), (error) => { seen = error })
                Assertions.require(seen === unknown)
                seen = null
                SQLiteError.handle(() => unknown.signal(), (error) => { seen = error })
                Assertions.require(seen === unknown)
                // A generic Error is never reinterpreted as a SQLiteError.
                seen = null
                Error.handle(() => {
                    SQLiteError.handle(() => generic.signal(), (error) => { Assertions.require(false) })
                }, (error) => { seen = error })
                Assertions.require(seen === generic)
                // The production helper handles every Error except Cancelled, which it re-signals unchanged.
                seen = null
                handled: null
                Cancelled.handle(() => {
                    Client.handleNotCancelled(() => cancelled.signal(), (error) => { handled = error })
                }, (error) => { seen = error })
                Assertions.require((seen === cancelled) && (handled === null))
                Client.handleNotCancelled(() => generic.signal(), (error) => { handled = error })
                Assertions.require(handled === generic)
                // Cleanup failure during close never replaces the primary lost-channel outcome.
                ReleaseFailure: Error {}
                releaseFailure: ReleaseFailure {}
                releases: 0
                bridge: {
                    launch: (authority, name) => {
                        { session: 7; identityHi: 101; identityLo: 202; reader: process.stdin(); writer: process.stdout() }
                    }
                    owns: (session) => session == 7
                    release: (session) => {
                        releases = releases + 1
                        releaseFailure.signal()
                    }
                }
                terminal: {
                    closeAllowed: () => true
                    resolved: (value) => (() => value).future()
                    failed: (error) => (() => error.signal()).future()
                }
                db: Client.open(bridge, {}, "application.db", terminal, SQLiteError, Uncertain)
                seen = null
                Error.handle(() => db.close().value(), (error) => { seen = error })
                Assertions.require(seen !== releaseFailure)
                Assertions.require(isKind(seen, Uncertain))
                Assertions.require((seen.operation == "CLOSE") && (seen.reason == "CONNECTION_LOST"))
                Assertions.require(releases == 1)
                print("ERROR_TAXONOMY_VERIFIED")
                """);
        Path log = work.resolve("error-taxonomy-output.txt");
        ProcessBuilder builder = new ProcessBuilder(distribution.resolve("bin/protos").toString(), source.toString())
                .directory(work.toFile()).redirectInput(input.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("PROTOS_HOME");
        Process client = builder.start();
        try {
            assertTrue(client.waitFor(60, TimeUnit.SECONDS), "FAIL: error taxonomy model timed out");
            assertEquals(0, client.exitValue(), Files.readString(log));
            assertTrue(Files.readString(log).contains("ERROR_TAXONOMY_VERIFIED"), Files.readString(log));
        } finally {
            if (client.isAlive()) client.destroyForcibly();
        }
    }

    @Test void anotherActorCannotUseCopiedGrantOrLiveSession() throws Exception {
        Files.writeString(work.resolve("worker.protos"), """
                SQLite: import("std:sqlite")
                construct: () => {
                    {
                        open: (grant) => {
                            denied: false
                            Error.handle(() => SQLite.openDurable(grant, "application.db"), (error) => { denied = true })
                            denied
                        }
                        use: (connection) => {
                            denied: false
                            Error.handle(() => connection.prepare("SELECT 1"), (error) => { denied = true })
                            denied
                        }
                    }
                }
                """);
        success("""
                worker: Actor.spawn("./worker.protos", "construct")
                (() => {
                    Assertions.require(worker.request("open", sqlite).value())
                    refused: false
                    Error.handle(() => { refused = worker.request("use", db).value() }, (error) => { refused = true })
                    Assertions.require(refused)
                    Assertions.require(scalar("SELECT 42") == 42)
                }).ensure(() => worker.stop())
                """);
    }

    @Test void parentMemoryAuthorityDoesNotImplicitlyAuthorizeDurableOpen() throws Exception {
        Result result = program("""
                db: SQLite.openMemory()
                db.close().value()
                expectFailure(() => SQLite.openDurable({}, "application.db"))
                """, false, true);
        assertEquals(0, result.code(), result.output());
        assertFalse(Files.exists(databaseDirectory.resolve("application.db")));
    }

    @Test void sqliteGrantIsRejectedForEvalEntry() throws Exception {
        Result result = command(List.of(distribution.resolve("bin/protos").toString(),
                "--allow-sqlite=" + databaseDirectory, "-e", "Error().signal()"));
        assertNotEquals(0, result.code());
        assertTrue(result.output().contains("direct file execution"), result.output());
    }


    @Test void maximumNulTextDoesNotAcquireAnEscapedJsonSizeLimit() throws Exception {
        success("""
                query: db.prepare("SELECT CAST(zeroblob(16777216) AS TEXT)")
                text: null
                (() => {
                    print("PLAT074_STAGE step:started")
                    Assertions.require(query.step())
                    print("PLAT074_STAGE step:completed")
                    text = query.column(0)
                    Assertions.require(String.recognizes(text))
                    octets: Encoding.UTF8.encode(text)
                    Assertions.require(octets.size() == 16777216)
                    Assertions.require((octets[0] == 0) && (octets[8388608] == 0) && (octets[16777215] == 0))
                    print("PLAT074_STAGE value:received")
                    Assertions.require(!query.step())
                }).ensure(() => query.close().value())
                // The streamed BIND keeps TEXT (never BLOB) and every NUL: SQLite
                // compares all 16 MiB octets after reinterpreting them as a BLOB.
                query = db.prepare("SELECT typeof(?1), length(CAST(?1 AS BLOB)), CAST(?1 AS BLOB) = zeroblob(16777216)")
                (() => {
                    print("PLAT074_STAGE bind:started")
                    query.bind(1, text)
                    print("PLAT074_STAGE bind:completed")
                    Assertions.require(query.step())
                    print("PLAT074_STAGE check:completed")
                    Assertions.require(query.column(0) == "text")
                    Assertions.require(query.column(1) == 16777216)
                    Assertions.require(query.column(2) == 1)
                    Assertions.require(!query.step())
                }).ensure(() => query.close().value())
                Assertions.require(scalar("SELECT 42") == 42)
                """);
    }


    @Test void semanticProcessTerminationRetiresHelperWhileHostJvmRemainsAlive() throws Exception {
        managedLifecycle("managed-process");
    }

    @Test void actorTerminationRetiresHelperWhileProcessAndHostRemainAlive() throws Exception {
        managedLifecycle("actor-owner");
    }

    private void managedLifecycle(String mode) throws Exception {
        Path source = work.resolve("managed.protos");
        Files.writeString(source, "SQLite: import(\"std:sqlite\")\ndb: SQLite.openDurable(sqlite, \"application.db\")\n");
        Path log = work.resolve("managed-output.txt");
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow",
                "-cp", classPath, ProtosIsolatedSqliteIntegrationTest.class.getName(),
                mode, distribution.toString(), databaseDirectory.toString(), source.toString())
                .directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("PROTOS_HOME", distribution.toString());
        Process probe = builder.start();
        try {
            assertTrue(probe.waitFor(90, TimeUnit.SECONDS), "FAIL: managed Process lifecycle probe timed out");
            assertEquals(0, probe.exitValue(), Files.readString(log));
            assertTrue(Files.readString(log).contains("MANAGED_PROCESS_RETIRED_HOST_ALIVE"));
        } finally {
            probe.descendants().forEach(ProcessHandle::destroyForcibly);
            if (probe.isAlive()) probe.destroyForcibly();
        }
    }

    /** Test-only driver: uses the existing standalone bootstrap and canonical direct-file entry. */
    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !(args[0].equals("managed-process") || args[0].equals("actor-owner")
                || args[0].equals("lost-commit") || LAUNCH_MODES.contains(args[0]))) {
            throw new IllegalArgumentException("expected managed-process, actor-owner, lost-commit or a launch mode, installation, database-directory, source");
        }
        Path installation = Path.of(args[1]);
        Path directory = Path.of(args[2]);
        Path source = Path.of(args[3]);
        Path core = installation.resolve("protos/lib/core");
        try (var host = ProtosPolyglotRuntimeHost.open();
                var resolver = new ProtosDirectFileModuleResolver(source, Files.readString(source),
                        new ProtosStandardLibraryModuleResolver(core.getParent()))) {
            var bootstrap = ProtosStandaloneHostedExecution.bootstrapProcess(core, resolver, List.of(),
                    null, null, null,
                    ProtosWorkspacePackageApplicationExecution.NetworkGrant.NONE,
                    ProtosStandaloneHostedExecution.ResolverGrant.NONE,
                    ProtosStandaloneHostedExecution.EntropyGrant.NONE,
                    ProtosStandaloneHostedExecution.ClockGrant.NONE,
                    ProtosStandaloneHostedExecution.FilesystemGrant.NONE,
                    ProtosIsolatedSqliteHostFacility.Grant.directory(directory, installation), host);
            Throwable primary = null;
            try {
                ProtosStandaloneHostedExecution.bindProcess(bootstrap, host,
                        java.io.InputStream.nullInputStream(), System.out, System.err);
                if (LAUNCH_MODES.contains(args[0])) {
                    driveLaunch(args[0], host, bootstrap, resolver);
                    return;
                }
                if (args[0].equals("lost-commit")) {
                    bootstrap.activation().createCurrentLocalSlotForRuntime("realBridge",
                            ProtosIsolatedSqliteHostFacility.createBridge());
                    bootstrap.activation().createCurrentLocalSlotForRuntime("terminal",
                            ProtosHttpAsyncFacility.createTerminalFacility());
                }
                var outcome = ProtosStandaloneHostedExecution.executeDirectFile(resolver, bootstrap.activation());
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(),
                        () -> "GUEST_FAILED " + args[0] + "\n" + describeOutcome(outcome));
                if (args[0].equals("lost-commit")) {
                    assertEquals(0, host.isolatedSqliteForRuntime().liveHelperCountForTesting());
                    bootstrap.process().requestTerminationForRuntime();
                    bootstrap.process().awaitTerminationForRuntime();
                    System.out.println("REAL_COMMIT_ACK_LOST_REOPEN_VERIFIED");
                    return;
                }
                assertEquals(1, host.isolatedSqliteForRuntime().liveHelperCountForTesting());
                List<ProcessHandle> helpers = ProcessHandle.current().descendants().filter(handle ->
                        handle.info().commandLine().orElse("").contains("isolated-helper.protos")).toList();
                assertEquals(1, helpers.size());
                if (args[0].equals("actor-owner")) {
                    bootstrap.process().rootActorForRuntime().requestTerminationForRuntime();
                    bootstrap.process().awaitRootActorTerminationForRuntime();
                    assertEquals(com.guillermomolina.protos.runtime.ProtosProcessRuntime.LifecycleState.RUNNING,
                            bootstrap.process().lifecycleState());
                    assertEquals(0, host.isolatedSqliteForRuntime().liveHelperCountForTesting());
                    for (ProcessHandle helper : helpers) {
                        helper.onExit().get(15, TimeUnit.SECONDS);
                        assertFalse(helper.isAlive());
                    }
                }
                bootstrap.process().requestTerminationForRuntime();
                bootstrap.process().awaitTerminationForRuntime();
                assertEquals(0, host.isolatedSqliteForRuntime().liveHelperCountForTesting());
                for (ProcessHandle helper : helpers) {
                    helper.onExit().get(15, TimeUnit.SECONDS);
                    assertFalse(helper.isAlive());
                }
                // The host remains open; another Process gains no process-creation permission.
                var other = ProtosStandaloneHostedExecution.bootstrapProcess(core, resolver, List.of(),
                        null, null, null,
                        ProtosWorkspacePackageApplicationExecution.NetworkGrant.NONE,
                        ProtosStandaloneHostedExecution.ResolverGrant.NONE,
                        ProtosStandaloneHostedExecution.EntropyGrant.NONE,
                        ProtosStandaloneHostedExecution.ClockGrant.NONE,
                        ProtosStandaloneHostedExecution.FilesystemGrant.NONE,
                        ProtosIsolatedSqliteHostFacility.Grant.NONE, host);
                try {
                    var otherContext = ProtosStandaloneHostedExecution.bindProcess(other, host,
                            java.io.InputStream.nullInputStream(), System.out, System.err);
                    assertFalse(host.isolatedSqliteForRuntime().authorizedForRuntime(other.process()));
                    other.activation().createCurrentLocalSlotForRuntime("processCreationAllowed",
                            com.guillermomolina.protos.runtime.ProtosClosureValue.nativeClosure((activation, supplied) ->
                                    ProtosLanguageContext.current().env().isCreateProcessAllowed()));
                    var key = com.guillermomolina.protos.runtime.modules.ProtosModuleKey.source("permission-probe");
                    var observed = otherContext.executeModuleSource(
                            ProtosModuleSource.fromCharacters(key, "processCreationAllowed()"), other.activation());
                    assertEquals(ProtosExecutionOutcome.State.COMPLETED, observed.state());
                    assertEquals(false, observed.value());
                } finally {
                    other.process().requestTerminationForRuntime();
                    other.process().awaitTerminationForRuntime();
                }
                System.out.println("MANAGED_PROCESS_RETIRED_HOST_ALIVE");
            } catch (Throwable failure) {
                primary = failure;
                throw failure;
            } finally {
                // A failed assertion must not leave this Process Context active when the host
                // closes; the primary failure keeps propagating after this retirement.
                try {
                    if (bootstrap.process().lifecycleState()
                            != com.guillermomolina.protos.runtime.ProtosProcessRuntime.LifecycleState.TERMINATED) {
                        bootstrap.process().requestTerminationForRuntime();
                        bootstrap.process().awaitTerminationForRuntime();
                    }
                } catch (RuntimeException | Error secondary) {
                    if (primary == null) throw secondary;
                    // A secondary Context-retirement failure never replaces the primary one.
                    System.err.println("SECONDARY_CONTEXT_CLOSE_FAILURE (primary preserved): " + secondary);
                    primary.addSuppressed(secondary);
                }
            }
        }
    }


    @Test void refusedFirstCreationCannotLaterRecreateADisappearedExistingFile() throws Exception {
        Path existing = databaseDirectory.resolve("application.db");
        Files.createFile(existing);
        Result refused = program("expectFailure(() => SQLite.openDurable(sqlite, \"application.db\"))", true, false);
        assertEquals(0, refused.code(), refused.output());
        assertTrue(Files.readString(identityDirectory.resolve("application.db.identity")).endsWith("provision=VERIFY\n"));
        Files.delete(existing);
        Result missing = program("expectFailure(() => SQLite.openDurable(sqlite, \"application.db\"))", true, false);
        assertEquals(0, missing.code(), missing.output());
        assertFalse(Files.exists(existing));
    }

    @Test void wireValuesRejectObjectsAndPreserveLeadingTextBom() throws Exception {
        protocolProgram("""
                value: "\\u{FEFF}\\u{0}caf\\u{E9}"
                Assertions.require(Protocol.decodeValue(Protocol.encodeValue(value)) == value)
                expectFailure(() => Protocol.encodeValue({ address: 42 }))
                expectFailure(() => Protocol.encodeValue(Array(1, 2)))
                """);
    }


    @Test void realHelperCommitPersistsEvenWhenItsAcknowledgementIsLostToClient() throws Exception {
        for (String name : List.of("IsolatedProtocol.protos", "IsolatedConnection.protos", "Resources.protos")) {
            Files.copy(distribution.resolve("protos/lib/sqlite/internal/" + name), work.resolve(name));
        }
        // Test-only observation fault in Protos. Read the complete real COMMIT
        // response, then lose it before the production client can observe it.
        Files.writeString(work.resolve("IsolatedProtocol.protos"), """
                // The first successful COMMIT reply: an OK value carrying both
                // `code` and `autocommit` true. BEGIN answers autocommit false.
                commitAcknowledgementLost: false
                readMessage = (reader, seq) => {
                    text: _isolatedProtocol.readMessage(reader, seq)
                    if((text !== null) && !commitAcknowledgementLost) {
                        members: JSON.parse(text).value
                        if(members.containsKey("value")) {
                            value: members["value"]
                            if(value.kind === "object") {
                                if(value.value.containsKey("code") && value.value.containsKey("autocommit")) {
                                    if(value.value["autocommit"].kind === "boolean") {
                                        if(value.value["autocommit"].value) {
                                            commitAcknowledgementLost = true
                                            text = null
                                        }
                                    }
                                }
                            }
                        }
                    }
                    text
                }
                """, java.nio.file.StandardOpenOption.APPEND);
        Path source = work.resolve("lost-real-commit.protos");
        String open = DATABASE.replace("db: SQLite.openDurable(sqlite, \"application.db\")",
                "Client: import(\"./IsolatedConnection.protos\")\ndb: Client.open(realBridge, sqlite, \"application.db\", terminal, SQLite.SQLiteError, SQLite.SQLiteUncertainError)");
        Files.writeString(source, PRELUDE + open + """
                // The hosted child has no CLI print facility; a failure is located by
                // the guest frame that describeOutcome reports.
                exec("CREATE TABLE sample(n)")
                db.begin()
                statement: db.prepare("INSERT INTO sample VALUES (?1)")
                statement.bind(1, 42)
                Assertions.require(!statement.step())
                statement.close().value()
                uncertain: false
                Error.handle(() => db.commit(), (error) => {
                    Assertions.require(isKind(error, SQLite.SQLiteUncertainError))
                    Assertions.require(error.operation == "commit")
                    Assertions.require(error.outcome == "UNCERTAIN")
                    uncertain = true
                })
                Assertions.require(uncertain)
                db.close().value()
                reopened: SQLite.openDurable(sqlite, "application.db")
                (() => {
                    query: reopened.prepare("SELECT n FROM sample")
                    (() => {
                        Assertions.require(query.step())
                        Assertions.require(query.column(0) == 42)
                        Assertions.require(!query.step())
                    }).ensure(() => query.close().value())
                }).ensure(() => reopened.close().value())
                """);
        Path log = work.resolve("lost-real-commit-output.txt");
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow",
                "-cp", classPath, ProtosIsolatedSqliteIntegrationTest.class.getName(),
                "lost-commit", distribution.toString(), databaseDirectory.toString(), source.toString())
                .directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("PROTOS_HOME", distribution.toString());
        Process probe = builder.start();
        try {
            assertTrue(probe.waitFor(90, TimeUnit.SECONDS), "FAIL: real-helper lost-COMMIT probe timed out");
            assertEquals(0, probe.exitValue(), "exit=" + probe.exitValue() + "\n" + Files.readString(log));
            assertTrue(Files.readString(log).contains("REAL_COMMIT_ACK_LOST_REOPEN_VERIFIED"));
        } finally {
            probe.descendants().forEach(ProcessHandle::destroyForcibly);
            if (probe.isAlive()) probe.destroyForcibly();
        }
        // A second, independent JVM reopens the same database: exactly one committed row,
        // so the lost acknowledgement caused no automatic retry.
        success("""
                Assertions.require(scalar("SELECT count(*) FROM sample") == 1)
                Assertions.require(scalar("SELECT n FROM sample") == 42)
                """);
    }


    @Test void sameActorTasksRejectConcurrentStepBeforeAnyIpc() throws Exception { concurrentAdmission("STEP"); }
    @Test void sameActorTasksRejectConcurrentCommitBeforeAnyIpc() throws Exception { concurrentAdmission("COMMIT"); }
    @Test void sameActorTasksRejectConcurrentCloseBeforeAnyIpc() throws Exception { concurrentAdmission("CLOSE"); }
    @Test void cancelledPendingStepRequiresReconciliation() throws Exception { concurrentAdmission("CANCEL_STEP"); }
    @Test void cancelledPendingCommitRequiresReconciliation() throws Exception { concurrentAdmission("CANCEL_COMMIT"); }
    @Test void cancelledPendingCloseCannotPublishClosedSuccess() throws Exception { concurrentAdmission("CANCEL_CLOSE"); }
    @Test void partiallyReceivedStreamedRowKeepsConnectionBusyAndPublishesOnlyWhenComplete() throws Exception {
        concurrentAdmission("STREAM_STEP");
    }
    @Test void cancelledPartiallyReceivedStreamedRowRequiresReconciliation() throws Exception {
        concurrentAdmission("CANCEL_STREAM_STEP");
    }
    @Test void pendingStreamedBindKeepsConnectionBusyWithoutConsumingIds() throws Exception {
        concurrentAdmission("BIND");
    }
    @Test void cancelledPartiallySentStreamedBindRequiresReconciliation() throws Exception {
        concurrentAdmission("CANCEL_BIND");
    }

    private void concurrentAdmission(String selectedMode) throws Exception {
        // Actor-local protocol model: all scheduling, gates, frames, counters and
        // assertions are Protos. Java only releases an ordinary stdin gate after
        // the cross-Task busy assertions have completed. No SQL engine is mocked in Java.
        for (String name : List.of("IsolatedProtocol.protos", "IsolatedConnection.protos", "Resources.protos")) {
            Files.copy(distribution.resolve("protos/lib/sqlite/internal/" + name), work.resolve(name));
        }
        Files.writeString(work.resolve("admission-monitor.protos"), """
                Assertions: import("std:test/assertions")
                construct: (output) => {
                    out: TextWriter(output, Encoding.UTF8)
                    report: (line) => {
                        out.writeLine(line).value()
                        out.flush().value()
                    }
                    {
                        // Ordered diagnostics from the owner; stage names only.
                        stage: (name) => report("ADMISSION_STAGE " + name)
                        pending: (owner) => {
                            report("ADMISSION_STAGE owner-suspended-at-gate")
                            report("ADMISSION_STAGE probeBusy-sent")
                            probed: false
                            (() => {
                                Assertions.require(owner.request("probeBusy").value())
                                probed = true
                            }).ensure(() => {
                                if(!probed) { report("ADMISSION_STAGE probeBusy-failed") }
                            })
                            report("ADMISSION_STAGE probeBusy-returned")
                            report("BUSY_ASSERTIONS_COMPLETE")
                        }
                    }
                }
                """);
        Files.writeString(work.resolve("admission-owner.protos"), """
                Assertions: import("std:test/assertions")
                SQLite: import("std:sqlite")
                Client: import("./IsolatedConnection.protos")
                Protocol: import("./IsolatedProtocol.protos")
                JSON: import("std:json/json")
                Arrays: import("std:collections/array")
                // Handler-style category test over the delegation chain. Error
                // prototypes define no `recognizes`; sending it signals SlotNotFound.
                isKind: (error, prototype) => {
                    found: false
                    current: error
                    while(!found && (current !== Object)) {
                        found = current === prototype
                        current = current.parent()
                    }
                    found
                }
                construct: (input, monitor) => {
                    mode: null
                    cancelMode: false
                    streamMode: false
                    target: null
                    db: null
                    statement: null
                    operation: null
                    frames: Array()
                    frameIndex: 0
                    offset: 0
                    blockAt: null
                    passedGate: false
                    writes: 0
                    // Write call that suspends at the stdin gate (CANCEL_BIND only).
                    writeCalls: 0
                    writeBlockAt: null
                    releases: 0
                    alive: true
                    // Explicit test Tasks manufacture Futures for structural
                    // ByteReadable/ByteWritable mocks, using the normal runtime.
                    terminal: {
                        closeAllowed: () => true
                        resolved: (value) => (() => value).future()
                        failed: (error) => (() => error.signal()).future()
                    }
                    sink: {
                        writeLine: (line) => {
                            frames = Arrays.append(frames, Encoding.UTF8.encode(line + "\\n"))
                            { value: () => null }
                        }
                        flush: () => { { value: () => null } }
                    }
                    enqueue: (sequence, text) => Protocol.writeMessage(sink, sequence, text)
                    reader: {
                        read: (maximum) => (() => {
                            if((frameIndex == blockAt) && !passedGate) {
                                monitor.send("pending", Actor.current())
                                input.read(1).value()
                                passedGate = true
                            }
                            bytes: null
                            if(frameIndex < frames.size()) {
                                source: frames[frameIndex]
                                bytes = Encoding.UTF8.encode("")
                                while((bytes.size() < maximum) && (offset < source.size())) {
                                    bytes.add(source[offset]); offset = offset + 1
                                }
                                if(offset == source.size()) { frameIndex = frameIndex + 1; offset = 0 }
                            }
                            bytes
                        }).future()
                    }
                    writer: {
                        write: (bytes) => {
                            writes = writes + bytes.size()
                            writeCalls = writeCalls + 1
                            if((writeCalls == writeBlockAt) && !passedGate) {
                                (() => {
                                    monitor.send("pending", Actor.current())
                                    input.read(1).value()
                                    passedGate = true
                                    writer
                                }).future()
                            } else {
                                terminal.resolved(writer)
                            }
                        }
                        flush: () => terminal.resolved(writer)
                    }
                    bridge: {
                        launch: (authority, name) => {
                            { session: 7; identityHi: 101; identityLo: 202; reader: reader; writer: writer }
                        }
                        owns: (session) => alive && (session == 7)
                        release: (session) => {
                            releases = releases + 1
                            alive = false
                            status: 0
                            if(cancelMode) { status = null }
                            status
                        }
                    }
                    expectBusy: (body) => {
                        rejected: false
                        Error.handle(body, (error) => {
                            Assertions.require(isKind(error, SQLite.SQLiteError))
                            Assertions.require(error.reason == "CONNECTION_BUSY")
                            rejected = true
                        })
                        Assertions.require(rejected)
                    }
                    expectReconcile: (body) => {
                        rejected: false
                        Error.handle(body, (error) => {
                            Assertions.require(isKind(error, SQLite.SQLiteUncertainError))
                            Assertions.require(error.reason == "RECONCILE_REQUIRED")
                            rejected = true
                        })
                        Assertions.require(rejected)
                    }
                    behavior: {
                        start: (requested) => {
                            mode = requested
                            cancelMode = (mode == "CANCEL_STEP") || (mode == "CANCEL_COMMIT") || (mode == "CANCEL_CLOSE") ||
                                (mode == "CANCEL_STREAM_STEP") || (mode == "CANCEL_BIND")
                            streamMode = (mode == "STREAM_STEP") || (mode == "CANCEL_STREAM_STEP")
                            target = mode
                            if((mode == "CANCEL_STEP") || streamMode) { target = "STEP" }
                            if(mode == "CANCEL_COMMIT") { target = "COMMIT" }
                            if(mode == "CANCEL_CLOSE") { target = "CLOSE" }
                            if(mode == "CANCEL_BIND") { target = "BIND" }
                            // A TEXT/BLOB value beyond the internal threshold: a streamed request.
                            bindPayload: Encoding.Latin1.encode("")
                            octet: 0
                            while(octet < 100000) { bindPayload.add(octet % 256); octet = octet + 1 }
                            enqueue(1, Protocol.encodeReady(7, 101, 202))
                            next: 2
                            if((target == "STEP") || (target == "BIND")) {
                                enqueue(2, Protocol.encodeResponse(7, 1, "PREPARE",
                                    Array(JSON.number(0, 0), JSON.number(1, 0), JSON.number(0, 0))))
                                next = 3
                            }
                            blockAt = frames.size()
                            if(target == "STEP") {
                                if(streamMode) {
                                    payload: Encoding.Latin1.encode("")
                                    n: 0
                                    while(n < 5000) { payload.add(n % 256); n = n + 1 }
                                    Protocol.writeRow(sink, next, 7, next - 1, Array(Protocol.rowColumn(null, false, payload)))
                                    // Suspend after the header and the first of three fragments.
                                    blockAt = blockAt + 2
                                } else {
                                    enqueue(next, Protocol.encodeResponse(7, next - 1, "STEP", Array(JSON.number(101, 0), JSON.nullValue())))
                                }
                            }
                            if(target == "BIND") {
                                enqueue(next, Protocol.encodeResponse(7, next - 1, "BIND", Array(JSON.number(0, 0))))
                                if(cancelMode) {
                                    // Suspend inside the request: PREPARE is write 1, the
                                    // header write 2, the first fragment write 3.
                                    blockAt = null
                                    writeBlockAt = 4
                                }
                            }
                            if(target == "COMMIT") {
                                enqueue(next, Protocol.encodeResponse(7, next - 1, "COMMIT", Array(JSON.number(0, 0), JSON.boolean(true))))
                            }
                            if(target == "CLOSE") {
                                enqueue(next, Protocol.encodeResponse(7, next - 1, "CLOSE", Array(JSON.number(0, 0))))
                            } else {
                                enqueue(next + 1, Protocol.encodeResponse(7, next, "AUTOCOMMIT", Array(JSON.boolean(true))))
                            }
                            db = Client.open(bridge, {}, "application.db", terminal, SQLite.SQLiteError, SQLite.SQLiteUncertainError)
                            monitor.send("stage", "opened")
                            if(target == "STEP") { statement = db.prepare("SELECT 1") }
                            if(target == "BIND") { statement = db.prepare("SELECT ?1") }
                            monitor.send("stage", "operation-scheduled")
                            operation = (() => {
                                monitor.send("stage", "operation-entered")
                                if(target == "STEP") { statement.step() }
                                if(target == "BIND") { statement.bind(1, bindPayload) }
                                if(target == "COMMIT") { db.commit() }
                                if(target == "CLOSE") { db.close().value() }
                            }).future()
                            null
                        }
                        probeBusy: () => {
                            before: writes
                            expectBusy(() => db.prepare("SELECT 2"))
                            expectBusy(() => db.commit())
                            expectBusy(() => db.close().value())
                            if(statement !== null) {
                                expectBusy(() => statement.step())
                                expectBusy(() => statement.reset())
                                expectBusy(() => statement.close().value())
                            }
                            Assertions.require(writes == before)
                            if(cancelMode) {
                                operation.cancel()
                                cancelled: false
                                Error.handle(() => operation.value(), (error) => {
                                    Assertions.require(isKind(error, Cancelled))
                                    cancelled = true
                                })
                                Assertions.require(cancelled)
                                // The stdin gate is still physically blocked: Java
                                // releases it only after this method has returned.
                                expectReconcile(() => db.prepare("SELECT 2"))
                                expectReconcile(() => db.commit())
                                Assertions.require((writes == before) && (releases == 1))
                            }
                            true
                        }
                        finish: () => {
                            if(cancelMode) {
                                cancelled: false
                                Error.handle(() => operation.value(), (error) => {
                                    Assertions.require(isKind(error, Cancelled))
                                    cancelled = true
                                })
                                Assertions.require(cancelled)
                                before: writes
                                expectReconcile(() => db.prepare("SELECT 2"))
                                expectReconcile(() => db.commit())
                                failedClose: false
                                Error.handle(() => db.close().value(), (error) => { failedClose = true })
                                Assertions.require(failedClose)
                                Assertions.require((writes == before) && (releases == 1))
                            } else {
                                operation.value()
                                if(streamMode) {
                                    // Published once, completely, after the terminator.
                                    value: statement.column(0)
                                    Assertions.require(!String.recognizes(value) && (value.size() == 5000))
                                    n: 0
                                    while(n < 5000) { Assertions.require(value[n] == (n % 256)); n = n + 1 }
                                }
                                if(target == "CLOSE") {
                                    Assertions.require(releases == 1)
                                    Assertions.require(db.close().value() === db)
                                } else {
                                    // Exact next correlation proves busy rejections
                                    // neither sent frames nor consumed request ids.
                                    Assertions.require(!db.inTransaction())
                                }
                            }
                            true
                        }
                    }
                    behavior
                }
                """);
        Path source = work.resolve("concurrent-admission.protos");
        Files.writeString(source, PRELUDE + """
                monitor: Actor.spawn("./admission-monitor.protos", "construct", process.stdout())
                owner: Actor.spawn("./admission-owner.protos", "construct", process.stdin(), monitor)
                (() => {
                    started: owner.request("start", "%s")
                    started.value()
                    Assertions.require(owner.request("finish").value())
                }).ensure(() => { owner.stop(); monitor.stop() })
                """.formatted(selectedMode));
        ProcessBuilder builder = new ProcessBuilder(distribution.resolve("bin/protos").toString(), source.toString())
                .directory(work.toFile()).redirectError(work.resolve("admission-errors.txt").toFile());
        builder.environment().remove("PROTOS_HOME");
        long startNanos = System.nanoTime();
        Process client = builder.start();
        childStarted(client, "CLI");
        try (BufferedReader output = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            // Stage lines precede the marker on the same ordered monitor stream.
            List<String> stages = new ArrayList<>();
            String signaled;
            try {
                signaled = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        String line;
                        while ((line = output.readLine()) != null && line.startsWith("ADMISSION_STAGE ")) {
                            synchronized (stages) { stages.add(line.substring("ADMISSION_STAGE ".length())); }
                        }
                        return line;
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }).get(60, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException timeout) {
                // The reading thread still holds the reader lock inside readLine(); closing the
                // reader first would block forever. Report, then end the child so readLine() returns.
                List<String> reached;
                synchronized (stages) { reached = List.copyOf(stages); }
                progress("PLAT074_ADMISSION_TIMEOUT scenario=" + selectedMode + " stages=" + reached);
                childTimedOut(client, startNanos, work.resolve("admission-errors.txt"));
                client.descendants().forEach(ProcessHandle::destroyForcibly);
                client.destroyForcibly();
                throw timeout;
            }
            if (!"BUSY_ASSERTIONS_COMPLETE".equals(signaled)) {
                // Keep the guest's original Actor/Task error rather than only EOF/null.
                boolean ended = client.waitFor(10, TimeUnit.SECONDS);
                List<String> reached;
                synchronized (stages) { reached = List.copyOf(stages); }
                String errors = Files.readString(work.resolve("admission-errors.txt"));
                if (errors.length() > 16384) errors = errors.substring(0, 16384) + "\n[stderr truncated]";
                org.junit.jupiter.api.Assertions.fail("scenario " + selectedMode
                        + ": expected BUSY_ASSERTIONS_COMPLETE but was " + signaled
                        + "; exit=" + (ended ? String.valueOf(client.exitValue()) : "running")
                        + "\nlast stage: " + (reached.isEmpty() ? "none" : reached.getLast())
                        + "\nstages: " + reached
                        + "\nprobeBusy sent: " + reached.contains("probeBusy-sent")
                        + "\nowner suspended at gate: " + reached.contains("owner-suspended-at-gate")
                        + "\nguest stderr:\n" + errors);
            }
            client.getOutputStream().write(1);
            client.getOutputStream().flush();
            client.getOutputStream().close();
            assertTrue(client.waitFor(60, TimeUnit.SECONDS), "FAIL: Actor-local admission probe timed out");
            assertEquals(0, client.exitValue(), "scenario " + selectedMode + "\n"
                    + Files.readString(work.resolve("admission-errors.txt")));
            childEnded(client, startNanos);
        } finally {
            if (client.isAlive()) client.destroyForcibly();
        }
    }


    @Test void nativeFailureReplyToCommitRemainsUncertainAndRetiresHelper() throws Exception {
        // A COMMIT failure escaping as a native code establishes no transaction
        // result: it must never be reported as a known SQLiteError.
        for (String name : List.of("IsolatedProtocol.protos", "IsolatedConnection.protos", "Resources.protos")) {
            Files.copy(distribution.resolve("protos/lib/sqlite/internal/" + name), work.resolve(name));
        }
        Result replies = program("""
                Protocol: import("./IsolatedProtocol.protos")
                Streams: import("std:io/process_streams")
                out: Streams.stdoutWriter(process)
                Protocol.writeMessage(out, 1, Protocol.encodeReady(7, 101, 202))
                Protocol.writeMessage(out, 2, Protocol.encodeSqliteFailure(7, 1, 10))
                """, false, false);
        assertEquals(0, replies.code(), replies.output());
        Path input = work.resolve("ready-then-commit-failure.txt");
        Files.writeString(input, replies.output());
        Path source = work.resolve("commit-native-failure.protos");
        Files.writeString(source, PRELUDE + """
                Client: import("./IsolatedConnection.protos")
                releases: 0
                bridge: {
                    launch: (authority, name) => {
                        { session: 7; identityHi: 101; identityLo: 202; reader: process.stdin(); writer: process.stdout() }
                    }
                    owns: (session) => session == 7
                    release: (session) => { releases = releases + 1; 0 }
                }
                uncertain: SQLite.SQLiteUncertainError
                db: Client.open(bridge, {}, "application.db", {}, SQLite.SQLiteError, uncertain)
                observed: false
                Error.handle(() => db.commit(), (error) => {
                    Assertions.require(isKind(error, uncertain))
                    Assertions.require((error.operation == "commit") && (error.code == 10))
                    Assertions.require(error.reason == "RECONCILE_REQUIRED")
                    observed = true
                })
                Assertions.require(observed && (releases == 1))
                reconciled: false
                Error.handle(() => db.rollback(), (error) => {
                    Assertions.require(isKind(error, uncertain) && (error.reason == "RECONCILE_REQUIRED"))
                    reconciled = true
                })
                Assertions.require(reconciled && (releases == 1))
                """);
        Path log = work.resolve("commit-native-failure-output.txt");
        ProcessBuilder builder = new ProcessBuilder(distribution.resolve("bin/protos").toString(), source.toString())
                .directory(work.toFile()).redirectInput(input.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("PROTOS_HOME");
        Process client = builder.start();
        try {
            assertTrue(client.waitFor(60, TimeUnit.SECONDS), "FAIL: COMMIT native-failure model timed out");
            assertEquals(0, client.exitValue(), Files.readString(log));
        } finally {
            if (client.isAlive()) client.destroyForcibly();
        }
    }


    @Test void confirmedReceiptQueryFailurePreservesSqliteCodeAndReleasesAdmission() throws Exception {
        success("""
                exec("CREATE TABLE protos_operation_receipts(unrelated)")
                rejected: false
                Error.handle(() => db.lookup(Array(1, 2)), (error) => {
                    Assertions.require(isKind(error, SQLite.SQLiteError))
                    Assertions.require(!isKind(error, SQLite.SQLiteUncertainError))
                    Assertions.require((error.operation == "lookup") && (error.code == 1))
                    rejected = true
                })
                Assertions.require(rejected)
                Assertions.require(scalar("SELECT 42") == 42)
                """);
    }


    @Test void manifestWritableByOtherPrincipalsIsRejectedBeforeCreation() throws Exception {
        Path manifest = identityDirectory.resolve("application.db.identity");
        Files.setPosixFilePermissions(manifest, PosixFilePermissions.fromString("rw-rw-rw-"));
        Result result = program("expectFailure(() => SQLite.openDurable(sqlite, \"application.db\"))", true, false);
        assertEquals(0, result.code(), result.output());
        assertFalse(Files.exists(databaseDirectory.resolve("application.db")));
        assertTrue(Files.readString(manifest).endsWith("provision=CREATE_NEW\n"));
    }


    // ---- Launch cancellation and launch-time termination (real CLI host, real helper) ----

    private static final List<String> LAUNCH_MODES = List.of(
            "cancel-submitting", "cancel-before-start", "cancel-after-start",
            "terminate-actor-launch", "terminate-process-launch");

    /**
     * Opens A, has the second launch (B) cancelled at a controlled stage, keeps using A and then
     * opens C through the same Process authority. Session ids are allocated in order: A=1, B=2.
     */
    private static final String CANCELLED_SECOND_OPEN = """
            Assertions: import("std:test/assertions")
            SQLite: import("std:sqlite")
            // Handler-style category test over the delegation chain. Error
            // prototypes define no `recognizes`; sending it signals SlotNotFound.
            isKind: (error, prototype) => {
                found: false
                current: error
                while(!found && (current !== Object)) {
                    found = current === prototype
                    current = current.parent()
                }
                found
            }
            run: (db, sql) => {
                statement: db.prepare(sql)
                (() => { statement.step() }).ensure(() => statement.close().value())
            }
            count: (db, sql) => {
                statement: db.prepare(sql)
                value: null
                (() => {
                    Assertions.require(statement.step())
                    value = statement.column(0)
                }).ensure(() => statement.close().value())
                value
            }
            a: SQLite.openDurable(sqlite, "a.db")
            run(a, "CREATE TABLE t(n)")
            b: null
            cancelled: null
            Error.handle(() => { b = SQLite.openDurable(sqlite, "b.db") }, (error) => { cancelled = error })
            Assertions.require((b === null) && (cancelled !== null))
            Assertions.require(isKind(cancelled, SQLite.SQLiteError))
            Assertions.require(!isKind(cancelled, SQLite.SQLiteUncertainError))
            Assertions.require(cancelled.reason == "LAUNCH_CANCELLED")
            run(a, "INSERT INTO t VALUES (1)")
            Assertions.require(count(a, "SELECT count(*) FROM t") == 1)
            c: SQLite.openDurable(sqlite, "c.db")
            run(c, "CREATE TABLE u(n)")
            Assertions.require(count(c, "SELECT count(*) FROM u") == 0)
            c.close().value()
            a.close().value()
            """;

    private void launchProbe(String mode, String program, String marker) throws Exception {
        Path source = work.resolve(mode + ".protos");
        Files.writeString(source, program);
        Path temporaries = Files.createDirectory(work.resolve(mode + "-tmp"));
        Files.setPosixFilePermissions(temporaries, PosixFilePermissions.fromString("rwx------"));
        Path log = work.resolve(mode + "-output.txt");
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow",
                "-Djava.io.tmpdir=" + temporaries,
                "-cp", classPath, ProtosIsolatedSqliteIntegrationTest.class.getName(),
                mode, distribution.toString(), databaseDirectory.toString(), source.toString())
                .directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("PROTOS_HOME", distribution.toString());
        Process probe = builder.start();
        try {
            assertTrue(probe.waitFor(120, TimeUnit.SECONDS), "FAIL: launch probe timed out: " + mode);
            assertEquals(0, probe.exitValue(), "launch probe " + mode + "\n" + Files.readString(log));
            assertTrue(Files.readString(log).contains(marker), Files.readString(log));
        } finally {
            probe.descendants().forEach(ProcessHandle::destroyForcibly);
            if (probe.isAlive()) probe.destroyForcibly();
        }
        try (var left = Files.list(temporaries)) {
            assertEquals(List.of(), left.map(Path::getFileName).map(Path::toString)
                    .filter(name -> name.startsWith("protos-sqlite-")).toList(), "leaked helper temporaries");
        }
    }

    private void cancelledSecondOpen(String mode) throws Exception {
        for (String name : List.of("a.db", "b.db", "c.db")) manifest(name, "11", "22", "CREATE_NEW");
        launchProbe(mode, CANCELLED_SECOND_OPEN, "LAUNCH_CANCEL_VERIFIED");
        // Cancellation never restores a consumed provisioning permission.
        assertTrue(Files.readString(identityDirectory.resolve("b.db.identity")).endsWith("provision=VERIFY\n"));
    }

    /** Test A: the cancellation lands while B is being submitted; either interleaving is legal. */
    @Test void cancellingOneOpenDoesNotRevokeProcessAuthority() throws Exception {
        cancelledSecondOpen("cancel-submitting");
    }

    /** Test B: the worker is held before its start section; B is withdrawn and never started. */
    @Test void openCancelledBeforeStartIsNeverStarted() throws Exception {
        cancelledSecondOpen("cancel-before-start");
    }

    /** Test C: B's helper was started; cancellation retires exactly that helper before publication. */
    @Test void openCancelledAfterStartRetiresOnlyThatHelper() throws Exception {
        cancelledSecondOpen("cancel-after-start");
    }

    /** Test D: termination of the owning Actor, then of the owning Process, during a launch. */
    @Test void ownerTerminationDuringLaunchPublishesNothing() throws Exception {
        launchProbe("terminate-actor-launch",
                "SQLite: import(\"std:sqlite\")\ndb: SQLite.openDurable(sqlite, \"application.db\")\n",
                "LAUNCH_TERMINATION_VERIFIED");
        manifest("other.db", "33", "44", "CREATE_NEW");
        launchProbe("terminate-process-launch",
                "SQLite: import(\"std:sqlite\")\ndb: SQLite.openDurable(sqlite, \"other.db\")\n",
                "LAUNCH_TERMINATION_VERIFIED");
    }

    /** Test E: CREATE_NEW consumed before the cancelled launch stays VERIFY; nothing is recreated. */
    @Test void consumedCreateNewSurvivesCancellationAsVerifyOnly() throws Exception {
        cancelledSecondOpen("cancel-before-start");
        Path database = databaseDirectory.resolve("b.db");
        Files.deleteIfExists(database);
        Result reopened = program("expectFailure(() => SQLite.openDurable(sqlite, \"b.db\"))", true, false);
        assertEquals(0, reopened.code(), reopened.output());
        assertFalse(Files.exists(database), "a cancelled provisioning must not be recreated");
        assertTrue(Files.readString(identityDirectory.resolve("b.db.identity")).endsWith("provision=VERIFY\n"));
    }

    /**
     * Test F: a real SHARED lock held by an active read statement of one helper makes the COMMIT of
     * another helper return SQLITE_BUSY. The transaction stays open and is committed only by the
     * explicit second commit after the reader is closed (positive control).
     */
    @Test void realBusyCommitIsKnownAndKeepsTransactionOpen() throws Exception {
        success("""
                exec("CREATE TABLE t(n)")
                exec("INSERT INTO t VALUES (1)")
                exec("INSERT INTO t VALUES (2)")
                other: SQLite.openDurable(sqlite, "application.db")
                (() => {
                    reader: db.prepare("SELECT n FROM t")
                    (() => {
                        Assertions.require(reader.step())
                        other.begin()
                        insert: other.prepare("INSERT INTO t VALUES (3)")
                        (() => { Assertions.require(!insert.step()) }).ensure(() => insert.close().value())
                        busy: false
                        Error.handle(() => other.commit(), (error) => {
                            Assertions.require(isKind(error, SQLite.SQLiteError))
                            Assertions.require(!isKind(error, SQLite.SQLiteUncertainError))
                            Assertions.require((error.operation == "commit") && (error.code == 5))
                            busy = true
                        })
                        Assertions.require(busy)
                        Assertions.require(other.inTransaction())
                    }).ensure(() => reader.close().value())
                    other.commit()
                    Assertions.require(!other.inTransaction())
                }).ensure(() => other.close().value())
                Assertions.require(scalar("SELECT count(*) FROM t") == 3)
                """);
    }

    /** Test G (Protos model, not native evidence): a native failure escaping APPLY_ONCE. */
    @Test void nativeFailureReplyToApplyOnceRemainsUncertainAndRetiresHelperOnce() throws Exception {
        for (String name : List.of("IsolatedProtocol.protos", "IsolatedConnection.protos", "Resources.protos")) {
            Files.copy(distribution.resolve("protos/lib/sqlite/internal/" + name), work.resolve(name));
        }
        Result replies = program("""
                Protocol: import("./IsolatedProtocol.protos")
                Streams: import("std:io/process_streams")
                out: Streams.stdoutWriter(process)
                Protocol.writeMessage(out, 1, Protocol.encodeReady(7, 101, 202))
                Protocol.writeMessage(out, 2, Protocol.encodeSqliteFailure(7, 1, 10))
                """, false, false);
        assertEquals(0, replies.code(), replies.output());
        Path input = work.resolve("ready-then-apply-failure.txt");
        Files.writeString(input, replies.output());
        Path source = work.resolve("apply-native-failure.protos");
        Files.writeString(source, PRELUDE + """
                Client: import("./IsolatedConnection.protos")
                releases: 0
                launches: 0
                bridge: {
                    launch: (authority, name) => {
                        launches = launches + 1
                        { session: 7; identityHi: 101; identityLo: 202; reader: process.stdin(); writer: process.stdout() }
                    }
                    owns: (session) => session == 7
                    release: (session) => { releases = releases + 1; 0 }
                }
                uncertain: SQLite.SQLiteUncertainError
                db: Client.open(bridge, {}, "application.db", {}, SQLite.SQLiteError, uncertain)
                observed: false
                Error.handle(() => db.applyOnce(Array(1, 2), "INSERT INTO t VALUES (1)", Array()), (error) => {
                    Assertions.require(isKind(error, uncertain))
                    Assertions.require((error.operation == "applyOnce") && (error.code == 10))
                    Assertions.require((error.reason == "RECONCILE_REQUIRED") && (error.outcome == "UNCERTAIN"))
                    observed = true
                })
                Assertions.require(observed && (releases == 1))
                // No second write and no LOOKUP through this session: only a new session may reconcile.
                for(retry in Array(() => db.applyOnce(Array(1, 2), "INSERT INTO t VALUES (1)", Array()),
                        () => db.lookup(Array(1, 2)))) {
                    refused: false
                    Error.handle(retry, (error) => {
                        Assertions.require(isKind(error, uncertain) && (error.reason == "RECONCILE_REQUIRED"))
                        refused = true
                    })
                    Assertions.require(refused)
                }
                Assertions.require((releases == 1) && (launches == 1))
                """);
        Path log = work.resolve("apply-native-failure-output.txt");
        ProcessBuilder builder = new ProcessBuilder(distribution.resolve("bin/protos").toString(), source.toString())
                .directory(work.toFile()).redirectInput(input.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("PROTOS_HOME");
        Process client = builder.start();
        try {
            assertTrue(client.waitFor(60, TimeUnit.SECONDS), "FAIL: APPLY_ONCE native-failure model timed out");
            assertEquals(0, client.exitValue(), Files.readString(log));
        } finally {
            if (client.isAlive()) client.destroyForcibly();
        }
    }

    /** Live helper descendants, of {@code databaseName} only, or of any database when null. */
    private static List<ProcessHandle> helperProcesses(String databaseName) {
        return ProcessHandle.current().descendants().filter(handle -> {
            String line = handle.info().commandLine().orElse("");
            return handle.isAlive() && line.contains("isolated-helper.protos")
                    && (databaseName == null || line.contains(" " + databaseName + " "));
        }).toList();
    }

    /**
     * Failure description for child-JVM drivers: state, safe Error category slots and guest
     * frames. Never renders SQL text or bound values.
     */
    private static String describeOutcome(ProtosExecutionOutcome outcome) {
        StringBuilder text = new StringBuilder("state=").append(outcome.state());
        var error = outcome.error();
        if (error != null) {
            ArrayList<String> names = new ArrayList<>();
            error.appendLocalBindingNamesTo(names);
            text.append("\nerror local slots=").append(names);
            for (String name : List.of("operation", "reason", "code", "outcome", "rejection")) {
                error.readLocalSlot(name).ifPresent(value -> text.append("\nerror.").append(name).append('=')
                        .append(value instanceof com.guillermomolina.protos.runtime.ProtosStringValue string
                                ? string.value() : String.valueOf(value)));
            }
        }
        outcome.failureDiagnosticTrace().ifPresent(trace -> trace.frames().forEach(frame -> text.append("\n  at ")
                .append(frame.knownDisplayPath().orElse(frame.sourceName()))
                .append(':').append(frame.line()).append(':').append(frame.column())));
        return text.toString();
    }

    /** Child-JVM driver of the launch modes; the observer is Java-only and grants nothing. */
    private static void driveLaunch(
            String mode,
            ProtosPolyglotRuntimeHost host,
            ProtosStandaloneProcessBootstrap.Result bootstrap,
            ProtosDirectFileModuleResolver resolver) throws Exception {
        ProtosIsolatedSqliteHostFacility facility = host.isolatedSqliteForRuntime();
        boolean cancelling = mode.startsWith("cancel-");
        long target = cancelling ? 2 : 1;
        var launching = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var withdrawn = new java.util.concurrent.CountDownLatch(1);
        var failures = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var stages = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var startedHelpers = new java.util.concurrent.CopyOnWriteArrayList<ProcessHandle>();
        facility.launchObserverForTesting = (session, stage) -> {
            stages.add(session + ":" + stage);
            if (session != target) return;
            switch (stage) {
                case "SUBMITTING" -> {
                    launching.set(Thread.currentThread());
                    if (mode.equals("cancel-submitting")) Thread.currentThread().interrupt();
                }
                case "BEFORE_START" -> {
                    if (mode.equals("cancel-before-start")) {
                        launching.get().interrupt();
                        try {
                            if (!withdrawn.await(60, TimeUnit.SECONDS)) failures.add("withdrawal not observed");
                        } catch (InterruptedException interrupted) {
                            failures.add("launcher interrupted while held");
                        }
                    } else if (mode.equals("terminate-actor-launch")) {
                        bootstrap.process().rootActorForRuntime().requestTerminationForRuntime();
                    } else if (mode.equals("terminate-process-launch")) {
                        bootstrap.process().requestTerminationForRuntime();
                    }
                }
                case "STARTED" -> {
                    startedHelpers.addAll(helperProcesses(cancelling ? "b.db" : null));
                    if (mode.equals("cancel-after-start")) Thread.currentThread().interrupt();
                }
                case "CANCELLED" -> {
                    // The harness injected this interrupt; as the cancelling party it consumes it.
                    Thread.interrupted();
                    withdrawn.countDown();
                }
                default -> { }
            }
        };
        if (cancelling) {
            // The expected LAUNCH_CANCELLED is handled by the guest program at the open() of B;
            // the entry itself must complete. Any other result is reported by category.
            ProtosExecutionOutcome outcome;
            try {
                outcome = ProtosStandaloneHostedExecution.executeDirectFile(resolver, bootstrap.activation());
            } catch (RuntimeException unexpected) {
                throw new AssertionError("LAUNCH_UNEXPECTED_HOST_EXCEPTION mode=" + mode
                        + " stages=" + stages + " observerFailures=" + failures, unexpected);
            }
            if (outcome.state() != ProtosExecutionOutcome.State.COMPLETED) {
                org.junit.jupiter.api.Assertions.fail("LAUNCH_GUEST_FAILED mode=" + mode + " stages=" + stages
                        + " observerFailures=" + failures + "\n" + describeOutcome(outcome));
            }
            assertEquals(List.of(), failures, "LAUNCH_CUSTODY_ASSERTION observer failures; stages=" + stages);
            assertTrue(stages.contains("2:CANCELLED"), () -> "LAUNCH_CUSTODY_ASSERTION no cancellation: " + stages);
            if (mode.equals("cancel-before-start")) {
                assertTrue(stages.contains("2:BEFORE_START") && !stages.contains("2:STARTED"),
                        () -> "LAUNCH_CUSTODY_ASSERTION B started: " + stages);
                assertEquals(List.of(), helperProcesses("b.db"), "LAUNCH_CUSTODY_ASSERTION live B helper");
            }
            if (mode.equals("cancel-after-start")) {
                assertFalse(startedHelpers.isEmpty(), "LAUNCH_CUSTODY_ASSERTION the cancelled helper never started");
            }
            for (ProcessHandle helper : startedHelpers) {
                helper.onExit().get(15, TimeUnit.SECONDS);
            }
            // The Process authority survived the cancellation; C opened through it above.
            assertTrue(facility.authorizedForRuntime(bootstrap.process()));
        } else {
            try {
                ProtosStandaloneHostedExecution.executeDirectFile(resolver, bootstrap.activation());
            } catch (RuntimeException terminated) {
                // A terminated owner may end the entry abruptly; custody is asserted below.
            }
            if (mode.equals("terminate-actor-launch")) {
                bootstrap.process().awaitRootActorTerminationForRuntime();
                // Actor termination retires that Actor's attempts, not the Process authority.
                assertTrue(facility.authorizedForRuntime(bootstrap.process()));
            }
            bootstrap.process().requestTerminationForRuntime();
            bootstrap.process().awaitTerminationForRuntime();
            assertFalse(facility.authorizedForRuntime(bootstrap.process()));
            for (ProcessHandle helper : startedHelpers) {
                helper.onExit().get(15, TimeUnit.SECONDS);
            }
        }
        for (ProcessHandle helper : helperProcesses(cancelling ? "b.db" : null)) {
            helper.onExit().get(15, TimeUnit.SECONDS);
        }
        assertEquals(0, facility.liveHelperCountForTesting());
        assertEquals(0, facility.pendingLaunchCountForTesting());
        assertEquals(List.of(), facility.unconfirmedTerminationsForTesting());
        assertEquals(List.of(), helperProcesses(cancelling ? "b.db" : null));
        System.out.println(cancelling ? "LAUNCH_CANCEL_VERIFIED" : "LAUNCH_TERMINATION_VERIFIED");
    }
}
