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
 * PLAT074-D1-R1 OS-authority probe. TEST FIXTURE ONLY: compiled into a JUnit
 * temporary directory by ProtosI144FfmFixtureAbiTest and run either directly
 * (unrestricted control) or behind sandbox-exec (restricted probe). It
 * implements no SQLite, no application protocol and no Protos logic; it only
 * performs one system call family per mode and reports the kernel outcome.
 *
 * Modes (all arguments on the command line; stdin carries no instructions):
 *
 *   tcp-connect PORT              connect to 127.0.0.1:PORT
 *   udp-send PORT                 send one datagram to 127.0.0.1:PORT
 *   unix-path-connect PATH        connect to a pathname UNIX stream socket
 *   unix-abstract-listen NAME     listen on an abstract name; READY; accept
 *                                 until stdin EOF (bounded), then CLOSED
 *   unix-abstract-connect NAME    connect to an abstract name
 *   signal-check PID              kill(PID, 0): permission check, no signal
 *   preopen-exec PATH FD -- CMD.. open PATH read-only as inheritable FD, exec
 *   fd-check FD                   report whether FD is open and readable
 *
 * Outcome words: *_ALLOWED / *_CONNECTED / *_SENT when the operation succeeds,
 * *_DENIED only for an authorization error (EACCES or EPERM), and
 * *_OTHER_ERROR for anything else (ECONNREFUSED, ENOENT, ESRCH, timeouts...),
 * which is never evidence of a Landlock denial. Every wait is bounded.
 */

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <time.h>
#include <unistd.h>

#define EXIT_USAGE 64
#define IO_TIMEOUT_SECONDS 10
#define LISTEN_DEADLINE_SECONDS 120
#define MARKER_MAX 64

static void usage(const char *detail) {
    fprintf(stderr, "PROBE_USAGE %s\n", detail);
    exit(EXIT_USAGE);
}

static long parse_long(const char *text, long minimum, long maximum) {
    char *end = NULL;
    errno = 0;
    long value = strtol(text, &end, 10);
    if (errno != 0 || end == text || *end != '\0' || value < minimum || value > maximum)
        usage("invalid number");
    return value;
}

static int authorization_error(int error) {
    return error == EACCES || error == EPERM;
}

/* Prints PREFIX_SUCCESS, PREFIX_DENIED errno=N or PREFIX_OTHER_ERROR errno=N. */
static int report(const char *prefix, const char *success, int result, int error) {
    if (result == 0) printf("%s_%s\n", prefix, success);
    else if (authorization_error(error)) printf("%s_DENIED errno=%d\n", prefix, error);
    else printf("%s_OTHER_ERROR errno=%d\n", prefix, error);
    fflush(stdout);
    return 0;
}

static void bound_socket(int fd) {
    struct timeval timeout = {.tv_sec = IO_TIMEOUT_SECONDS, .tv_usec = 0};
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof timeout);
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof timeout);
}

static struct sockaddr_in loopback(const char *port) {
    struct sockaddr_in address;
    memset(&address, 0, sizeof address);
    address.sin_family = AF_INET;
    address.sin_port = htons((unsigned short)parse_long(port, 1, 65535));
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    return address;
}

static int tcp_connect(const char *port) {
    struct sockaddr_in address = loopback(port);
    int fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return report("TCP", "ALLOWED", -1, errno);
    bound_socket(fd);
    int result = connect(fd, (struct sockaddr *)&address, sizeof address);
    int error = errno;
    close(fd);
    return report("TCP", "ALLOWED", result, error);
}

static int udp_send(const char *port) {
    struct sockaddr_in address = loopback(port);
    int fd = socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return report("UDP", "SENT", -1, errno);
    bound_socket(fd);
    static const char payload[] = "PROBE";
    ssize_t sent = sendto(fd, payload, sizeof payload - 1, 0, (struct sockaddr *)&address, sizeof address);
    int error = errno;
    close(fd);
    if (sent >= 0 && (size_t)sent != sizeof payload - 1) return report("UDP", "SENT", -1, EMSGSIZE);
    return report("UDP", "SENT", sent < 0 ? -1 : 0, error);
}

