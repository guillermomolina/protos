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

/*
 * PLAT074-D1 / D1-R1 experimental pre-exec Landlock launcher for the isolated
 * SQLite helper. TEST FIXTURE ONLY: compiled into a JUnit temporary directory by
 * ProtosI144FfmFixtureAbiTest; never a provider, never part of a distribution.
 *
 * Independent implementation of the documented Linux userspace procedure
 * (Documentation/userspace-api/landlock.rst, "Landlock: unprivileged access
 * control"): landlock_create_ruleset, landlock_add_rule (path beneath),
 * prctl(PR_SET_NO_NEW_PRIVS), landlock_restrict_self, close_range, then
 * execve. No code is copied from the kernel's samples/landlock/sandboxer.c; only the public UAPI
 * constants and structure layouts are restated below.
 *
 * The sequence runs in this single-threaded C process before the JVM exists,
 * so the restriction covers the exec'd JVM and every thread it later creates
 * (Landlock domains are inherited across clone and preserved across execve).
 *
 * D1-R1 profile (Landlock ABI 10 contract, see PROFILE_ABI):
 *   - every filesystem right through ABI 10 is handled, including
 *     RESOLVE_UNIX (ABI 9): connecting to a pathname UNIX socket is denied
 *     everywhere, even beneath a readable grant;
 *   - TCP bind/connect and UDP bind/connect-send are handled with no port
 *     rule: every such operation is denied (no loopback exception);
 *   - the abstract-UNIX-socket and signal scopes are set: the helper cannot
 *     connect to abstract sockets or signal processes outside its domain. The
 *     unrestricted parent can still signal (and kill) the helper;
 *   - every inherited descriptor above 2 is closed with close_range or the
 *     launch is refused. Descriptors 0, 1 and 2 stay open: they are the
 *     harness's explicit communication channels and keep that authority;
 *   - the environment is an explicit allowlist (PROTOS_HOME, HOME, TMPDIR,
 *     LANG, PATH) with HOME and TMPDIR equal to the private --tmp root.
 * This is not a container and not a general syscall filter. Paths are
 * validated before the ruleset exists (realpath, then open(O_PATH)); that
 * check does not remove every race against concurrent host namespace
 * changes, so the host that provisions the paths stays trusted.
 *
 * Usage (every path absolute and canonical; nothing comes from helper IPC):
 *
 *   sandbox-exec [--require-abi N] [--abi-only]
 *       --db DIR --tmp DIR
 *       [--rx PATH]... [--ro PATH]... [--dev PATH]...
 *       [--env NAME=VALUE]...
 *       -- EXECUTABLE [ARGUMENT]...
 *
 *   EXECUTABLE is the JVM of the helper or a native test probe; it must be
 *   absolute, canonical, a regular executable file beneath an --rx grant and
 *   outside both write roots. It is exec'd directly (no shell, no lookup).
 *
 *   --rx   read (and execute) beneath PATH: the JDK, the ELF interpreter or the
 *          probe directory. Never "/".
 *   --ro   read beneath PATH: class path entries, Protos sources, libraries,
 *          or one read-only character device (for example /dev/urandom).
 *          Never "/". Reading a directory never authorizes connecting to a
 *          socket inside it (RESOLVE_UNIX is granted nowhere).
 *   --dev  read/write of one character device (only /dev/null in D1).
 *   --db   the only SQLite database directory (create/write/truncate/unlink
 *          regular files; no directories, no links, no refer).
 *   --tmp  the private JVM temporary directory.
 *   --env  the complete environment of the executable; nothing is inherited.
 *          Only the allowlisted names, each at most once.
 *   --require-abi N  raises the minimum Landlock ABI (never below
 *          PROFILE_ABI). A requirement above PROFILE_ABI asks for guarantees
 *          this launcher does not implement and is refused. Used by the
 *          refusal tests only; it can never weaken the profile.
 *   --abi-only  validates platform support only, then exits 0 without exec.
 *
 * There is no unconfined fallback: any failure exits non-zero before execve.
 */

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

