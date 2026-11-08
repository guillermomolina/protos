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

/**
 * I156 / PLAT072-E Landlock policy of one confined Filesystem grant.
 *
 * <p>The raw Landlock and {@code prctl} system calls live in {@link
 * ProtosLinuxConfinedFilesystemNative}; this class owns what is granted and how the restriction
 * is installed and confirmed on the grant's worker thread. The ABI is always queried from the
 * running kernel ({@code LANDLOCK_CREATE_RULESET_VERSION}), never inferred from its release.
 */
final class ProtosLinuxConfinedFilesystemLandlock {
    private ProtosLinuxConfinedFilesystemLandlock() {}

    static final long LANDLOCK_ACCESS_FS_EXECUTE = 1L << 0;
    static final long LANDLOCK_ACCESS_FS_WRITE_FILE = 1L << 1;
    static final long LANDLOCK_ACCESS_FS_READ_FILE = 1L << 2;
    static final long LANDLOCK_ACCESS_FS_READ_DIR = 1L << 3;
    static final long LANDLOCK_ACCESS_FS_REMOVE_DIR = 1L << 4;
    static final long LANDLOCK_ACCESS_FS_REMOVE_FILE = 1L << 5;
    /** ABI 1: rights {@code EXECUTE} through {@code MAKE_SYM}. */
    private static final long LANDLOCK_ACCESS_FS_ABI_1 = (1L << 13) - 1;
    static final long LANDLOCK_ACCESS_FS_REFER = 1L << 13;
    static final long LANDLOCK_ACCESS_FS_TRUNCATE = 1L << 14;
    private static final long LANDLOCK_ACCESS_FS_IOCTL_DEV = 1L << 15;
    /** First ABI that provides both {@code REFER} (ABI 2) and {@code TRUNCATE} (ABI 3). */
    static final int MINIMUM_LANDLOCK_ABI = 3;

    /** Every filesystem access right defined by the given Landlock ABI. */
    static long handledFilesystemAccess(int abi) {
        long handled = LANDLOCK_ACCESS_FS_ABI_1;
        if (abi >= 2) {
            handled |= LANDLOCK_ACCESS_FS_REFER;
        }
        if (abi >= 3) {
            handled |= LANDLOCK_ACCESS_FS_TRUNCATE;
        }
        if (abi >= 5) {
            handled |= LANDLOCK_ACCESS_FS_IOCTL_DEV;
        }
        return handled;
    }

    /** Entry-creation rights {@code MAKE_CHAR} through {@code MAKE_SYM} (ABI 1, bits 6 to 12). */
    static final long LANDLOCK_ACCESS_FS_MAKE_ANY = ((1L << 13) - 1) & ~((1L << 6) - 1);

    /**
     * Access rights granted beneath the grant root: read, write, enumerate, remove entries,
     * reparent/replace ({@code REFER}), truncate, and every {@code MAKE_*} right.
     *
     * <p>Landlock models a rename as removal from the source plus creation of an entry of the
     * same kind in the target, so {@code replace} of a directory, link or special entry
     * (FILESYSTEM.md §20.3) requires the matching {@code MAKE_*} right. The backend only ever
     * creates regular files ({@code O_CREAT}); it never issues {@code mkdirat}, {@code symlinkat}
     * or {@code mknodat}, so these rights add no Protos-visible operation. Execution and device
     * ioctl are never granted, and nothing outside the root is.
     */
    static final long GRANT_ACCESS =
            LANDLOCK_ACCESS_FS_READ_FILE
                    | LANDLOCK_ACCESS_FS_WRITE_FILE
                    | LANDLOCK_ACCESS_FS_READ_DIR
                    | LANDLOCK_ACCESS_FS_REMOVE_DIR
                    | LANDLOCK_ACCESS_FS_REMOVE_FILE
                    | LANDLOCK_ACCESS_FS_MAKE_ANY
                    | LANDLOCK_ACCESS_FS_REFER
                    | LANDLOCK_ACCESS_FS_TRUNCATE;

    /**
     * Installs the grant's Landlock domain on the <em>calling thread</em>.
     *
     * <p>The ruleset handles every filesystem right of the running ABI (at least {@link
     * #MINIMUM_LANDLOCK_ABI}) and allows only {@link #GRANT_ACCESS} beneath {@code rootFd}. The
     * thread then sets {@code no_new_privs} and calls {@code landlock_restrict_self}. Installation
     * is established by the results of those system calls themselves: each one either succeeds or
     * throws, and only a successful {@code landlock_restrict_self} lets the worker admit work. No
     * probe of an unrelated path is used, so no grant root is restricted by a diagnostic.
     *
     * @throws ProtosLinuxConfinedFilesystemNative.NativeException when any step fails; the caller
     *     must then admit no work
     */
    static void restrictCurrentThreadBeneath(int rootFd)
            throws ProtosLinuxConfinedFilesystemNative.NativeException {
        int abi = ProtosLinuxConfinedFilesystemNative.landlockAbiVersion();
        if (abi < MINIMUM_LANDLOCK_ABI) {
            throw new ProtosLinuxConfinedFilesystemNative.NativeException(
                    "Landlock ABI " + abi + " lacks REFER/TRUNCATE (need "
                            + MINIMUM_LANDLOCK_ABI + ")",
                    null);
        }
        if (!ProtosLinuxConfinedFilesystemNative.fstat(rootFd).directory()) {
            throw new ProtosLinuxConfinedFilesystemNative.NativeException(
                    "grant root descriptor is not a directory",
                    ProtosLinuxConfinedFilesystemNative.ENOTDIR);
        }
        int rulesetFd =
                ProtosLinuxConfinedFilesystemNative.createRuleset(handledFilesystemAccess(abi));
        try {
            ProtosLinuxConfinedFilesystemNative.addPathBeneathRule(rulesetFd, rootFd, GRANT_ACCESS);
            ProtosLinuxConfinedFilesystemNative.setNoNewPrivileges();
            ProtosLinuxConfinedFilesystemNative.restrictSelf(rulesetFd);
        } finally {
            ProtosLinuxConfinedFilesystemNative.closeQuietly(rulesetFd);
        }
    }
}
