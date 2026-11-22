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
 * Native POSIX child-process provider for the Protos host.
 *
 * Responsibilities: launch one child directly with an explicit argv, environment
 * and absolute working directory; own its three standard pipes; report exec
 * failures through a private status pipe; reap the child exactly once and keep
 * its raw wait status. Only the host links this library; no guest code runs
 * here and no process-global signal disposition is changed.
 *
 * The writing host must already ignore SIGPIPE (as the JVM does), so a write to
 * a pipe whose reader is gone fails with EPIPE instead of killing the host.
 */
#ifndef __linux__
#error "The Protos POSIX process provider supports Linux only"
#endif
#define _GNU_SOURCE 1

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#ifndef CLOSE_RANGE_CLOEXEC
#define CLOSE_RANGE_CLOEXEC (1U << 2)
#endif

enum { CHILD_STDIN = 0, CHILD_STDOUT = 1, CHILD_STDERR = 2, STREAM_COUNT = 3 };

/* Per-child state; freed only after reap and the close of every stream. */
typedef struct protos_process {
    pthread_mutex_t lock;
    pid_t pid;
    int stream_fd[STREAM_COUNT];
    int has_status;
    int wait_status;
} protos_process;

/* A pipe pair with close-on-exec set and both ends above the standard fds. */
typedef struct {
    int read_end;
    int write_end;
} pipe_pair;

static void
discard_fd(int *fd)
{
    if (*fd < 0)
        return;
    int victim = *fd;
    *fd = -1;
    /* On Linux the descriptor is gone even when close reports EINTR. */
    (void) close(victim);
}

static void
discard_pair(pipe_pair *pair)
{
    discard_fd(&pair->read_end);
    discard_fd(&pair->write_end);
}

static int
lift_above_standard(int *fd)
{
    if (*fd > STDERR_FILENO)
        return 0;
    int lifted = fcntl(*fd, F_DUPFD_CLOEXEC, STDERR_FILENO + 1);
    if (lifted < 0)
        return errno;
    discard_fd(fd);
    *fd = lifted;
    return 0;
}

static int
open_pair(pipe_pair *pair)
{
    int ends[2];
    if (pipe2(ends, O_CLOEXEC) < 0)
        return errno;
    pair->read_end = ends[0];
    pair->write_end = ends[1];
    int error = lift_above_standard(&pair->read_end);
    if (error == 0)
        error = lift_above_standard(&pair->write_end);
    if (error != 0)
        discard_pair(pair);
    return error;
}

/* ---- Child side: only async-signal-safe calls between fork and exec. ---- */

static _Noreturn void
report_and_exit(int status_fd, int error)
{
    int32_t value = (int32_t) error;
    const char *cursor = (const char *) &value;
    size_t remaining = sizeof(value);
    while (remaining > 0) {
        ssize_t written = write(status_fd, cursor, remaining);
        if (written < 0 && errno == EINTR)
            continue;
        if (written <= 0)
            break;
        cursor += written;
        remaining -= (size_t) written;
    }
    _exit(127);
}

/* Every inherited descriptor above stderr closes at exec, the status pipe included. */
static void
mark_inherited_cloexec(void)
{
#ifdef SYS_close_range
    if (syscall(SYS_close_range, STDERR_FILENO + 1, ~0U, CLOSE_RANGE_CLOEXEC) == 0)
        return;
#endif
    struct rlimit limit;
    long highest = 1L << 20;
    if (getrlimit(RLIMIT_NOFILE, &limit) == 0 && limit.rlim_cur != RLIM_INFINITY
            && limit.rlim_cur < (rlim_t) highest)
        highest = (long) limit.rlim_cur;
    for (long fd = STDERR_FILENO + 1; fd < highest; ++fd) {
        int flags = fcntl((int) fd, F_GETFD);
        if (flags >= 0 && (flags & FD_CLOEXEC) == 0)
            (void) fcntl((int) fd, F_SETFD, flags | FD_CLOEXEC);
    }
}

static void
reset_signal_dispositions(void)
{
    struct sigaction standard;
    memset(&standard, 0, sizeof(standard));
    standard.sa_handler = SIG_DFL;
    (void) sigemptyset(&standard.sa_mask);
    for (int number = 1; number < NSIG; ++number) {
        if (number == SIGKILL || number == SIGSTOP)
            continue;
        (void) sigaction(number, &standard, NULL);
    }
}

static void
install_standard_fd(int source, int target, int status_fd)
{
    while (dup2(source, target) < 0) {
        if (errno != EINTR)
            report_and_exit(status_fd, errno);
    }
}