/* Landlock UAPI (include/uapi/linux/landlock.h), restated to stay independent
 * of the installed kernel headers. */
#define LL_CREATE_RULESET_VERSION (1U << 0)
#define LL_RULE_PATH_BENEATH 1

#define LL_FS_EXECUTE (1ULL << 0)
#define LL_FS_WRITE_FILE (1ULL << 1)
#define LL_FS_READ_FILE (1ULL << 2)
#define LL_FS_READ_DIR (1ULL << 3)
#define LL_FS_REMOVE_DIR (1ULL << 4)
#define LL_FS_REMOVE_FILE (1ULL << 5)
#define LL_FS_MAKE_CHAR (1ULL << 6)
#define LL_FS_MAKE_DIR (1ULL << 7)
#define LL_FS_MAKE_REG (1ULL << 8)
#define LL_FS_MAKE_SOCK (1ULL << 9)
#define LL_FS_MAKE_FIFO (1ULL << 10)
#define LL_FS_MAKE_BLOCK (1ULL << 11)
#define LL_FS_MAKE_SYM (1ULL << 12)
#define LL_FS_REFER (1ULL << 13)    /* ABI 2 */
#define LL_FS_TRUNCATE (1ULL << 14) /* ABI 3 */
#define LL_FS_IOCTL_DEV (1ULL << 15) /* ABI 5 */
#define LL_FS_RESOLVE_UNIX (1ULL << 16) /* ABI 9 */

#define LL_NET_BIND_TCP (1ULL << 0)         /* ABI 4 */
#define LL_NET_CONNECT_TCP (1ULL << 1)      /* ABI 4 */
#define LL_NET_BIND_UDP (1ULL << 2)         /* ABI 10 */
#define LL_NET_CONNECT_SEND_UDP (1ULL << 3) /* ABI 10 */

#define LL_SCOPE_ABSTRACT_UNIX_SOCKET (1ULL << 0) /* ABI 6 */
#define LL_SCOPE_SIGNAL (1ULL << 1)               /* ABI 6 */

#ifndef __NR_landlock_create_ruleset
#define __NR_landlock_create_ruleset 444
#endif
#ifndef __NR_landlock_add_rule
#define __NR_landlock_add_rule 445
#endif
#ifndef __NR_landlock_restrict_self
#define __NR_landlock_restrict_self 446
#endif
#ifndef __NR_close_range
#define __NR_close_range 436
#endif

/* The ruleset attribute as of the ABI 10 contract implemented here. The quiet
 * fields are always zero: audit logging is never silenced. Its size is the one
 * passed to landlock_create_ruleset. */
struct ll_ruleset_attr {
    uint64_t handled_access_fs;
    uint64_t handled_access_net;
    uint64_t scoped;
    uint64_t quiet_access_fs;
    uint64_t quiet_access_net;
    uint64_t quiet_scoped;
};

struct ll_path_beneath_attr {
    uint64_t allowed_access;
    int32_t parent_fd;
} __attribute__((packed));

/* The reviewed UAPI contract of this profile: ABI 3 TRUNCATE, ABI 4 TCP,
 * ABI 5 IOCTL_DEV, ABI 6 scopes, ABI 9 RESOLVE_UNIX, ABI 10 UDP. It is both
 * the floor (no weaker profile is ever installed) and the ceiling of what is
 * claimed: rights introduced after ABI 10 are unknown here and not handled. */
#define PROFILE_ABI 10L
#define MINIMUM_ABI PROFILE_ABI