/* Abstract address: sun_path[0] == '\0' followed by NAME (not terminated). */
static socklen_t abstract_address(const char *name, struct sockaddr_un *address) {
    size_t length = strlen(name);
    if (length == 0 || length >= sizeof address->sun_path - 1) usage("invalid abstract name");
    memset(address, 0, sizeof *address);
    address->sun_family = AF_UNIX;
    memcpy(address->sun_path + 1, name, length);
    return (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + length);
}

static int unix_path_connect(const char *path) {
    struct sockaddr_un address;
    if (path[0] != '/' || strlen(path) >= sizeof address.sun_path) usage("invalid socket path");
    /* The socket must be visible first: a missing or unreadable path is
     * reported as OTHER_ERROR and never as a RESOLVE_UNIX denial. */
    struct stat status;
    if (stat(path, &status) != 0) {
        printf("UNIX_PATH_OTHER_ERROR stage=stat errno=%d\n", errno);
        fflush(stdout);
        return 0;
    }
    if (!S_ISSOCK(status.st_mode)) {
        printf("UNIX_PATH_OTHER_ERROR stage=stat not-a-socket\n");
        fflush(stdout);
        return 0;
    }
    memset(&address, 0, sizeof address);
    address.sun_family = AF_UNIX;
    strcpy(address.sun_path, path);
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return report("UNIX_PATH", "CONNECTED", -1, errno);
    bound_socket(fd);
    int result = connect(fd, (struct sockaddr *)&address, sizeof address);
    int error = errno;
    close(fd);
    return report("UNIX_PATH", "CONNECTED", result, error);
}

static int unix_abstract_connect(const char *name) {
    struct sockaddr_un address;
    socklen_t length = abstract_address(name, &address);
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return report("UNIX_ABSTRACT", "CONNECTED", -1, errno);
    bound_socket(fd);
    int result = connect(fd, (struct sockaddr *)&address, length);
    int error = errno;
    close(fd);
    return report("UNIX_ABSTRACT", "CONNECTED", result, error);
}

/* Accepts every pending connection without blocking; returns the count. */
static long drain_accepts(int listener) {
    long count = 0;
    for (;;) {
        int connection = accept4(listener, NULL, NULL, SOCK_CLOEXEC | SOCK_NONBLOCK);
        if (connection < 0) return count;
        close(connection);
        count++;
        printf("ACCEPTED\n");
        fflush(stdout);
    }
}

static int unix_abstract_listen(const char *name) {
    struct sockaddr_un address;
    socklen_t length = abstract_address(name, &address);
    int listener = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (listener < 0 || bind(listener, (struct sockaddr *)&address, length) != 0 || listen(listener, 8) != 0) {
        printf("LISTEN_FAILED errno=%d\n", errno);
        fflush(stdout);
        if (listener >= 0) close(listener);
        return 2;
    }
    /* READY only after the name is installed and listening. */
    printf("READY\n");
    fflush(stdout);
    long accepted = 0;
    time_t deadline = time(NULL) + LISTEN_DEADLINE_SECONDS;
    for (;;) {
        time_t now = time(NULL);
        if (now >= deadline) {
            printf("TIMEOUT total=%ld\n", accepted);
            fflush(stdout);
            close(listener);
            return 3;
        }
        struct pollfd watched[2] = {{.fd = listener, .events = POLLIN}, {.fd = STDIN_FILENO, .events = POLLIN}};
        int ready = poll(watched, 2, (int)(deadline - now) * 1000);
        if (ready < 0 && errno != EINTR) break;
        if (ready <= 0) continue;
        if (watched[0].revents & POLLIN) accepted += drain_accepts(listener);
        if (watched[1].revents & (POLLIN | POLLHUP | POLLERR)) {
            char buffer[64];
            ssize_t got = read(STDIN_FILENO, buffer, sizeof buffer);
            if (got <= 0) break;
        }
    }
    accepted += drain_accepts(listener);
    printf("CLOSED total=%ld\n", accepted);
    fflush(stdout);
    close(listener);
    return 0;
}

