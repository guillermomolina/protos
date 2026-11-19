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
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PLAT074-B1 physical falsification only. No provider, Actor or production worker is exercised.
 * All native handles stay on their creating platform thread; only Java observations cross it.
 * A retained native call fails the harness without interrupting C or unloading its library.
 */
final class ProtosPlat074SqliteLandlockFeasibilityTest {
    private static final int OK = 0, BUSY = 5, CANTOPEN = 14, ROW = 100, DONE = 101;
    private static final int READONLY = 1, READWRITE = 2, CREATE = 4;
    private static final long VALUE = 8_000_000_000_000_123L;
    private static final long WAIT_SECONDS = 20;
    @TempDir Path temporary;

    @Test
    void t1NativeSqliteOpensOnlyAfterRestriction() throws Exception {
        Path allowed = directory("allowed");
        try (Sqlite sqlite = admittedSqlite()) {
            confined(sqlite, allowed, arena -> {
                try (Database db = sqlite.open(arena, allowed.resolve("main.db"), READWRITE | CREATE)) {
                    seed(db);
                    assertEquals(VALUE, db.scalar("SELECT value FROM sample"));
                }
            });
        }
        assertTrue(Files.isRegularFile(allowed.resolve("main.db")));
    }

    @Test
    void t2ExteriorReadAndWriteHaveUnrestrictedPositiveControls() throws Exception {
        Path allowed = directory("allowed"), exterior = directory("outside").resolve("forbidden.db");
        try (Sqlite sqlite = admittedSqlite()) {
            try (Arena arena = Arena.ofConfined();
                    Database db = sqlite.open(arena, exterior, READWRITE | CREATE)) {
                seed(db);
            }
            for (int flags : new int[] {READONLY, READWRITE}) {
                try (Arena arena = Arena.ofConfined(); Database db = sqlite.open(arena, exterior, flags)) {
                    assertEquals(VALUE, db.scalar("SELECT value FROM sample"));
                    if (flags == READWRITE) db.exec("UPDATE sample SET value=value");
                }
            }
            byte[] before = Files.readAllBytes(exterior);
            confined(sqlite, allowed, arena -> {
                sqlite.denied(arena, exterior, READONLY);
                sqlite.denied(arena, exterior, READWRITE);
            });
            assertArrayEquals(before, Files.readAllBytes(exterior));
        }
    }

    @Test
    void t3WalAndShmSupportIndependentConnections() throws Exception {
        Path allowed = directory("allowed"), outside = directory("outside");
        CountDownLatch observing = new CountDownLatch(1), observed = new CountDownLatch(1);
        try (Sqlite sqlite = admittedSqlite()) {
            Domain domain = start(sqlite, allowed, arena -> {
                try (Database first = sqlite.open(arena, allowed.resolve("main.db"), READWRITE | CREATE)) {
                    first.exec("PRAGMA journal_mode=WAL");
                    seed(first);
                    try (Database second = sqlite.open(arena, allowed.resolve("main.db"), READWRITE)) {
                        assertEquals(VALUE, second.scalar("SELECT value FROM sample"));
                        first.exec("INSERT INTO sample VALUES(42)");
                        assertEquals(2, second.scalar("SELECT count(*) FROM sample"));
                        observing.countDown();
                        await(observed);
                    }
                }
            });
            try {
                await(observing);
                assertTrue(Files.size(allowed.resolve("main.db-wal")) > 0);
                assertTrue(Files.size(allowed.resolve("main.db-shm")) > 0);
                try (var entries = Files.list(outside)) { assertEquals(0, entries.count()); }
            } finally {
                observed.countDown();
                domain.finish();
            }
        }
    }

    @Test
    void t4AttachCannotModifyExistingExteriorDatabase() throws Exception {
        Path allowed = directory("allowed"), exterior = directory("outside").resolve("existing.db");
        try (Sqlite sqlite = admittedSqlite()) {
            try (Arena arena = Arena.ofConfined();
                    Database control = sqlite.open(arena, exterior, READWRITE | CREATE)) { seed(control); }
            byte[] before = Files.readAllBytes(exterior);
            confined(sqlite, allowed, arena -> {
                try (Database db = sqlite.open(arena, allowed.resolve("main.db"), READWRITE | CREATE)) {
                    assertEquals(CANTOPEN, db.execCode("ATTACH DATABASE " + literal(exterior) + " AS exterior"));
                    assertEquals(CANTOPEN, sqlite.code("sqlite3_errcode", db.pointer));
                    assertEquals(1, db.scalar("SELECT count(*) FROM pragma_database_list"));
                }
            });
            assertArrayEquals(before, Files.readAllBytes(exterior));
            try (var entries = Files.list(exterior.getParent())) { assertEquals(1, entries.count()); }
        }
    }

