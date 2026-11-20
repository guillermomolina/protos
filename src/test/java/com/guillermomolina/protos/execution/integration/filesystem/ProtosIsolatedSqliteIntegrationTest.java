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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Public installed-distribution acceptance: all SQL and assertions execute in Protos. */
final class ProtosIsolatedSqliteIntegrationTest {
    @TempDir Path work;
    private Path distribution;
    private Path databaseDirectory;
    private Path identityDirectory;
    private int serial;

    @BeforeEach
    void installedDistribution() throws Exception {
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
            """;
    private static final String DATABASE = """
            db: SQLite.openDurable(sqlite, "application.db")
            exec: (sql) => {
                statement: db.prepare(sql)
                (() => { Assertions.require(!statement.step()) }).ensure(() => statement.close().value())
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

    private void success(String body) throws Exception {
        Result result = program(DATABASE + "(() => {\n" + body + "\n}).ensure(() => db.close().value())\n", true, false);
        assertEquals(0, result.code(), result.output());
    }

    private record Result(int code, String output) {}

    private Result command(List<String> args) throws Exception {
        Path log = work.resolve("output-" + serial++ + ".txt");
        ProcessBuilder builder = new ProcessBuilder(args).directory(work.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().remove("PROTOS_HOME");
        builder.environment().remove("CLASSPATH");
        Process process = builder.start();
        process.getOutputStream().close();
        try {
            assertTrue(process.waitFor(120, TimeUnit.SECONDS), "FAIL: process timeout: " + args.getFirst());
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
        success("""
                query: db.prepare("SELECT zeroblob(16777216), zeroblob(16777217)")
                (() => {
                    Assertions.require(query.step())
                    bytes: query.column(0)
                    Assertions.require(bytes.size() == 16777216)
                    Assertions.require((bytes[0] == 0) && (bytes[16777215] == 0))
                    expectFailure(() => query.column(1))
                    query.close().value()
                    bound: db.prepare("SELECT length(?1)")
                    (() => {
                        bound.bind(1, bytes)
                        Assertions.require(bound.step())
                        Assertions.require(bound.column(0) == 16777216)
                        bound.reset()
                        bytes.add(0)
                        expectFailure(() => bound.bind(1, bytes))
                    }).ensure(() => bound.close().value())
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
                ABI: import("./abi.protos")
                Resources: import("./Resources.protos")
                ControlError: Error {}
                terminal: {
                    closeAllowed: () => true
                    resolved: (value) => (() => value).future()
                    failed: (error) => (() => error.signal()).future()
                }
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
                exec("ATTACH DATABASE ?1 AS exterior", %s)
                Assertions.require(scalar("SELECT count(*) FROM pragma_database_list WHERE name = 'exterior'") == 1)
                exec("CREATE TABLE exterior.sample(n)", null)
                exec("INSERT INTO exterior.sample VALUES (73)", null)
                Assertions.require(scalar("SELECT n FROM exterior.sample") == 73)
                Assertions.require(db.close().value() === db)
                Streams: import("std:io/process_streams")
                out: Streams.stdoutWriter(process)
                out.writeLine("EXTERIOR_CONTROL_READY").value()
                out.flush().value()
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
        Process owner = builder.start();
        List<ProcessHandle> children = List.of();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(owner.getInputStream(), StandardCharsets.UTF_8))) {
            String ready = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return reader.readLine(); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }).get(60, TimeUnit.SECONDS);
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
                oversized: JSON.encode(JSON.object("v", JSON.number(3, 0), "s", JSON.number(1, 0),
                    "i", JSON.number(0, 0), "n", JSON.number((50331649 + 2303).div(2304), 0),
                    "o", JSON.number(50331649, 0), "d", JSON.string(Base64url.encode(chunk))))
                expectFailure(() => read(Array(oversized)))
                invalid: Encoding.Latin1.encode("")
                invalid.add(255)
                invalidFrame: JSON.encode(JSON.object("v", JSON.number(3, 0), "s", JSON.number(1, 0),
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
                    Assertions.require(query.step())
                    text = query.column(0)
                    Assertions.require(String.recognizes(text))
                    Assertions.require(Encoding.UTF8.encode(text).size() == 16777216)
                }).ensure(() => query.close().value())
                query = db.prepare("SELECT length(CAST(?1 AS BLOB))")
                (() => {
                    query.bind(1, text)
                    Assertions.require(query.step())
                    Assertions.require(query.column(0) == 16777216)
                }).ensure(() => query.close().value())
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
                assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), () -> String.valueOf(outcome.error()));
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
            } finally {
                // A failed assertion must not leave this Process Context active when the host
                // closes; the primary failure keeps propagating after this retirement.
                if (bootstrap.process().lifecycleState()
                        != com.guillermomolina.protos.runtime.ProtosProcessRuntime.LifecycleState.TERMINATED) {
                    bootstrap.process().requestTerminationForRuntime();
                    bootstrap.process().awaitTerminationForRuntime();
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
                readMessage = (reader, seq) => {
                    text: _isolatedProtocol.readMessage(reader, seq)
                    if(seq == 10) { text = null }
                    text
                }
                """, java.nio.file.StandardOpenOption.APPEND);
        Path source = work.resolve("lost-real-commit.protos");
        String open = DATABASE.replace("db: SQLite.openDurable(sqlite, \"application.db\")",
                "Client: import(\"./IsolatedConnection.protos\")\ndb: Client.open(realBridge, sqlite, \"application.db\", terminal, SQLite.SQLiteError, SQLite.SQLiteUncertainError)");
        Files.writeString(source, PRELUDE + open + """
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
            assertEquals(0, probe.exitValue(), Files.readString(log));
            assertTrue(Files.readString(log).contains("REAL_COMMIT_ACK_LOST_REOPEN_VERIFIED"));
        } finally {
            probe.descendants().forEach(ProcessHandle::destroyForcibly);
            if (probe.isAlive()) probe.destroyForcibly();
        }
    }


    @Test void sameActorTasksRejectConcurrentStepBeforeAnyIpc() throws Exception { concurrentAdmission("STEP"); }
    @Test void sameActorTasksRejectConcurrentCommitBeforeAnyIpc() throws Exception { concurrentAdmission("COMMIT"); }
    @Test void sameActorTasksRejectConcurrentCloseBeforeAnyIpc() throws Exception { concurrentAdmission("CLOSE"); }
    @Test void cancelledPendingStepRequiresReconciliation() throws Exception { concurrentAdmission("CANCEL_STEP"); }
    @Test void cancelledPendingCommitRequiresReconciliation() throws Exception { concurrentAdmission("CANCEL_COMMIT"); }
    @Test void cancelledPendingCloseCannotPublishClosedSuccess() throws Exception { concurrentAdmission("CANCEL_CLOSE"); }

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
                    {
                        pending: (owner) => {
                            Assertions.require(owner.request("probeBusy").value())
                            out.writeLine("BUSY_ASSERTIONS_COMPLETE").value()
                            out.flush().value()
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
                        write: (bytes) => { writes = writes + bytes.size(); terminal.resolved(writer) }
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
                            cancelMode = (mode == "CANCEL_STEP") || (mode == "CANCEL_COMMIT") || (mode == "CANCEL_CLOSE")
                            target = mode
                            if(mode == "CANCEL_STEP") { target = "STEP" }
                            if(mode == "CANCEL_COMMIT") { target = "COMMIT" }
                            if(mode == "CANCEL_CLOSE") { target = "CLOSE" }
                            enqueue(1, Protocol.encodeReady(7, 101, 202))
                            next: 2
                            if(target == "STEP") {
                                enqueue(2, Protocol.encodeResponse(7, 1, "PREPARE",
                                    Array(JSON.number(0, 0), JSON.number(1, 0), JSON.number(0, 0))))
                                next = 3
                            }
                            blockAt = frames.size()
                            if(target == "STEP") {
                                enqueue(next, Protocol.encodeResponse(7, next - 1, "STEP", Array(JSON.number(101, 0), JSON.nullValue())))
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
                            if(target == "STEP") { statement = db.prepare("SELECT 1") }
                            operation = (() => {
                                if(target == "STEP") { statement.step() }
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
        Process client = builder.start();
        try (BufferedReader output = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            String signaled = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return output.readLine(); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }).get(60, TimeUnit.SECONDS);
            if (!"BUSY_ASSERTIONS_COMPLETE".equals(signaled)) {
                // Keep the guest's original Actor/Task error rather than only EOF/null.
                boolean ended = client.waitFor(10, TimeUnit.SECONDS);
                org.junit.jupiter.api.Assertions.fail("scenario " + selectedMode
                        + ": expected BUSY_ASSERTIONS_COMPLETE but was " + signaled
                        + "; exit=" + (ended ? String.valueOf(client.exitValue()) : "running")
                        + "\n" + Files.readString(work.resolve("admission-errors.txt")));
            }
            client.getOutputStream().write(1);
            client.getOutputStream().flush();
            client.getOutputStream().close();
            assertTrue(client.waitFor(60, TimeUnit.SECONDS), "FAIL: Actor-local admission probe timed out");
            assertEquals(0, client.exitValue(), "scenario " + selectedMode + "\n"
                    + Files.readString(work.resolve("admission-errors.txt")));
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
            var outcome = ProtosStandaloneHostedExecution.executeDirectFile(resolver, bootstrap.activation());
            assertEquals(ProtosExecutionOutcome.State.COMPLETED, outcome.state(), () -> String.valueOf(outcome.error()));
            assertEquals(List.of(), failures);
            assertTrue(stages.contains("2:CANCELLED"), stages::toString);
            if (mode.equals("cancel-before-start")) {
                assertTrue(stages.contains("2:BEFORE_START") && !stages.contains("2:STARTED"), stages::toString);
                assertEquals(List.of(), helperProcesses("b.db"));
            }
            if (mode.equals("cancel-after-start")) {
                assertFalse(startedHelpers.isEmpty(), "FAIL: the cancelled helper never started");
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