#define HANDLED_FS (LL_FS_EXECUTE | LL_FS_WRITE_FILE | LL_FS_READ_FILE | LL_FS_READ_DIR | \
                    LL_FS_REMOVE_DIR | LL_FS_REMOVE_FILE | LL_FS_MAKE_CHAR | LL_FS_MAKE_DIR | \
                    LL_FS_MAKE_REG | LL_FS_MAKE_SOCK | LL_FS_MAKE_FIFO | LL_FS_MAKE_BLOCK | \
                    LL_FS_MAKE_SYM | LL_FS_REFER | LL_FS_TRUNCATE | LL_FS_IOCTL_DEV | \
                    LL_FS_RESOLVE_UNIX)
#define HANDLED_NET (LL_NET_BIND_TCP | LL_NET_CONNECT_TCP | LL_NET_BIND_UDP | \
                     LL_NET_CONNECT_SEND_UDP)
#define SCOPED (LL_SCOPE_ABSTRACT_UNIX_SOCKET | LL_SCOPE_SIGNAL)

#define EXIT_INVALID 64     /* invalid arguments or policy paths */
#define EXIT_UNSUPPORTED 69 /* Landlock missing or ABI insufficient */
#define EXIT_INSTALL 70     /* a rule, no_new_privs or the restriction failed */
#define EXIT_EXEC 71        /* execve failed after the restriction */

/* Rights a rule may grant on a non-directory inode. */
#define FILE_RIGHTS (LL_FS_EXECUTE | LL_FS_WRITE_FILE | LL_FS_READ_FILE | \
                     LL_FS_TRUNCATE | LL_FS_IOCTL_DEV)

#define READ_RIGHTS (LL_FS_READ_FILE | LL_FS_READ_DIR)
#define DB_RIGHTS (READ_RIGHTS | LL_FS_WRITE_FILE | LL_FS_TRUNCATE | \
                   LL_FS_MAKE_REG | LL_FS_REMOVE_FILE)
#define TMP_RIGHTS (DB_RIGHTS | LL_FS_MAKE_DIR | LL_FS_REMOVE_DIR | LL_FS_REFER)

#define MAX_RULES 1024
#define MAX_ENV 5
#define MAX_ENV_NAME 64

/* The complete environment vocabulary of this profile. */
static const char *const ALLOWED_ENV[] = {"PROTOS_HOME", "HOME", "TMPDIR", "LANG", "PATH"};
/* Named explicitly so a refusal says why; any other LD_ name is refused too. */
static const char *const FORBIDDEN_ENV[] = {"JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS",
                                            "JDK_JAVA_OPTIONS", "LD_PRELOAD",
                                            "LD_LIBRARY_PATH", "LD_AUDIT", "GCONV_PATH"};

enum kind { KIND_RX, KIND_RO, KIND_DEV, KIND_DB, KIND_TMP };

struct rule {
    const char *path;
    enum kind kind;
};

static struct rule rules[MAX_RULES];
static size_t rule_count;
static char *environment[MAX_ENV + 1];
static size_t environment_count;

static void refuse(int status, const char *reason, const char *detail) {
    if (detail != NULL) {
        fprintf(stderr, "SANDBOX_REFUSED %s %s\n", reason, detail);
    } else {
        fprintf(stderr, "SANDBOX_REFUSED %s\n", reason);
    }
    fflush(stderr);
    exit(status);
}

static void refuse_errno(int status, const char *reason, const char *detail) {
    char text[PATH_MAX + 64];
    snprintf(text, sizeof text, "%s errno=%d", detail == NULL ? "-" : detail, errno);
    refuse(status, reason, text);
}

/* Absolute, existing and already canonical: realpath must return it verbatim. */
static void require_canonical(const char *path) {
    if (path == NULL || path[0] != '/') refuse(EXIT_INVALID, "PATH_NOT_ABSOLUTE", path);
    char resolved[PATH_MAX];
    if (realpath(path, resolved) == NULL) refuse_errno(EXIT_INVALID, "PATH_INVALID", path);
    if (strcmp(resolved, path) != 0) refuse(EXIT_INVALID, "PATH_NOT_CANONICAL", path);
}

