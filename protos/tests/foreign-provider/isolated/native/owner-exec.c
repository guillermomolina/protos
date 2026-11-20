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
 * PLAT074-D3 experimental process-tree fixture. TEST FIXTURE ONLY: compiled
 * into a JUnit temporary directory by ProtosI144FfmFixtureAbiTest; never a
 * provider, never a library, never part of a distribution. It contains no SQL,
 * builds or parses no protocol message and makes no business decision.
 *
 * It establishes exactly one process tree and its descriptors, then gets out
 * of the way:
 *
 *   JUnit
 *     +-- owner-exec  --execve-->  Protos client JVM   (same PID)
 *           +-- fork --> sandbox-exec --execve--> Protos helper JVM
 *
 * Sequence (single-threaded until the final execve calls):
 *   1. two pipes (O_CLOEXEC): client->helper and helper->client;
 *   2. fork exactly once;
 *   3. child: stdin <- client->helper read end, stdout -> helper->client write
 *      end, every original pipe descriptor closed, then execv of the
 *      sandbox-exec launcher with "--expected-parent-pid <owner PID>" inserted
 *      as its first option. sandbox-exec sets PR_SET_PDEATHSIG(SIGKILL),
 *      verifies the parent and installs the D1-R1 Landlock profile before the
 *      helper JVM exists;
 *   4. parent: stdin <- helper->client read end, stdout -> client->helper write
 *      end, every original pipe descriptor closed, "OWNER_HELPER_PID <pid>" on
 *      stderr, then execv of the Protos client JVM. execve keeps the PID, so
 *      the client JVM is the helper's OS parent.
 *
 * PR_SET_PDEATHSIG(2const) is delivered when the *thread* that forked the
 * child terminates. That thread is this process's only thread; after execve it
 * is the JVM launcher's primordial thread, which waits for the Java main
 * thread and lives until the process exits. If it ended earlier the helper
 * would be killed early (fail-closed), never kept alive.
 *
 * stderr of both processes stays the harness pipe (diagnostics and
 * synchronization only). The harness's stdin/stdout pipes are replaced in both
 * processes, so the harness has no data channel and cannot relay requests.
 * There is no persistent supervisor, no watchdog, no polling loop, no shell,
 * no socket and no port. Any failure exits before an unprotected helper can
 * run: a helper is only ever exec'd through sandbox-exec with the custody
 * option, and if the client exec fails the forked child is killed.
 *
 * Usage (every executable absolute; exec'd directly, no PATH lookup):
 *
 *   owner-exec --owner-helper SANDBOX_EXEC [SANDBOX_ARGUMENT]...
 *              --owner-client CLIENT_EXECUTABLE [CLIENT_ARGUMENT]...
 */

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

#define EXIT_INVALID 64
#define EXIT_SETUP 70
#define EXIT_EXEC 71

static const char HELPER_MARKER[] = "--owner-helper";
static const char CLIENT_MARKER[] = "--owner-client";

static void refuse(int status, const char *reason, int error) {
    fprintf(stderr, "OWNER_REFUSED %s errno=%d\n", reason, error);
    fflush(stderr);
    exit(status);
}

/* In the forked child only: no stdio buffers or atexit handlers of the parent
 * may run twice. */
static void child_refuse(int status, const char *reason, int error) {
    char text[96];
    int length = snprintf(text, sizeof text, "OWNER_REFUSED %s errno=%d\n", reason, error);
    if (length > 0) {
        ssize_t ignored = write(2, text, (size_t)length);
        (void)ignored;
    }
    _exit(status);
}

int main(int argc, char **argv) {
    if (argc < 5 || strcmp(argv[1], HELPER_MARKER) != 0) refuse(EXIT_INVALID, "USAGE", 0);
    int marker = -1;
    for (int i = 2; i < argc; i++) {
        if (strcmp(argv[i], HELPER_MARKER) == 0) refuse(EXIT_INVALID, "DUPLICATE_HELPER_MARKER", 0);
        if (strcmp(argv[i], CLIENT_MARKER) == 0) {
            if (marker >= 0) refuse(EXIT_INVALID, "DUPLICATE_CLIENT_MARKER", 0);
            marker = i;
        }
    }
    if (marker <= 2 || marker + 1 >= argc) refuse(EXIT_INVALID, "USAGE", 0);
    if (argv[2][0] != '/' || argv[marker + 1][0] != '/') refuse(EXIT_INVALID, "EXECUTABLE_NOT_ABSOLUTE", 0);

    /* Helper argv: the launcher, the custody option, then the host's policy. */
    int helper_arguments = marker - 2;
    char **helper = calloc((size_t)helper_arguments + 3, sizeof *helper);
    if (helper == NULL) refuse(EXIT_SETUP, "ALLOCATION_FAILED", errno);
    char owner[32];
    snprintf(owner, sizeof owner, "%ld", (long)getpid());
    helper[0] = argv[2];
    helper[1] = "--expected-parent-pid";
    helper[2] = owner;
    for (int i = 1; i < helper_arguments; i++) helper[i + 2] = argv[2 + i];
    helper[helper_arguments + 2] = NULL;
    char **client = &argv[marker + 1];

    int to_helper[2];
    int from_helper[2];
    if (pipe2(to_helper, O_CLOEXEC) != 0) refuse(EXIT_SETUP, "PIPE_FAILED", errno);
    if (pipe2(from_helper, O_CLOEXEC) != 0) refuse(EXIT_SETUP, "PIPE_FAILED", errno);
    /* 0, 1 and 2 are the harness's pipes, so every new descriptor is above 2;
     * otherwise a dup2 below could overwrite a pipe end it still needs. */
    if (to_helper[0] <= 2 || to_helper[1] <= 2 || from_helper[0] <= 2 || from_helper[1] <= 2)
        refuse(EXIT_SETUP, "STANDARD_DESCRIPTOR_MISSING", 0);

    fflush(NULL);
    pid_t child = fork();
    if (child < 0) refuse(EXIT_SETUP, "FORK_FAILED", errno);
    if (child == 0) {
        if (dup2(to_helper[0], 0) != 0) child_refuse(EXIT_SETUP, "HELPER_DUP2_FAILED", errno);
        if (dup2(from_helper[1], 1) != 1) child_refuse(EXIT_SETUP, "HELPER_DUP2_FAILED", errno);
        if (close(to_helper[0]) != 0 || close(to_helper[1]) != 0 ||
                close(from_helper[0]) != 0 || close(from_helper[1]) != 0)
            child_refuse(EXIT_SETUP, "HELPER_CLOSE_FAILED", errno);
        execv(helper[0], helper);
        child_refuse(EXIT_EXEC, "HELPER_EXEC_FAILED", errno);
    }

    int failed = 0;
    int error = 0;
    if (dup2(from_helper[0], 0) != 0 || dup2(to_helper[1], 1) != 1) {
        failed = 1;
        error = errno;
    }
    if (!failed && (close(to_helper[0]) != 0 || close(to_helper[1]) != 0 ||
                       close(from_helper[0]) != 0 || close(from_helper[1]) != 0)) {
        failed = 1;
        error = errno;
    }
    if (failed) {
        kill(child, SIGKILL);
        refuse(EXIT_SETUP, "CLIENT_DESCRIPTOR_SETUP_FAILED", error);
    }
    fprintf(stderr, "OWNER_HELPER_PID %ld\n", (long)child);
    fflush(stderr);
    execv(client[0], client);
    error = errno;
    /* No client: the helper must not run without its owner. */
    kill(child, SIGKILL);
    refuse(EXIT_EXEC, "CLIENT_EXEC_FAILED", error);
    return EXIT_EXEC;
}
