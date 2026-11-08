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

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * I156 / PLAT072-E / PLAT073-C internal Linux system-call bridge of the confined Filesystem grant,
 * implemented exclusively with the Java 25 Foreign Function &amp; Memory API.
 *
 * <p>Every call goes through one stable downcall of the C library {@code long syscall(long
 * number, ...)} function. The descriptor is fixed (the number plus six variadic {@code long}
 * arguments, with {@code firstVariadicArg(1)}); unused arguments are passed as zero, so no
 * kernel entry reads an indeterminate register. Pointers are passed as their native addresses,
 * which the supported LP64 ABI represents as a C {@code long}. {@code errno} is captured by the
 * linker itself ({@code captureCallState("errno")}) as part of the same downcall, before any other
 * native code can run on the thread.
 *
 * <p><b>Return contract.</b> The libc wrapper, not the raw kernel convention, is decoded: a result
 * of exactly {@code -1} means failure and the captured {@code errno} is authoritative; any other
 * negative result is a protocol violation of the wrapper and is reported as unavailability, never
 * reinterpreted as a negated errno.
 *
 * <p>Nothing here touches guest objects or activations. FFM state is initialized lazily on first
 * use, so a Process without a Filesystem grant never links the bridge. Only Linux x86_64 is
 * accepted; system-call numbers are architecture specific and an unknown architecture is
 * refused rather than mapped.
 */
final class ProtosLinuxConfinedFilesystemNative {
    private ProtosLinuxConfinedFilesystemNative() {}

    // Linux errno values (asm-generic, shared by x86_64).
    static final int EPERM = 1;
    static final int ENOENT = 2;
    static final int EINTR = 4;
    static final int E2BIG = 7;
    static final int EBADF = 9;
    static final int EAGAIN = 11;
    static final int EACCES = 13;
    static final int EEXIST = 17;
    static final int EXDEV = 18;
    static final int ENOTDIR = 20;
    static final int EISDIR = 21;
    static final int EINVAL = 22;
    static final int ENOSYS = 38;
    static final int ENOTEMPTY = 39;
    static final int ELOOP = 40;
    static final int EILSEQ = 84;
    static final int EOPNOTSUPP = 95;

    // open(2) flags (x86_64 values).
    static final int O_RDONLY = 0;
    static final int O_WRONLY = 01;
    static final int O_RDWR = 02;
    static final int O_CREAT = 0100;
    static final int O_EXCL = 0200;
    static final int O_NOCTTY = 0400;
    static final int O_NONBLOCK = 04000;
    static final int O_DIRECTORY = 0200000;
    static final int O_NOFOLLOW = 0400000;
    static final int O_CLOEXEC = 02000000;

    /** Creation mode of a new File resource; the process umask still applies. */
    static final int CREATE_MODE = 0666;

    // openat2(2) resolve flags.
    static final long RESOLVE_NO_XDEV = 0x01;
    static final long RESOLVE_NO_MAGICLINKS = 0x02;
    static final long RESOLVE_NO_SYMLINKS = 0x04;
    static final long RESOLVE_BENEATH = 0x08;
    /** Every descendant selection carries all four restrictions; none is ever relaxed. */
    static final long RESOLVE_CONFINED =
            RESOLVE_BENEATH | RESOLVE_NO_SYMLINKS | RESOLVE_NO_MAGICLINKS | RESOLVE_NO_XDEV;

    private static final int AT_FDCWD = -100;
    private static final int AT_REMOVEDIR = 0x200;
    private static final int PR_SET_NO_NEW_PRIVS = 38;

    // Landlock (include/uapi/linux/landlock.h).
    private static final long LANDLOCK_CREATE_RULESET_VERSION = 1L;
    private static final long LANDLOCK_RULE_PATH_BENEATH = 1L;

    // File type bits of st_mode and getdents64 d_type values.
    private static final int S_IFMT = 0170000;
    static final int S_IFDIR = 0040000;
    static final int S_IFREG = 0100000;
    static final int DT_UNKNOWN = 0;
    static final int DT_DIR = 4;
    static final int DT_REG = 8;
    static final int DT_LNK = 10;