/* True when `ancestor` equals `path` or is a directory prefix of it. */
static int contains(const char *ancestor, const char *path) {
    size_t length = strlen(ancestor);
    if (strcmp(ancestor, "/") == 0) return 1;
    if (strncmp(ancestor, path, length) != 0) return 0;
    return path[length] == '\0' || path[length] == '/';
}

static void add_policy(const char *option, const char *path) {
    if (rule_count == MAX_RULES) refuse(EXIT_INVALID, "TOO_MANY_RULES", option);
    enum kind kind;
    if (strcmp(option, "--rx") == 0) kind = KIND_RX;
    else if (strcmp(option, "--ro") == 0) kind = KIND_RO;
    else if (strcmp(option, "--dev") == 0) kind = KIND_DEV;
    else if (strcmp(option, "--db") == 0) kind = KIND_DB;
    else kind = KIND_TMP;
    rules[rule_count].path = path;
    rules[rule_count].kind = kind;
    rule_count++;
}

static long parse_abi(const char *text) {
    char *end = NULL;
    errno = 0;
    long value = strtol(text, &end, 10);
    if (errno != 0 || end == text || *end != '\0') refuse(EXIT_INVALID, "INVALID_ABI", text);
    /* The requirement may only be raised: a lower floor would drop rights. */
    if (value < MINIMUM_ABI) refuse(EXIT_INVALID, "ABI_BELOW_PROFILE_FLOOR", text);
    return value;
}

static int name_is(const char *name, size_t length, const char *candidate) {
    return strlen(candidate) == length && strncmp(name, candidate, length) == 0;
}

/* Syntax and vocabulary of one --env entry; values tied to the policy are
 * checked by validate_environment once the roots are known. */
static void add_environment(const char *entry) {
    const char *equals = strchr(entry, '=');
    if (equals == NULL) refuse(EXIT_INVALID, "ENV_MISSING_SEPARATOR", entry);
    size_t length = (size_t)(equals - entry);
    if (length == 0) refuse(EXIT_INVALID, "ENV_EMPTY_NAME", entry);
    if (length >= MAX_ENV_NAME) refuse(EXIT_INVALID, "ENV_UNKNOWN", "-");
    char name[MAX_ENV_NAME];
    memcpy(name, entry, length);
    name[length] = '\0';
    for (size_t i = 0; i < sizeof FORBIDDEN_ENV / sizeof FORBIDDEN_ENV[0]; i++) {
        if (name_is(entry, length, FORBIDDEN_ENV[i])) refuse(EXIT_INVALID, "ENV_FORBIDDEN", name);
    }
    if (strncmp(name, "LD_", 3) == 0) refuse(EXIT_INVALID, "ENV_FORBIDDEN", name);
    int allowed = 0;
    for (size_t i = 0; i < sizeof ALLOWED_ENV / sizeof ALLOWED_ENV[0]; i++) {
        if (name_is(entry, length, ALLOWED_ENV[i])) allowed = 1;
    }
    if (!allowed) refuse(EXIT_INVALID, "ENV_UNKNOWN", name);
    for (size_t i = 0; i < environment_count; i++) {
        if (strncmp(environment[i], entry, length + 1) == 0) refuse(EXIT_INVALID, "ENV_DUPLICATE", name);
    }
    const char *value = equals + 1;
    if (*value == '\0') refuse(EXIT_INVALID, "ENV_INVALID_VALUE", name);
    for (const unsigned char *c = (const unsigned char *)value; *c != '\0'; c++) {
        if (*c < 0x20 || *c == 0x7f) refuse(EXIT_INVALID, "ENV_INVALID_VALUE", name);
    }
    if (environment_count == MAX_ENV) refuse(EXIT_INVALID, "TOO_MANY_ENV", name);
    environment[environment_count++] = (char *)entry;
}

static const char *environment_value(const char *name) {
    size_t length = strlen(name);
    for (size_t i = 0; i < environment_count; i++) {
        if (strncmp(environment[i], name, length) == 0 && environment[i][length] == '=')
            return environment[i] + length + 1;
    }
    return NULL;
}