static int signal_check(const char *pid_text) {
    pid_t pid = (pid_t)parse_long(pid_text, 2, 4194304);
    int result = kill(pid, 0);
    int error = errno;
    if (result != 0 && error == ESRCH) {
        printf("SIGNAL_NO_TARGET errno=%d\n", error);
        fflush(stdout);
        return 0;
    }
    return report("SIGNAL", "ALLOWED", result, error);
}

/* Places an inheritable (no FD_CLOEXEC) read-only descriptor at FD, then
 * replaces this process with CMD; the descriptor is the only authority the
 * fixture adds. */
static int preopen_exec(int argc, char **argv) {
    if (argc < 6 || strcmp(argv[4], "--") != 0) usage("preopen-exec PATH FD -- CMD...");
    int target = (int)parse_long(argv[3], 3, 1023);
    int fd = open(argv[2], O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        fprintf(stderr, "PREOPEN_FAILED errno=%d\n", errno);
        return 2;
    }
    /* dup2 clears FD_CLOEXEC on the target; verify instead of assuming. */
    if (fd != target) {
        if (dup2(fd, target) != target) {
            fprintf(stderr, "PREOPEN_FAILED errno=%d\n", errno);
            return 2;
        }
        close(fd);
    } else if (fcntl(target, F_SETFD, 0) != 0) {
        fprintf(stderr, "PREOPEN_FAILED errno=%d\n", errno);
        return 2;
    }
    int flags = fcntl(target, F_GETFD);
    if (flags < 0 || (flags & FD_CLOEXEC) != 0) {
        fprintf(stderr, "PREOPEN_NOT_INHERITABLE\n");
        return 2;
    }
    fprintf(stderr, "PREOPENED fd=%d\n", target);
    fflush(stderr);
    execv(argv[5], &argv[5]);
    fprintf(stderr, "PREOPEN_EXEC_FAILED errno=%d\n", errno);
    return 2;
}

static int fd_check(const char *fd_text) {
    int fd = (int)parse_long(fd_text, 3, 1023);
    if (fcntl(fd, F_GETFD) < 0) {
        if (errno == EBADF) printf("FD_CLOSED\n");
        else printf("FD_OTHER_ERROR errno=%d\n", errno);
        fflush(stdout);
        return 0;
    }
    char buffer[MARKER_MAX + 1];
    ssize_t got = pread(fd, buffer, MARKER_MAX, 0);
    if (got < 0) {
        printf("FD_OPEN_UNREADABLE errno=%d\n", errno);
    } else {
        buffer[got] = '\0';
        for (ssize_t i = 0; i < got; i++) {
            if (buffer[i] < 0x21 || buffer[i] > 0x7e) buffer[i] = '?';
        }
        printf("FD_OPEN_READ %s\n", buffer);
    }
    fflush(stdout);
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 2) usage("missing mode");
    const char *mode = argv[1];
    if (strcmp(mode, "preopen-exec") == 0) return preopen_exec(argc, argv);
    if (argc != 3) usage(mode);
    if (strcmp(mode, "tcp-connect") == 0) return tcp_connect(argv[2]);
    if (strcmp(mode, "udp-send") == 0) return udp_send(argv[2]);
    if (strcmp(mode, "unix-path-connect") == 0) return unix_path_connect(argv[2]);
    if (strcmp(mode, "unix-abstract-listen") == 0) return unix_abstract_listen(argv[2]);
    if (strcmp(mode, "unix-abstract-connect") == 0) return unix_abstract_connect(argv[2]);
    if (strcmp(mode, "signal-check") == 0) return signal_check(argv[2]);
    if (strcmp(mode, "fd-check") == 0) return fd_check(argv[2]);
    usage(mode);
    return EXIT_USAGE;
}