    private static final int MAX_INTERRUPT_RETRIES = 16;
    private static final int DIRECTORY_BUFFER_BYTES = 32 * 1024;

    /** {@code struct open_how}: three 64-bit fields, 24 bytes. */
    static final StructLayout OPEN_HOW =
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_LONG.withName("flags"),
                    ValueLayout.JAVA_LONG.withName("mode"),
                    ValueLayout.JAVA_LONG.withName("resolve"));

    /**
     * {@code struct landlock_ruleset_attr} (extensible): {@code handled_access_fs} (ABI 1), {@code
     * handled_access_net} (ABI 4) and {@code scoped} (ABI 6). The trailing fields are always zero,
     * which the kernel's extensible-struct copy accepts on every ABI.
     */
    static final StructLayout LANDLOCK_RULESET_ATTR =
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_LONG.withName("handled_access_fs"),
                    ValueLayout.JAVA_LONG.withName("handled_access_net"),
                    ValueLayout.JAVA_LONG.withName("scoped"));

    /**
     * {@code struct landlock_path_beneath_attr}, declared {@code __attribute__((packed))} by the
     * kernel UAPI: a 64-bit {@code allowed_access} followed by a 32-bit {@code parent_fd}, 12 bytes
     * with no trailing padding. The packed declaration is the ABI; adding padding would be wrong.
     */
    static final StructLayout LANDLOCK_PATH_BENEATH_ATTR =
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_LONG.withName("allowed_access"),
                    ValueLayout.JAVA_INT.withName("parent_fd"));

    /** x86_64 {@code struct stat} as filled by {@code fstat}; only mode, size and identity used. */
    static final StructLayout STAT_X86_64 =
            MemoryLayout.structLayout(
                    ValueLayout.JAVA_LONG.withName("st_dev"),
                    ValueLayout.JAVA_LONG.withName("st_ino"),
                    ValueLayout.JAVA_LONG.withName("st_nlink"),
                    ValueLayout.JAVA_INT.withName("st_mode"),
                    ValueLayout.JAVA_INT.withName("st_uid"),
                    ValueLayout.JAVA_INT.withName("st_gid"),
                    ValueLayout.JAVA_INT.withName("__pad0"),
                    ValueLayout.JAVA_LONG.withName("st_rdev"),
                    ValueLayout.JAVA_LONG.withName("st_size"),
                    ValueLayout.JAVA_LONG.withName("st_blksize"),
                    ValueLayout.JAVA_LONG.withName("st_blocks"),
                    MemoryLayout.sequenceLayout(6, ValueLayout.JAVA_LONG).withName("st_times"),
                    MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_LONG).withName("__unused"));

    /** {@code linux_dirent64}: d_ino(8) d_off(8) d_reclen(2) d_type(1) d_name[]. */
    private static final long DIRENT_RECLEN_OFFSET = 16;
    private static final long DIRENT_TYPE_OFFSET = 18;
    private static final long DIRENT_NAME_OFFSET = 19;

    static {
        requireLayout(OPEN_HOW, 24, "open_how");
        requireLayout(LANDLOCK_RULESET_ATTR, 24, "landlock_ruleset_attr");
        requireLayout(LANDLOCK_PATH_BENEATH_ATTR, 12, "landlock_path_beneath_attr");
        requireLayout(STAT_X86_64, 144, "stat");
    }

    private static void requireLayout(StructLayout layout, long size, String name) {
        if (layout.byteSize() != size) {
            throw new AssertionError("unexpected native layout size of " + name);
        }
    }

    /**
     * A failed native operation. {@link #unavailable()} distinguishes a missing or refused
     * platform primitive (bridge, architecture, kernel feature, security policy) from an ordinary
     * operation failure such as an absent entry.
     */
    static final class NativeException extends IOException {
        private static final long serialVersionUID = 1L;

        private final int errno;
        private final boolean unavailable;

        NativeException(String operation, int errno) {
            super(operation + " failed (errno " + errno + ")");
            this.errno = errno;
            this.unavailable = errno == ENOSYS || errno == EOPNOTSUPP;
        }

        NativeException(String message, Throwable cause) {
            super(message, cause);
            this.errno = 0;
            this.unavailable = true;
        }

        int errno() {
            return errno;
        }

        boolean unavailable() {
            return unavailable;
        }
    }

    /** Result of one {@code fstat}: file type and size plus device/inode identity. */
    record Stat(int mode, long size, long device, long inode) {
        boolean regular() {
            return (mode & S_IFMT) == S_IFREG;
        }

        boolean directory() {
            return (mode & S_IFMT) == S_IFDIR;
        }
    }

    /** One name and type from a single {@code getdents64} record. */
    record DirectoryEntry(String name, int type) {}

    /** Private per-architecture system-call numbers. Only x86_64 is enabled. */
    private record SyscallTable(
            long openat,
            long openat2,
            long close,
            long pread64,
            long pwrite64,
            long fstat,
            long ftruncate,
            long fsync,
            long getdents64,
            long renameat2,
            long unlinkat,
            long prctl,
            long landlockCreateRuleset,
            long landlockAddRule,
            long landlockRestrictSelf) {
        static final SyscallTable X86_64 =
                new SyscallTable(257, 437, 3, 17, 18, 5, 77, 74, 217, 316, 263, 157, 444, 445, 446);

        static SyscallTable forHost() throws NativeException {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            if (!os.equals("linux")) {
                throw new NativeException(
                        "the confined Filesystem grant requires Linux (host: " + os + ")", null);
            }
            if (arch.equals("amd64") || arch.equals("x86_64")) {
                return X86_64;
            }
            throw new NativeException(
                    "the confined Filesystem grant is not supported on architecture " + arch, null);
        }
    }

    /** Lazily linked FFM state: built on first use, never by a Process without the grant. */
    private static final class Bridge {
        private static final Bridge INSTANCE;
        private static final String FAILURE;
        private static final Throwable FAILURE_CAUSE;

        static {
            Bridge bridge = null;
            String failure = null;
            Throwable cause = null;
            try {
                bridge = new Bridge();
            } catch (NativeException unsupported) {
                failure = unsupported.getMessage();
                cause = unsupported;
            } catch (RuntimeException | LinkageError unusable) {
                failure = "the Java FFM native linker is unusable for the confined Filesystem grant";
                cause = unusable;
            }
            INSTANCE = bridge;
            FAILURE = failure;
            FAILURE_CAUSE = cause;
        }

        private final SyscallTable table;
        private final MethodHandle syscall;
        private final StructLayout captureState;
        private final VarHandle errno;

        private Bridge() throws NativeException {
            this.table = SyscallTable.forHost();
            Linker linker = Linker.nativeLinker();
            MemorySegment symbol =
                    linker.defaultLookup()
                            .find("syscall")
                            .orElseThrow(
                                    () -> new NativeException(
                                            "the C library exposes no syscall entry point", null));
            this.captureState = Linker.Option.captureStateLayout();
            this.errno = captureState.varHandle(MemoryLayout.PathElement.groupElement("errno"));
            this.syscall =
                    linker.downcallHandle(
                            symbol,
                            SYSCALL_DESCRIPTOR,
                            Linker.Option.firstVariadicArg(1),
                            Linker.Option.captureCallState("errno"));
        }

        static Bridge require() throws NativeException {
            if (INSTANCE == null) {
                throw new NativeException(FAILURE, FAILURE_CAUSE);
            }
            return INSTANCE;
        }
    }

    /** {@code long syscall(long number, long, long, long, long, long, long)}. */
    static final FunctionDescriptor SYSCALL_DESCRIPTOR =
            FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG);

    /** Fails with an unavailable {@link NativeException} unless the bridge can be linked here. */
    static void requireBridge() throws NativeException {
        Bridge.require();
    }

    /**
     * One libc {@code syscall} invocation. Throws on a {@code -1} result with the errno captured
     * by that same downcall.
     */
    private static long syscall(
            String operation, long number, long a0, long a1, long a2, long a3, long a4, long a5)
            throws NativeException {
        SyscallRoute route = ROUTE.get();
        if (route != null) {
            return route.invoke(operation, number, a0, a1, a2, a3, a4, a5);
        }
        return invokeDirect(operation, number, a0, a1, a2, a3, a4, a5);
    }

    /**
     * Executes one system call on a grant's restricted thread on behalf of the calling (driver)
     * thread, which waits for the result. Pointer arguments stay valid because the driver keeps
     * their arenas open until the call returns.
     */
    @FunctionalInterface
    interface SyscallRoute {
        long invoke(
                String operation, long number, long a0, long a1, long a2, long a3, long a4, long a5)
                throws NativeException;
    }

    private static final ThreadLocal<SyscallRoute> ROUTE = new ThreadLocal<>();

    /** Binds the calling thread so that every system call it issues runs through {@code route}. */
    static void bindRoute(SyscallRoute route) {
        ROUTE.set(java.util.Objects.requireNonNull(route, "route"));
    }

    /** The downcall itself, on the calling thread; errno is captured by the same downcall. */
    static long invokeDirect(
            String operation, long number, long a0, long a1, long a2, long a3, long a4, long a5)
            throws NativeException {
        Bridge bridge = Bridge.require();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment state = arena.allocate(bridge.captureState);
            long result;
            try {
                result =
                        (long) bridge.syscall.invokeExact(state, number, a0, a1, a2, a3, a4, a5);
            } catch (Throwable linkFailure) {
                throw new NativeException("native downcall failed for " + operation, linkFailure);
            }
            if (result == -1L) {
                throw new NativeException(operation, (int) bridge.errno.get(state, 0L));
            }
            if (result < -1L) {
                throw new NativeException(
                        operation + " returned an invalid libc result " + result, null);
            }
            return result;
        }
    }

    /** Repeats only operations whose interruption guarantees no effect (bounded). */
    private static long syscallRestartable(
            String operation, long number, long a0, long a1, long a2, long a3, long a4, long a5)
            throws NativeException {
        for (int attempt = 0; ; attempt++) {
            try {
                return syscall(operation, number, a0, a1, a2, a3, a4, a5);
            } catch (NativeException failure) {
                if (failure.errno() != EINTR || attempt >= MAX_INTERRUPT_RETRIES) {
                    throw failure;
                }
            }
        }
    }

    private static SyscallTable table() throws NativeException {
        return Bridge.require().table;
    }

    private static int descriptor(long result) throws NativeException {
        if (result < 0 || result > Integer.MAX_VALUE) {
            throw new NativeException("kernel returned an invalid descriptor " + result, null);
        }
        return (int) result;
    }

    // ----- descriptors -----

    /**
     * Host selection of the grant root: one {@code openat(AT_FDCWD)} of the absolute path as a
     * directory, refusing a final symbolic link ({@code O_NOFOLLOW} with {@code O_DIRECTORY}
     * fails with {@code ENOTDIR} or {@code ELOOP}; the link is never followed).
     * Intermediate host components are resolved by the kernel once, at this instant; the
     * returned descriptor, never the text, is the authority from then on.
     */
    static int openRootDirectory(String absolutePath) throws NativeException {
        if (!absolutePath.startsWith("/")) {
            throw new NativeException("grant root must be absolute", EINVAL);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = ProtosLinuxConfinedFilesystemNames.cString(arena, absolutePath);
            return descriptor(
                    syscall(
                            "openat(root)",
                            table().openat(),
                            AT_FDCWD,
                            path.address(),
                            O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC,
                            0,
                            0,
                            0));
        }
    }

    /**
     * Selects a real descendant of {@code directoryFd} through {@code openat2} with all four
     * {@link #RESOLVE_CONFINED} restrictions; {@code O_CLOEXEC} and {@code O_NOFOLLOW} are always
     * added. The open itself is the acquisition, never a pre-check.
     *
     * <p>{@code EINTR} and {@code EAGAIN} (a concurrent rename/mount detected by the scoped
     * lookup) are retried only for a non-creating open, which cannot have published an effect.
     * A creating open is never repeated here; its caller decides.
     */
    static int openBeneath(int directoryFd, String relativePath, int flags, int mode)
            throws NativeException {
        int effectiveFlags = flags | O_CLOEXEC | O_NOFOLLOW;
        boolean creating = (effectiveFlags & O_CREAT) != 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = ProtosLinuxConfinedFilesystemNames.cString(arena, relativePath);
            MemorySegment how = arena.allocate(OPEN_HOW);
            how.set(ValueLayout.JAVA_LONG, 0, Integer.toUnsignedLong(effectiveFlags));
            how.set(ValueLayout.JAVA_LONG, 8, creating ? Integer.toUnsignedLong(mode) : 0L);
            how.set(ValueLayout.JAVA_LONG, 16, RESOLVE_CONFINED);
            for (int attempt = 0; ; attempt++) {
                try {
                    return descriptor(
                            syscall(
                                    "openat2",
                                    table().openat2(),
                                    directoryFd,
                                    path.address(),
                                    how.address(),
                                    OPEN_HOW.byteSize(),
                                    0,
                                    0));
                } catch (NativeException failure) {
                    boolean transientFailure =
                            failure.errno() == EINTR || failure.errno() == EAGAIN;
                    if (creating || !transientFailure || attempt >= MAX_INTERRUPT_RETRIES) {
                        throw failure;
                    }
                }
            }
        }
    }

    /** Closes once; never retried, because Linux releases the descriptor even on EINTR. */
    static void close(int fd) throws NativeException {
        syscall("close", table().close(), fd, 0, 0, 0, 0, 0);
    }

    static Stat fstat(int fd) throws NativeException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment stat = arena.allocate(STAT_X86_64);
            syscallRestartable("fstat", table().fstat(), fd, stat.address(), 0, 0, 0, 0);
            return new Stat(
                    stat.get(ValueLayout.JAVA_INT, field(STAT_X86_64, "st_mode")),
                    stat.get(ValueLayout.JAVA_LONG, field(STAT_X86_64, "st_size")),
                    stat.get(ValueLayout.JAVA_LONG, field(STAT_X86_64, "st_dev")),
                    stat.get(ValueLayout.JAVA_LONG, field(STAT_X86_64, "st_ino")));
        }
    }

    private static long field(StructLayout layout, String name) {
        return layout.byteOffset(MemoryLayout.PathElement.groupElement(name));
    }

    /** Positioned read into {@code buffer}; returns 0 only at end of file. */
    static long pread(int fd, MemorySegment buffer, long offset) throws NativeException {
        return syscallRestartable(
                "pread64", table().pread64(), fd, buffer.address(), buffer.byteSize(), offset, 0, 0);
    }

    /** Positioned write from {@code buffer}; returns the accepted byte count. */
    static long pwrite(int fd, MemorySegment buffer, long offset) throws NativeException {
        return syscallRestartable(
                "pwrite64",
                table().pwrite64(),
                fd,
                buffer.address(),
                buffer.byteSize(),
                offset,
                0,
                0);
    }

    static void ftruncate(int fd, long size) throws NativeException {
        syscallRestartable("ftruncate", table().ftruncate(), fd, size, 0, 0, 0, 0);
    }

    static void fsync(int fd) throws NativeException {
        syscallRestartable("fsync", table().fsync(), fd, 0, 0, 0, 0, 0);
    }

    /**
     * Reads every record of the directory {@code fd} with {@code getdents64}. Each returned name
     * and type come from the same record; a name that is not strict UTF-8 fails the observation.
     */
    static List<DirectoryEntry> readDirectory(int fd) throws NativeException {
        ArrayList<DirectoryEntry> entries = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(DIRECTORY_BUFFER_BYTES, 8);
            while (true) {
                long filled =
                        syscallRestartable(
                                "getdents64",
                                table().getdents64(),
                                fd,
                                buffer.address(),
                                buffer.byteSize(),
                                0,
                                0,
                                0);
                if (filled == 0) {
                    return entries;
                }
                if (filled > buffer.byteSize()) {
                    throw new NativeException("getdents64 overran its buffer", null);
                }
                parseDirectoryRecords(buffer, filled, entries);
            }
        }
    }

    private static void parseDirectoryRecords(
            MemorySegment buffer, long filled, List<DirectoryEntry> entries)
            throws NativeException {
        long offset = 0;
        while (offset < filled) {
            if (filled - offset < DIRENT_NAME_OFFSET + 1) {
                throw new NativeException("truncated linux_dirent64 record", EINVAL);
            }
            int recordLength =
                    Short.toUnsignedInt(
                            buffer.get(
                                    ValueLayout.JAVA_SHORT_UNALIGNED,
                                    offset + DIRENT_RECLEN_OFFSET));
            if (recordLength <= DIRENT_NAME_OFFSET || offset + recordLength > filled) {
                throw new NativeException("invalid linux_dirent64 record length", EINVAL);
            }
            int type = Byte.toUnsignedInt(buffer.get(ValueLayout.JAVA_BYTE, offset + DIRENT_TYPE_OFFSET));
            long nameStart = offset + DIRENT_NAME_OFFSET;
            long nameEnd = nameStart;
            long recordEnd = offset + recordLength;
            while (nameEnd < recordEnd && buffer.get(ValueLayout.JAVA_BYTE, nameEnd) != 0) {
                nameEnd++;
            }
            if (nameEnd == recordEnd || nameEnd == nameStart) {
                throw new NativeException("linux_dirent64 name is not terminated", EINVAL);
            }
            byte[] raw = buffer.asSlice(nameStart, nameEnd - nameStart).toArray(ValueLayout.JAVA_BYTE);
            String name;
            try {
                name = ProtosLinuxConfinedFilesystemNames.strictUtf8(raw);
            } catch (CharacterCodingException unrepresentable) {
                throw new NativeException("directory entry name is not strict UTF-8", EILSEQ);
            }
            if (!name.equals(".") && !name.equals("..")) {
                entries.add(new DirectoryEntry(name, type));
            }
            offset = recordEnd;
        }
    }

    /** One {@code renameat2(flags = 0)} between two parent descriptors and exact entry names. */
    static void renameAt(int sourceDirectoryFd, String sourceName, int targetDirectoryFd, String targetName)
            throws NativeException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment source = ProtosLinuxConfinedFilesystemNames.cString(arena, sourceName);
            MemorySegment target = ProtosLinuxConfinedFilesystemNames.cString(arena, targetName);
            syscall(
                    "renameat2",
                    table().renameat2(),
                    sourceDirectoryFd,
                    source.address(),
                    targetDirectoryFd,
                    target.address(),
                    0,
                    0);
        }
    }

    /**
     * One {@code unlinkat} of the exact entry name. {@code directory} selects {@code AT_REMOVEDIR},
     * which removes only an empty directory. The final entry is never followed.
     */
    static void unlinkAt(int directoryFd, String name, boolean directory) throws NativeException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment entry = ProtosLinuxConfinedFilesystemNames.cString(arena, name);
            syscall(
                    "unlinkat",
                    table().unlinkat(),
                    directoryFd,
                    entry.address(),
                    directory ? AT_REMOVEDIR : 0,
                    0,
                    0,
                    0);
        }
    }

    // ----- Landlock and thread privileges -----

    /**
     * The Landlock ABI version actually served by the running kernel, queried with {@code
     * LANDLOCK_CREATE_RULESET_VERSION}; never inferred from the kernel release string.
     */
    static int landlockAbiVersion() throws NativeException {
        long version =
                syscall(
                        "landlock_create_ruleset(version)",
                        table().landlockCreateRuleset(),
                        0,
                        0,
                        LANDLOCK_CREATE_RULESET_VERSION,
                        0,
                        0,
                        0);
        if (version < 1 || version > Integer.MAX_VALUE) {
            throw new NativeException("kernel reported an invalid Landlock ABI " + version, null);
        }
        return (int) version;
    }

    static int createRuleset(long handledAccessFs) throws NativeException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment attr = arena.allocate(LANDLOCK_RULESET_ATTR);
            attr.set(ValueLayout.JAVA_LONG, field(LANDLOCK_RULESET_ATTR, "handled_access_fs"), handledAccessFs);
            return descriptor(
                    syscall(
                            "landlock_create_ruleset",
                            table().landlockCreateRuleset(),
                            attr.address(),
                            LANDLOCK_RULESET_ATTR.byteSize(),
                            0,
                            0,
                            0,
                            0));
        }
    }

    static void addPathBeneathRule(int rulesetFd, int parentFd, long allowedAccess)
            throws NativeException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment attr = arena.allocate(LANDLOCK_PATH_BENEATH_ATTR);
            attr.set(ValueLayout.JAVA_LONG, field(LANDLOCK_PATH_BENEATH_ATTR, "allowed_access"), allowedAccess);
            attr.set(ValueLayout.JAVA_INT, field(LANDLOCK_PATH_BENEATH_ATTR, "parent_fd"), parentFd);
            syscall(
                    "landlock_add_rule",
                    table().landlockAddRule(),
                    rulesetFd,
                    LANDLOCK_RULE_PATH_BENEATH,
                    attr.address(),
                    0,
                    0,
                    0);
        }
    }

    /** Sets {@code no_new_privs} on the calling thread, as unprivileged Landlock requires. */
    static void setNoNewPrivileges() throws NativeException {
        syscall("prctl(PR_SET_NO_NEW_PRIVS)", table().prctl(), PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0, 0);
    }

    /** Restricts the calling thread (only) with {@code rulesetFd}. */
    static void restrictSelf(int rulesetFd) throws NativeException {
        syscall("landlock_restrict_self", table().landlockRestrictSelf(), rulesetFd, 0, 0, 0, 0, 0);
    }

    /**
     * Opens a directory by absolute path for reading ({@code O_RDONLY | O_DIRECTORY}, not {@code
     * O_PATH}), an operation subject to the handled {@code READ_DIR} right. Used only by negative
     * tests on directories outside a grant; installation never depends on it.
     */
    static int openAbsoluteDirectory(String absolutePath) throws NativeException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment path = ProtosLinuxConfinedFilesystemNames.cString(arena, absolutePath);
            return descriptor(
                    syscall(
                            "openat(probe)",
                            table().openat(),
                            AT_FDCWD,
                            path.address(),
                            O_RDONLY | O_DIRECTORY | O_CLOEXEC,
                            0,
                            0,
                            0));
        }
    }

    /**
     * Links the bridge and loads this class's nested types on the calling thread, so a thread
     * that is about to be restricted never needs to load them later (see the backend).
     */
    static void preloadForRestrictedThread() throws NativeException {
        Bridge.require();
        new Stat(0, 0, 0, 0).regular();
        ProtosLinuxConfinedFilesystemNames.relativePath(List.of());
        new DirectoryEntry(".", DT_UNKNOWN).name();
        new NativeException("preload", 0).errno();
        ProtosLinuxConfinedFilesystemNames.isEntryName("preload");
    }

    /** Best-effort close for cleanup paths that cannot report a failure. */
    static void closeQuietly(int fd) {
        if (fd < 0) {
            return;
        }
        try {
            close(fd);
        } catch (NativeException ignored) {
            // The descriptor is released by Linux even when close reports an error.
        }
    }
}