static _Noreturn void
run_child(char *const argv[], char *const envp[], const char *cwd,
          const pipe_pair *in, const pipe_pair *out, const pipe_pair *err,
          int status_fd)
{
    /* The parent blocked every signal before fork; defaults first, then unblock. */
    reset_signal_dispositions();
    sigset_t none;
    (void) sigemptyset(&none);
    (void) sigprocmask(SIG_SETMASK, &none, NULL);

    /* All pipe ends are above stderr, so these copies never overwrite a source. */
    install_standard_fd(in->read_end, STDIN_FILENO, status_fd);
    install_standard_fd(out->write_end, STDOUT_FILENO, status_fd);
    install_standard_fd(err->write_end, STDERR_FILENO, status_fd);
    if (chdir(cwd) < 0)
        report_and_exit(status_fd, errno);
    mark_inherited_cloexec();
    execve(argv[0], argv, envp);
    report_and_exit(status_fd, errno);
}

/* ---- Host side. ---- */

/* Reaps at most once; a stopped or continued report is not terminal. */
static int
reap_if_terminated(protos_process *process, int options)
{
    if (process->has_status)
        return 1;
    int status = 0;
    pid_t answered;
    do {
        answered = waitpid(process->pid, &status, options);
    } while (answered < 0 && errno == EINTR);
    if (answered < 0)
        return -errno;
    if (answered == 0 || !(WIFEXITED(status) || WIFSIGNALED(status)))
        return 0;
    process->wait_status = status;
    process->has_status = 1;
    return 1;
}

/* Reads the child's exec report: 0 when exec succeeded (EOF with no bytes). */
static int
await_exec_report(int status_fd)
{
    int32_t value = 0;
    char *cursor = (char *) &value;
    size_t received = 0;
    for (;;) {
        ssize_t count = read(status_fd, cursor + received, sizeof(value) - received);
        if (count < 0 && errno == EINTR)
            continue;
        if (count < 0)
            return errno;
        if (count == 0)
            return received == 0 ? 0 : EIO;
        received += (size_t) count;
        if (received == sizeof(value))
            return value > 0 ? (int) value : EIO;
    }
}

static void
destroy_process(protos_process *process)
{
    (void) pthread_mutex_destroy(&process->lock);
    free(process);
}

protos_process *
protos_process_start(char *const argv[], char *const envp[],
                     const char *cwd, int32_t *launch_error,
                     int64_t *host_pid)
{
    *host_pid = 0;
    *launch_error = EINVAL;
    if (argv == NULL || argv[0] == NULL || argv[0][0] != '/'
            || envp == NULL || cwd == NULL || cwd[0] != '/') {
        errno = EINVAL;
        return NULL;
    }

    protos_process *process = calloc(1, sizeof(*process));
    if (process == NULL) {
        *launch_error = ENOMEM;
        errno = ENOMEM;
        return NULL;
    }
    for (int i = 0; i < STREAM_COUNT; ++i)
        process->stream_fd[i] = -1;
    int error = pthread_mutex_init(&process->lock, NULL);
    if (error != 0) {
        free(process);
        *launch_error = error;
        errno = error;
        return NULL;
    }

    pipe_pair in = {-1, -1}, out = {-1, -1}, err = {-1, -1}, report = {-1, -1};
    if ((error = open_pair(&in)) != 0 || (error = open_pair(&out)) != 0
            || (error = open_pair(&err)) != 0 || (error = open_pair(&report)) != 0)
        goto before_child;

    /* No host signal handler may run in the child before its dispositions reset. */
    sigset_t all, previous;
    (void) sigfillset(&all);
    error = pthread_sigmask(SIG_SETMASK, &all, &previous);
    if (error != 0)
        goto before_child;
    pid_t pid = fork();
    if (pid == 0)
        run_child(argv, envp, cwd, &in, &out, &err, report.write_end);
    int fork_error = pid < 0 ? errno : 0;
    (void) pthread_sigmask(SIG_SETMASK, &previous, NULL);
    if (pid < 0) {
        error = fork_error;
        goto before_child;
    }

    /* A child exists from here on; its identity is the fork result. */
    process->pid = pid;
    *host_pid = pid;
    discard_fd(&in.read_end);
    discard_fd(&out.write_end);
    discard_fd(&err.write_end);
    discard_fd(&report.write_end);

    error = await_exec_report(report.read_end);
    discard_fd(&report.read_end);
    if (error == 0) {
        process->stream_fd[CHILD_STDIN] = in.write_end;
        process->stream_fd[CHILD_STDOUT] = out.read_end;
        process->stream_fd[CHILD_STDERR] = err.read_end;
        *launch_error = 0;
        return process;
    }

    /*
     * The launch failed after fork. The kill is only a request: the child is
     * gone only once it is reaped. If the reap cannot be confirmed the handle
     * is returned with the error so that the caller keeps custody of it.
     */
    discard_pair(&in);
    discard_pair(&out);
    discard_pair(&err);
    (void) kill(pid, SIGKILL);
    int reaped = reap_if_terminated(process, 0);
    *launch_error = error;
    if (reaped != 1) {
        errno = error;
        return process;
    }
    destroy_process(process);
    errno = error;
    return NULL;

before_child:
    discard_pair(&in);
    discard_pair(&out);
    discard_pair(&err);
    discard_pair(&report);
    destroy_process(process);
    *launch_error = error;
    errno = error;
    return NULL;
}