/* HOME and TMPDIR must name the private temporary root and nothing else. */
static void validate_environment(const char *tmp) {
    const char *private_names[] = {"HOME", "TMPDIR"};
    for (size_t i = 0; i < 2; i++) {
        const char *value = environment_value(private_names[i]);
        if (value == NULL || strcmp(value, tmp) != 0)
            refuse(EXIT_INVALID, "ENV_NOT_PRIVATE_TMP", private_names[i]);
    }
}

/* Validates every policy path before any Landlock state exists. The check is
 * done by name before restriction; it does not by itself exclude concurrent
 * replacement of a path by the (trusted) host. */
static void validate(const char *executable) {
    const char *db = NULL;
    const char *tmp = NULL;
    for (size_t i = 0; i < rule_count; i++) {
        struct stat status;
        require_canonical(rules[i].path);
        if (stat(rules[i].path, &status) != 0) refuse_errno(EXIT_INVALID, "PATH_INVALID", rules[i].path);
        switch (rules[i].kind) {
        case KIND_DB:
        case KIND_TMP:
            if (!S_ISDIR(status.st_mode)) refuse(EXIT_INVALID, "WRITE_ROOT_NOT_DIRECTORY", rules[i].path);
            if (strcmp(rules[i].path, "/") == 0) refuse(EXIT_INVALID, "WRITE_ROOT_IS_FILESYSTEM_ROOT", "/");
            if (rules[i].kind == KIND_DB) {
                if (db != NULL) refuse(EXIT_INVALID, "DUPLICATE_DB_ROOT", rules[i].path);
                db = rules[i].path;
            } else {
                if (tmp != NULL) refuse(EXIT_INVALID, "DUPLICATE_TMP_ROOT", rules[i].path);
                tmp = rules[i].path;
            }
            break;
        case KIND_DEV:
            if (!S_ISCHR(status.st_mode)) refuse(EXIT_INVALID, "DEVICE_NOT_CHARACTER", rules[i].path);
            break;
        default:
            if (strcmp(rules[i].path, "/") == 0) refuse(EXIT_INVALID, "READ_ROOT_IS_FILESYSTEM_ROOT", "/");
            /* Read-only character devices (for example /dev/urandom) never
             * receive WRITE_FILE or IOCTL_DEV. */
            if (!S_ISDIR(status.st_mode) && !S_ISREG(status.st_mode) &&
                    !(rules[i].kind == KIND_RO && S_ISCHR(status.st_mode)))
                refuse(EXIT_INVALID, "READ_PATH_NOT_FILE_OR_DIRECTORY", rules[i].path);
            break;
        }
    }
    if (db == NULL) refuse(EXIT_INVALID, "MISSING_DB_ROOT", NULL);
    if (tmp == NULL) refuse(EXIT_INVALID, "MISSING_TMP_ROOT", NULL);
    if (contains(db, tmp) || contains(tmp, db)) refuse(EXIT_INVALID, "WRITE_ROOTS_NOT_INDEPENDENT", db);
    /* A writable root must not contain (or be) any trusted runtime path. */
    for (size_t i = 0; i < rule_count; i++) {
        if (rules[i].kind == KIND_DB || rules[i].kind == KIND_TMP) continue;
        if (contains(db, rules[i].path) || contains(tmp, rules[i].path))
            refuse(EXIT_INVALID, "WRITE_ROOT_CONTAINS_RUNTIME_PATH", rules[i].path);
    }
    validate_environment(tmp);
    require_canonical(executable);
    struct stat status;
    if (stat(executable, &status) != 0 || !S_ISREG(status.st_mode) || access(executable, X_OK) != 0)
        refuse(EXIT_INVALID, "EXECUTABLE_NOT_EXECUTABLE", executable);
    int beneath_rx = 0;
    for (size_t i = 0; i < rule_count; i++) {
        if (rules[i].kind == KIND_RX && contains(rules[i].path, executable)) beneath_rx = 1;
    }
    if (!beneath_rx) refuse(EXIT_INVALID, "EXECUTABLE_NOT_IN_EXECUTABLE_GRANT", executable);
    if (contains(db, executable) || contains(tmp, executable))
        refuse(EXIT_INVALID, "EXECUTABLE_IN_WRITE_ROOT", executable);
}