    @Test
    void t5VacuumIntoCannotCreateExteriorOutput() throws Exception {
        Path allowed = directory("allowed"), outside = directory("outside");
        Path target = outside.resolve("vacuum.db");
        assertFalse(Files.exists(target));
        Files.write(outside.resolve("writable-control"), new byte[] {1});
        Files.delete(outside.resolve("writable-control"));
        try (Sqlite sqlite = admittedSqlite()) {
            confined(sqlite, allowed, arena -> {
                try (Database db = sqlite.open(arena, allowed.resolve("main.db"), READWRITE | CREATE)) {
                    seed(db);
                    assertEquals(CANTOPEN, db.execCode("VACUUM INTO " + literal(target)));
                }
            });
        }
        assertFalse(Files.exists(target));
        try (var entries = Files.list(outside)) { assertEquals(0, entries.count()); }
        // End-state observation cannot exclude a transient create/unlink; syscall tracing is unproven.
    }

    @Test
    void t6PreopenedExteriorDescriptorRemainsUsable() throws Exception {
        Path allowed = directory("allowed"), outside = directory("outside");
        Path resource = outside.resolve("preopened");
        Files.write(resource, new byte[] {7});
        try (Sqlite sqlite = admittedSqlite()) {
            int exteriorRoot = ProtosLinuxConfinedFilesystemNative.openRootDirectory(outside.toString());
            int fd;
            try {
                fd = ProtosLinuxConfinedFilesystemNative.openBeneath(
                        exteriorRoot, "preopened", ProtosLinuxConfinedFilesystemNative.O_RDWR, 0);
            } finally { ProtosLinuxConfinedFilesystemNative.close(exteriorRoot); }
            try {
                confined(sqlite, allowed, arena -> {
                    sqlite.denied(arena, resource, READONLY);
                    sqlite.denied(arena, resource, READWRITE);
                    MemorySegment buffer = arena.allocate(1);
                    assertEquals(1, ProtosLinuxConfinedFilesystemNative.pread(fd, buffer, 0));
                    assertEquals((byte) 7, buffer.get(ValueLayout.JAVA_BYTE, 0));
                    buffer.set(ValueLayout.JAVA_BYTE, 0, (byte) 9);
                    assertEquals(1, ProtosLinuxConfinedFilesystemNative.pwrite(fd, buffer, 0));
                });
            } finally { ProtosLinuxConfinedFilesystemNative.close(fd); }
        }
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(resource));
    }

    @Test
    void t7TwoSimultaneousGrantsDenyEachOthersPaths() throws Exception {
        Path a = directory("A"), b = directory("B");
        CountDownLatch ready = new CountDownLatch(2), release = new CountDownLatch(1);
        try (Sqlite sqlite = admittedSqlite()) {
            Domain first = start(sqlite, a, arena -> grantProbe(sqlite, arena, a, b, ready, release));
            try {
                Domain second = start(sqlite, b, arena -> grantProbe(sqlite, arena, b, a, ready, release));
                try { await(ready); } finally { release.countDown(); second.finish(); }
            } finally { release.countDown(); first.finish(); }
        }
    }

    @Test
    void t8BusyIsSqliteStatusWithFiniteBusyWait() throws Exception {
        Path allowed = directory("allowed");
        try (Sqlite sqlite = admittedSqlite()) {
            confined(sqlite, allowed, arena -> {
                try (Database holder = sqlite.open(arena, allowed.resolve("main.db"), READWRITE | CREATE);
                        Database contender = sqlite.open(arena, allowed.resolve("main.db"), READWRITE)) {
                    seed(holder);
                    assertEquals(OK, sqlite.code("sqlite3_busy_timeout", contender.pointer, 100));
                    holder.exec("BEGIN IMMEDIATE");
                    try {
                        assertEquals(BUSY, contender.execCode("INSERT INTO sample VALUES(2)"));
                        assertEquals(BUSY, sqlite.code("sqlite3_errcode", contender.pointer));
                        assertEquals(1, sqlite.code("sqlite3_get_autocommit", contender.pointer));
                        assertEquals(0, sqlite.code("sqlite3_get_autocommit", holder.pointer));
                    } finally { holder.exec("ROLLBACK"); }
                    contender.exec("INSERT INTO sample VALUES(3)");
                }
            });
        }
    }

    @Test
    void t9CloseAndCloseV2HaveDifferentPendingStatementLifetimes() throws Exception {
        Path allowed = directory("allowed");
        try (Sqlite sqlite = admittedSqlite()) {
            confined(sqlite, allowed, arena -> {
                try (Database db = sqlite.open(arena, allowed.resolve("main.db"), READWRITE | CREATE)) {
                    MemorySegment statement = db.prepare("SELECT 42");
                    assertEquals(BUSY, sqlite.code("sqlite3_close", db.pointer));
                    assertEquals(ROW, sqlite.code("sqlite3_step", statement));
                    db.finalizeStatement(statement);
                }
                try (Database db = sqlite.open(arena, allowed.resolve("main.db"), READWRITE)) {
                    MemorySegment statement = db.prepare("SELECT 43");
                    assertEquals(OK, sqlite.code("sqlite3_close_v2", db.pointer));
                    db.pointer = MemorySegment.NULL; // Retired; never pass the database pointer again.
                    assertEquals(ROW, sqlite.code("sqlite3_step", statement));
                    db.finalizeStatement(statement); // Last dependent cleanup, still inside Landlock.
                }
            });
        }
    }

    @Test
    void t10DomainFinishesOnlyAfterNativeResourcesAndCallArenaClose() throws Exception {
        Path allowed = directory("allowed");
        AtomicReference<String> lifecycle = new AtomicReference<>("not entered");
        try (Sqlite sqlite = admittedSqlite()) {
            Domain domain = start(sqlite, allowed, arena -> {
                try (Database db = sqlite.open(arena, allowed.resolve("main.db"), READWRITE | CREATE)) {
                    seed(db);
                    assertEquals(VALUE, db.scalar("SELECT value FROM sample"));
                }
                lifecycle.set("connections and statements released");
            });
            domain.finish();
            assertEquals("connections and statements released", lifecycle.get());
            assertTrue(domain.arenaClosed);
            assertFalse(domain.thread.isAlive());
        }
    }

    @Test
    void t11RestrictionDoesNotSpreadToConcurrentUnrestrictedThread() throws Exception {
        Path allowed = directory("allowed"), exterior = directory("outside").resolve("control.db");
        CountDownLatch restricted = new CountDownLatch(1), controlDone = new CountDownLatch(1);
        try (Sqlite sqlite = admittedSqlite()) {
            Domain domain = start(sqlite, allowed, arena -> {
                restricted.countDown();
                await(controlDone);
                sqlite.denied(arena, exterior, READONLY);
                sqlite.denied(arena, exterior, READWRITE);
            });
            try {
                await(restricted);
                try (Arena arena = Arena.ofConfined();
                        Database control = sqlite.open(arena, exterior, READWRITE | CREATE)) { seed(control); }
            } finally { controlDone.countDown(); domain.finish(); }
        }
    }

    @Test
    void twoOsProcessesRollbackJournalLockVisibilityAndKillRecovery() throws Exception {
        processScenario(false);
    }

    @Test
    void twoOsProcessesWalShmLockVisibilityAndKillRecovery() throws Exception {
        processScenario(true);
    }

    private void processScenario(boolean wal) throws Exception {
        Path root = directory("process-fixture");
        // Admission and warm-up happen before spawning; child failures are never silently skipped.
        try (Sqlite sqlite = admittedSqlite()) {
            assertTrue(sqlite.code("sqlite3_libversion_number") >= 3_027_000);
        }
        try (Peer first = new Peer(root)) {
            first.exchange(wal ? "SEED_WAL" : "SEED_DELETE", "SEEDED");
            try (Peer second = new Peer(root)) {
                second.exchange("COUNT", "COUNT 1");
                first.exchange("HOLD", "HELD");
                second.exchange("BUSY", "BUSY 5");
                if (wal) assertSidecars(root);
                first.exchange("COMMIT", "COMMITTED");
                second.exchange("COUNT", "COUNT 2");
                first.orderlyClose();
                try (Peer reopened = new Peer(root)) {
                    reopened.exchange("COUNT", "COUNT 2");
                    reopened.exchange("HOLD", "HELD");
                    reopened.kill(); // Linux Process.destroyForcibly sends SIGKILL; not power loss.
                    second.exchange("COUNT", "COUNT 2");
                    second.exchange("RECOVER", "RECOVERED");
                    try (Peer recovered = new Peer(root)) {
                        recovered.exchange("COUNT", "COUNT 3");
                        recovered.exchange("SUM", "SUM 7"); // 1 + committed 2 + recovery 4; no killed 3.
                    }
                }
            }
        }
    }

    private static void assertSidecars(Path root) throws Exception {
        assertTrue(Files.size(root.resolve("shared.db-wal")) > 0);
        assertTrue(Files.size(root.resolve("shared.db-shm")) > 0);
    }

    /** Test launcher only: exactly one fixed mode and a parent-owned existing fixture directory. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2 || !arguments[0].equals("peer")) {
            throw new IllegalArgumentException("expected peer and fixture directory");
        }
        Path root = Path.of(arguments[1]).toRealPath();
        if (!Files.isDirectory(root)) throw new IllegalArgumentException("fixture must be a directory");
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        // Warm the stream codecs before confinement; these descriptors remain usable afterward.
        reply(output, "BOOT");
        // Populate the stream reader/decoder paths without consuming the parent's protocol.
        try (var warm = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream("warm\n".getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8))) {
            assertEquals("warm", warm.readLine());
        }
        try (Sqlite sqlite = new Sqlite()) {
            confined(sqlite, root, arena -> {
                try (Database db = sqlite.open(arena, root.resolve("shared.db"), READWRITE | CREATE)) {
                    reply(output, "READY");
                    String command;
                    while ((command = input.readLine()) != null) {
                        switch (command) {
                            case "SEED_WAL", "SEED_DELETE" -> {
                                db.exec(command.equals("SEED_WAL")
                                        ? "PRAGMA journal_mode=WAL" : "PRAGMA journal_mode=DELETE");
                                db.exec("CREATE TABLE sample(value INTEGER); INSERT INTO sample VALUES(1)");
                                reply(output, "SEEDED");
                            }
                            case "COUNT" -> reply(output, "COUNT " + db.scalar("SELECT count(*) FROM sample"));
                            case "SUM" -> reply(output, "SUM " + db.scalar("SELECT sum(value) FROM sample"));
                            case "HOLD" -> {
                                db.exec("BEGIN IMMEDIATE; INSERT INTO sample SELECT max(value)+1 FROM sample");
                                assertEquals(0, sqlite.code("sqlite3_get_autocommit", db.pointer));
                                reply(output, "HELD");
                            }
                            case "BUSY" -> {
                                assertEquals(OK, sqlite.code("sqlite3_busy_timeout", db.pointer, 100));
                                int result = db.execCode("INSERT INTO sample VALUES(99)");
                                assertEquals(BUSY, result);
                                assertEquals(1, sqlite.code("sqlite3_get_autocommit", db.pointer));
                                reply(output, "BUSY " + result);
                            }
                            case "COMMIT" -> { db.exec("COMMIT"); reply(output, "COMMITTED"); }
                            case "RECOVER" -> {
                                db.exec("INSERT INTO sample VALUES(4)");
                                assertEquals(1, sqlite.code("sqlite3_get_autocommit", db.pointer));
                                reply(output, "RECOVERED");
                            }
                            case "CLOSE" -> { return; }
                            default -> throw new IllegalArgumentException("unknown test command");
                        }
                    }
                    throw new AssertionError("parent closed protocol before CLOSE");
                }
            });
        }
        reply(output, "CLOSED"); // After connection, thread arena, domain and library cleanup.
    }

    private static void reply(BufferedWriter output, String response) throws Exception {
        output.write(response);
        output.newLine();
        output.flush();
    }

    private static final class Peer implements AutoCloseable {
        final Process process;
        final BufferedWriter commands;
        final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
        final Thread reader;
        boolean ended;

        Peer(Path root) throws Exception {
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", classpath,
                    ProtosPlat074SqliteLandlockFeasibilityTest.class.getName(), "peer", root.toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            commands = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            reader = Thread.ofPlatform().daemon().name("plat074-peer-output").start(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) replies.add(line);
                    replies.add("<EOF>");
                } catch (Exception failure) { replies.add("<IO failure: " + failure + ">"); }
            });
            try { expect("BOOT"); expect("READY"); } catch (Throwable failure) {
                try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        void expect(String expected) throws Exception {
            assertEquals(expected, replies.poll(WAIT_SECONDS, TimeUnit.SECONDS), "child barrier; pid=" + process.pid());
        }

        void exchange(String command, String expected) throws Exception {
            reply(commands, command);
            expect(expected);
        }

        void orderlyClose() throws Exception {
            if (ended) return;
            exchange("CLOSE", "CLOSED");
            assertTrue(process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS), "child retained after cleanup");
            assertEquals(0, process.exitValue());
            ended = true;
        }

        void kill() throws Exception {
            process.destroyForcibly();
            assertTrue(process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS), "SIGKILL did not terminate child");
            assertEquals(137, process.exitValue(), "Linux SIGKILL status");
            ended = true;
        }

        @Override public void close() throws Exception {
            try { if (!ended && process.isAlive()) orderlyClose(); }
            finally {
                if (process.isAlive()) {
                    process.destroyForcibly(); // Fixture containment after failure, not native cancellation.
                    assertTrue(process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS), "retained child process");
                }
                commands.close();
                reader.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
                assertFalse(reader.isAlive(), "retained child stream reader");
            }
        }
    }

    private Path directory(String name) throws Exception {
        return Files.createDirectories(temporary.resolve(name)).toRealPath();
    }

    private static String literal(Path path) { return "'" + path.toString().replace("'", "''") + "'"; }

    private static void seed(Database db) {
        db.exec("CREATE TABLE sample(value INTEGER); INSERT INTO sample VALUES(" + VALUE + ")");
    }

    private static void grantProbe(Sqlite sqlite, Arena arena, Path own, Path other,
            CountDownLatch ready, CountDownLatch release) throws Exception {
        try (Database db = sqlite.open(arena, own.resolve("main.db"), READWRITE | CREATE)) { seed(db); }
        ready.countDown();
        await(release);
        sqlite.denied(arena, other.resolve("main.db"), READONLY);
        sqlite.denied(arena, other.resolve("main.db"), READWRITE);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(WAIT_SECONDS, TimeUnit.SECONDS), "test barrier not reached");
    }

    @FunctionalInterface private interface Body { void run(Arena arena) throws Exception; }

    private static void confined(Sqlite sqlite, Path root, Body body) throws Exception {
        start(sqlite, root, body).finish();
    }

    private static Domain start(Sqlite sqlite, Path root, Body body) throws Exception {
        ProtosLinuxConfinedFilesystemNative.preloadForRestrictedThread();
        int rootFd = ProtosLinuxConfinedFilesystemNative.openRootDirectory(root.toString());
        Domain domain = new Domain();
        sqlite.active.incrementAndGet();
        try {
            domain.thread = Thread.ofPlatform().daemon().name("plat074-sqlite-landlock").start(() -> {
                try (Arena arena = Arena.ofConfined()) {
                    ProtosLinuxConfinedFilesystemLandlock.restrictCurrentThreadBeneath(rootFd);
                    domain.installed = true; // Only reached after successful restrict_self.
                    body.run(arena);
                } catch (Throwable failure) { domain.failure = failure; }
                finally {
                    try { ProtosLinuxConfinedFilesystemNative.close(rootFd); }
                    catch (Throwable cleanup) {
                        if (domain.failure == null) domain.failure = cleanup;
                        else domain.failure.addSuppressed(cleanup);
                    }
                    domain.arenaClosed = domain.failure == null;
                    sqlite.active.decrementAndGet();
                }
            });
        } catch (Throwable failure) {
            sqlite.active.decrementAndGet();
            ProtosLinuxConfinedFilesystemNative.close(rootFd);
            throw failure;
        }
        return domain;
    }

    private static final class Domain {
        Thread thread;
        volatile Throwable failure;
        volatile boolean installed, arenaClosed;
        void finish() throws Exception {
            thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
            assertFalse(thread.isAlive(), "retained native thread; cleanup incomplete; no forced interruption");
            if (!installed && failure instanceof ProtosLinuxConfinedFilesystemNative.NativeException) {
                assumeTrue(false, "UNSUPPORTED: Landlock installation refused: " + failure);
            }
            if (failure != null) throw new AssertionError("restricted domain failed", failure);
            assertTrue(installed, "SQLite must never run after a failed installation");
            assertTrue(arenaClosed);
        }
    }

    private static Sqlite admittedSqlite() {
        ProtosLinuxConfinedFilesystemTestSupport.assumeConfinementPrimitives();
        try { return new Sqlite(); }
        catch (UnsatisfiedLinkError | IllegalArgumentException unavailable) {
            assumeTrue(false, "UNSUPPORTED: SQLite library/symbol unavailable: " + unavailable);
            throw new AssertionError("unreachable", unavailable);
        }
    }

    /** Exact C signatures: int statuses/flags, sqlite3_int64 result, and native pointers. */
    private static final class Sqlite implements AutoCloseable {
        final Arena library = Arena.ofShared();
        final Map<String, MethodHandle> functions = new HashMap<>();
        final AtomicInteger active = new AtomicInteger();
        volatile boolean cleanupIncomplete;
        Sqlite() {
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup symbols = SymbolLookup.libraryLookup("libsqlite3.so.0", library);
                bind(linker, symbols, "sqlite3_libversion_number", ValueLayout.JAVA_INT);
                bind(linker, symbols, "sqlite3_threadsafe", ValueLayout.JAVA_INT);
                bind(linker, symbols, "sqlite3_open_v2", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
                bind(linker, symbols, "sqlite3_prepare_v2", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS);
                for (String name : List.of("sqlite3_step", "sqlite3_finalize", "sqlite3_close", "sqlite3_close_v2",
                        "sqlite3_errcode", "sqlite3_get_autocommit")) {
                    bind(linker, symbols, name, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
                }
                bind(linker, symbols, "sqlite3_exec", ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
                bind(linker, symbols, "sqlite3_column_int64", ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
                bind(linker, symbols, "sqlite3_busy_timeout", ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
                assumeTrue(code("sqlite3_libversion_number") >= 3_027_000,
                        "UNSUPPORTED: VACUUM INTO needs SQLite 3.27+");
                assumeTrue(code("sqlite3_threadsafe") != 0,
                        "UNSUPPORTED: simultaneous grants require threadsafe SQLite");
                // Exercise adapters and test-only classes before denying classpath filesystem access.
                try (Arena arena = Arena.ofConfined(); Database db = openName(arena, ":memory:", READWRITE | CREATE)) {
                    seed(db);
                    assertEquals(VALUE, db.scalar("SELECT value FROM sample"));
                    assertEquals(OK, code("sqlite3_busy_timeout", db.pointer, 100));
                    assertEquals(OK, code("sqlite3_errcode", db.pointer));
                    assertEquals(1, code("sqlite3_get_autocommit", db.pointer));
                    MemorySegment statement = db.prepare("SELECT 1");
                    assertEquals(BUSY, code("sqlite3_close", db.pointer));
                    assertEquals(OK, code("sqlite3_close_v2", db.pointer));
                    db.pointer = MemorySegment.NULL;
                    db.finalizeStatement(statement);
                }
                new Domain();
            } catch (Throwable failure) {
                if (!cleanupIncomplete) library.close();
                throw failure;
            }
        }

        void bind(Linker linker, SymbolLookup symbols, String name, ValueLayout result, ValueLayout... inputs) {
            functions.put(name, linker.downcallHandle(symbols.find(name).orElseThrow(
                    () -> new IllegalArgumentException("missing SQLite symbol: " + name)),
                    FunctionDescriptor.of(result, inputs)));
        }
        Object call(String name, Object... arguments) {
            try { return functions.get(name).invokeWithArguments(arguments); }
            catch (Throwable failure) { throw new AssertionError("native call failed: " + name, failure); }
        }
        int code(String name, Object... arguments) { return (int) call(name, arguments); }
        Database open(Arena arena, Path path, int flags) { return openName(arena, path.toString(), flags); }
        Database openName(Arena arena, String name, int flags) {
            MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
            int status = code("sqlite3_open_v2", arena.allocateFrom(name), output, flags, MemorySegment.NULL);
            MemorySegment pointer = output.get(ValueLayout.ADDRESS, 0);
            if (status != OK) {
                closeAcquisition(pointer);
                throw new AssertionError("sqlite3_open_v2 status=" + status + "; path=" + name);
            }
            return new Database(this, arena, pointer);
        }
        void denied(Arena arena, Path path, int flags) {
            MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
            int result = code("sqlite3_open_v2", arena.allocateFrom(path.toString()), output, flags, MemorySegment.NULL);
            MemorySegment pointer = output.get(ValueLayout.ADDRESS, 0);
            try { assertEquals(CANTOPEN, result, "exterior open must fail: " + path); }
            finally {
                closeAcquisition(pointer);
            }
        }
        void closeAcquisition(MemorySegment pointer) {
            if (pointer.address() == 0) return;
            try { assertEquals(OK, code("sqlite3_close", pointer), "acquisition cleanup"); }
            catch (Throwable failure) { cleanupIncomplete = true; throw failure; }
        }
        @Override public void close() {
            assertEquals(0, active.get(), "cleanup incomplete: retain library while native thread is alive");
            assertFalse(cleanupIncomplete, "cleanup incomplete: retain library with unresolved native resources");
            library.close();
        }
    }

    /** Fixture ownership only, deliberately separate from FfmSession custody. */
    private static final class Database implements AutoCloseable {
        final Sqlite sqlite;
        final Arena arena;
        final List<MemorySegment> statements = new ArrayList<>();
        MemorySegment pointer;
        Database(Sqlite sqlite, Arena arena, MemorySegment pointer) {
            this.sqlite = sqlite; this.arena = arena; this.pointer = pointer;
        }
        int execCode(String sql) {
            return sqlite.code("sqlite3_exec", pointer, arena.allocateFrom(sql),
                    MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
        }
        void exec(String sql) { assertEquals(OK, execCode(sql), sql); }
        MemorySegment prepare(String sql) {
            MemorySegment output = arena.allocate(ValueLayout.ADDRESS);
            int status = sqlite.code("sqlite3_prepare_v2", pointer, arena.allocateFrom(sql), -1,
                    output, MemorySegment.NULL);
            MemorySegment statement = output.get(ValueLayout.ADDRESS, 0);
            if (statement.address() != 0) statements.add(statement);
            assertEquals(OK, status, sql);
            assertNotEquals(0L, statement.address(), "expected a statement");
            return statement;
        }
        long scalar(String sql) {
            MemorySegment statement = prepare(sql);
            try {
                assertEquals(ROW, sqlite.code("sqlite3_step", statement), sql);
                long result = (long) sqlite.call("sqlite3_column_int64", statement, 0);
                assertEquals(DONE, sqlite.code("sqlite3_step", statement), sql);
                return result;
            } finally { finalizeStatement(statement); }
        }
        void finalizeStatement(MemorySegment statement) {
            assertTrue(statements.remove(statement), "statement must be finalized exactly once");
            try { assertEquals(OK, sqlite.code("sqlite3_finalize", statement)); }
            catch (Throwable failure) { sqlite.cleanupIncomplete = true; throw failure; }
        }
        @Override public void close() {
            Throwable failure = null;
            for (MemorySegment statement : List.copyOf(statements)) {
                try { finalizeStatement(statement); }
                catch (Throwable cleanup) {
                    if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
                }
            }
            if (pointer.address() != 0) {
                MemorySegment closing = pointer;
                pointer = MemorySegment.NULL;
                try { assertEquals(OK, sqlite.code("sqlite3_close", closing), "connection cleanup incomplete"); }
                catch (Throwable cleanup) {
                    if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
                }
            }
            if (failure != null) {
                sqlite.cleanupIncomplete = true;
                throw new AssertionError("native cleanup incomplete", failure);
            }
        }
    }
}