int32_t
protos_process_poll(protos_process *process, int32_t *status)
{
    int error = pthread_mutex_lock(&process->lock);
    if (error != 0)
        return -error;
    int result = reap_if_terminated(process, WNOHANG);
    if (result == 1)
        *status = process->wait_status;
    (void) pthread_mutex_unlock(&process->lock);
    return result;
}

int32_t
protos_process_signal(protos_process *process, int32_t signal_number)
{
    if (signal_number != SIGTERM && signal_number != SIGKILL)
        return -EINVAL;
    int error = pthread_mutex_lock(&process->lock);
    if (error != 0)
        return -error;
    /* Never signal a reaped pid: it may already belong to another process. */
    int result = reap_if_terminated(process, WNOHANG);
    if (result == 0)
        result = kill(process->pid, signal_number) < 0 ? -errno : 0;
    else if (result == 1)
        result = 0;
    (void) pthread_mutex_unlock(&process->lock);
    return result;
}

static int
stream_fd(protos_process *process, int stream, int *fd)
{
    *fd = -1;
    int error = pthread_mutex_lock(&process->lock);
    if (error != 0)
        return -error;
    *fd = process->stream_fd[stream];
    (void) pthread_mutex_unlock(&process->lock);
    return *fd < 0 ? -EBADF : 0;
}

int64_t
protos_process_read(protos_process *process, int32_t stream,
                    void *buffer, uint64_t count)
{
    if ((stream != CHILD_STDOUT && stream != CHILD_STDERR) || count > SSIZE_MAX)
        return -EINVAL;
    int fd = -1;
    int error = stream_fd(process, stream, &fd);
    if (error < 0)
        return error;
    ssize_t result;
    do {
        result = read(fd, buffer, (size_t) count);
    } while (result < 0 && errno == EINTR);
    return result < 0 ? -errno : (int64_t) result;
}

int64_t
protos_process_write(protos_process *process, const void *buffer, uint64_t count)
{
    if (count > SSIZE_MAX)
        return -EINVAL;
    int fd = -1;
    int error = stream_fd(process, CHILD_STDIN, &fd);
    if (error < 0)
        return error;
    ssize_t result;
    do {
        result = write(fd, buffer, (size_t) count);
    } while (result < 0 && errno == EINTR);
    return result < 0 ? -errno : (int64_t) result;
}

int32_t
protos_process_close(protos_process *process, int32_t stream)
{
    if (stream < CHILD_STDIN || stream > CHILD_STDERR)
        return -EINVAL;
    int error = pthread_mutex_lock(&process->lock);
    if (error != 0)
        return -error;
    int fd = process->stream_fd[stream];
    process->stream_fd[stream] = -1;
    (void) pthread_mutex_unlock(&process->lock);
    if (fd < 0)
        return 0;
    return close(fd) < 0 ? -errno : 0;
}

int32_t
protos_process_release(protos_process *process)
{
    /* The caller guarantees that no other thread still uses this handle. */
    if (!process->has_status || process->stream_fd[CHILD_STDIN] >= 0
            || process->stream_fd[CHILD_STDOUT] >= 0
            || process->stream_fd[CHILD_STDERR] >= 0)
        return -EBUSY;
    int error = pthread_mutex_destroy(&process->lock);
    if (error != 0)
        return -error;
    free(process);
    return 0;
}

/* Decoders for a status previously answered by protos_process_poll. */
int32_t protos_process_wifexited(int32_t status) { return WIFEXITED(status) ? 1 : 0; }
int32_t protos_process_wifsignaled(int32_t status) { return WIFSIGNALED(status) ? 1 : 0; }
int32_t protos_process_wexitstatus(int32_t status) { return WEXITSTATUS(status); }
int32_t protos_process_wtermsig(int32_t status) { return WTERMSIG(status); }