static long landlock_abi(void) {
    long abi = syscall(__NR_landlock_create_ruleset, NULL, 0, LL_CREATE_RULESET_VERSION);
    if (abi < 0) {
        if (errno == ENOSYS) refuse(EXIT_UNSUPPORTED, "LANDLOCK_UNAVAILABLE", "ENOSYS");
        if (errno == EOPNOTSUPP) refuse(EXIT_UNSUPPORTED, "LANDLOCK_UNAVAILABLE", "DISABLED");
        refuse_errno(EXIT_UNSUPPORTED, "LANDLOCK_UNAVAILABLE", NULL);
    }
    return abi;
}

static uint64_t rights_for(enum kind kind, uint64_t handled) {
    switch (kind) {
    case KIND_RX: return (READ_RIGHTS | LL_FS_EXECUTE) & handled;
    case KIND_RO: return READ_RIGHTS & handled;
    case KIND_DEV: return (LL_FS_READ_FILE | LL_FS_WRITE_FILE | LL_FS_IOCTL_DEV) & handled;
    case KIND_DB: return DB_RIGHTS & handled;
    default: return TMP_RIGHTS & handled;
    }
}

static void add_rule(int ruleset, const struct rule *rule, uint64_t handled) {
    int fd = open(rule->path, O_PATH | O_CLOEXEC);
    if (fd < 0) refuse_errno(EXIT_INSTALL, "RULE_PATH_OPEN_FAILED", rule->path);
    struct stat status;
    if (fstat(fd, &status) != 0) refuse_errno(EXIT_INSTALL, "RULE_PATH_STAT_FAILED", rule->path);
    struct ll_path_beneath_attr beneath;
    beneath.allowed_access = rights_for(rule->kind, handled);
    if (!S_ISDIR(status.st_mode)) beneath.allowed_access &= FILE_RIGHTS;
    beneath.parent_fd = fd;
    if (syscall(__NR_landlock_add_rule, ruleset, LL_RULE_PATH_BENEATH, &beneath, 0) != 0)
        refuse_errno(EXIT_INSTALL, "RULE_INSTALL_FAILED", rule->path);
    if (close(fd) != 0) refuse_errno(EXIT_INSTALL, "RULE_PATH_CLOSE_FAILED", rule->path);
}

/* Leaves only the standard streams (the harness's explicit channels) for the
 * executable; every other inherited descriptor is closed (O_CLOEXEC alone is
 * not relied upon). There is no per-descriptor fallback: if close_range fails
 * for any reason, the launch is refused. */
static void close_inherited(void) {
    if (syscall(__NR_close_range, 3U, ~0U, 0U) != 0)
        refuse_errno(EXIT_INSTALL, "CLOSE_RANGE_FAILED", NULL);
}

int main(int argc, char **argv) {
    long required = MINIMUM_ABI;
    int abi_only = 0;
    int separator = -1;
    for (int i = 1; i < argc; i++) {
        const char *option = argv[i];
        if (strcmp(option, "--") == 0) {
            separator = i;
            break;
        }
        if (strcmp(option, "--abi-only") == 0) {
            abi_only = 1;
            continue;
        }
        if (i + 1 >= argc) refuse(EXIT_INVALID, "MISSING_OPTION_VALUE", option);
        const char *value = argv[++i];
        if (strcmp(option, "--require-abi") == 0) {
            required = parse_abi(value);
        } else if (strcmp(option, "--rx") == 0 || strcmp(option, "--ro") == 0 ||
                   strcmp(option, "--dev") == 0 || strcmp(option, "--db") == 0 ||
                   strcmp(option, "--tmp") == 0) {
            add_policy(option, value);
        } else if (strcmp(option, "--env") == 0) {
            add_environment(value);
        } else {
            refuse(EXIT_INVALID, "UNKNOWN_OPTION", option);
        }
    }
    environment[environment_count] = NULL;

    /* Platform guarantee first: an insufficient ABI is UNSUPPORTED, not a
     * reason to install a weaker profile. */
    long abi = landlock_abi();
    if (abi < required) {
        char text[64];
        snprintf(text, sizeof text, "abi=%ld required=%ld", abi, required);
        refuse(EXIT_UNSUPPORTED, "LANDLOCK_ABI_INSUFFICIENT", text);
    }
    /* A requirement beyond the reviewed contract would claim rights this
     * launcher does not handle: refuse rather than run a weaker profile. */
    if (required > PROFILE_ABI) {
        char text[64];
        snprintf(text, sizeof text, "required=%ld profile=%ld", required, PROFILE_ABI);
        refuse(EXIT_UNSUPPORTED, "PROFILE_DOES_NOT_COVER_ABI", text);
    }
    if (abi_only) {
        fprintf(stderr, "LANDLOCK_ABI %ld\n", abi);
        return 0;
    }

    if (separator < 0 || separator + 1 >= argc) refuse(EXIT_INVALID, "MISSING_COMMAND", NULL);
    const char *executable = argv[separator + 1];
    validate(executable);

    uint64_t handled = HANDLED_FS;
    struct ll_ruleset_attr attributes = {
        .handled_access_fs = HANDLED_FS,
        .handled_access_net = HANDLED_NET,
        .scoped = SCOPED,
        .quiet_access_fs = 0,
        .quiet_access_net = 0,
        .quiet_scoped = 0,
    };
    int ruleset = (int)syscall(__NR_landlock_create_ruleset, &attributes, sizeof attributes, 0U);
    if (ruleset < 0) refuse_errno(EXIT_INSTALL, "RULESET_CREATE_FAILED", NULL);
    for (size_t i = 0; i < rule_count; i++) add_rule(ruleset, &rules[i], handled);

    if (prctl(PR_SET_NO_NEW_PRIVS, 1L, 0L, 0L, 0L) != 0)
        refuse_errno(EXIT_INSTALL, "NO_NEW_PRIVS_FAILED", NULL);
    if (syscall(__NR_landlock_restrict_self, ruleset, 0U) != 0)
        refuse_errno(EXIT_INSTALL, "RESTRICT_SELF_FAILED", NULL);
    if (close(ruleset) != 0) refuse_errno(EXIT_INSTALL, "RULESET_CLOSE_FAILED", NULL);
    close_inherited();

    /* Diagnostic only: confinement is proven by allowed/denied native access. */
    fprintf(stderr, "LANDLOCK_PROFILE abi=%ld handled_fs=0x%llx handled_net=0x%llx scoped=0x%llx\n",
            PROFILE_ABI, (unsigned long long)HANDLED_FS, (unsigned long long)HANDLED_NET,
            (unsigned long long)SCOPED);
    /* A newer kernel is not a grant: rights it adds after PROFILE_ABI are not
     * handled by this profile and are therefore not claimed. */
    if (abi > PROFILE_ABI) fprintf(stderr, "LANDLOCK_UNREVIEWED_ABI abi=%ld profile=%ld\n", abi, PROFILE_ABI);
    fprintf(stderr, "LANDLOCK_APPLIED\n");
    fprintf(stderr, "LANDLOCK_ABI %ld\n", abi);
    fflush(stderr);

    execve(executable, &argv[separator + 1], environment);
    refuse_errno(EXIT_EXEC, "EXECVE_FAILED", executable);
    return EXIT_EXEC;
}
