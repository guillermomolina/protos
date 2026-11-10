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
 * I144 generic ABI fixture for the external FFM provider. It depends on no
 * library beyond the C runtime and is compiled only by
 * dist/test_external_foreign_providers.py. Every symbol is small and generic;
 * none imitates SQLite.
 */

#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* ---- scalars */

int32_t ffi_test_add_i32(int32_t left, int32_t right) { return left + right; }

int32_t ffi_test_i32_identity(int32_t value) { return value; }

int64_t ffi_test_i64_identity(int64_t value) { return value; }

size_t ffi_test_size_identity(size_t value) { return value; }

size_t ffi_test_size_max(void) { return SIZE_MAX; }

double ffi_test_scale(double value, double factor) { return value * factor; }

static int32_t ffi_test_calls;

void ffi_test_touch(void) { ffi_test_calls++; }

int32_t ffi_test_touch_count(void) { return ffi_test_calls; }

/* ---- NUL-terminated UTF-8 text input */

size_t ffi_test_utf8_length(const char *text) { return text == NULL ? 0 : strlen(text); }

/* Sum of the octets, to prove the exact UTF-8 encoding crossed. */
int64_t ffi_test_octet_sum(const char *text) {
    int64_t sum = 0;
    for (const unsigned char *p = (const unsigned char *)text; *p != 0; p++) {
        sum += *p;
    }
    return sum;
}

/* ---- out parameters */

int32_t ffi_test_divmod(int32_t dividend, int32_t divisor, int32_t *quotient, int32_t *remainder) {
    if (divisor == 0) {
        return 1;
    }
    *quotient = dividend / divisor;
    *remainder = dividend % divisor;
    return 0;
}

/* Reports whether the out slot arrived zero-initialized. */
int32_t ffi_test_out_was_zero(int64_t *slot) {
    int32_t was_zero = *slot == 0;
    *slot = 77;
    return was_zero;
}

/* ---- opaque owned resources */

typedef struct ffi_test_counter {
    int64_t value;
} ffi_test_counter;

static int32_t ffi_test_live;
static int32_t ffi_test_destroyed;

ffi_test_counter *ffi_test_counter_new(int64_t initial) {
    ffi_test_counter *counter = malloc(sizeof *counter);
    if (counter != NULL) {
        counter->value = initial;
        ffi_test_live++;
    }
    return counter;
}

/* Status-and-resource: answers 0 or 7 and always delivers a resource. */
int32_t ffi_test_counter_open(int64_t initial, int32_t fail, ffi_test_counter **out) {
    *out = ffi_test_counter_new(initial);
    return fail ? 7 : 0;
}

/* Answers NULL through the out slot. */
int32_t ffi_test_counter_open_none(ffi_test_counter **out) {
    *out = NULL;
    return 3;
}

/*
 * Two pointer outs: NULL into the first, a fresh owned counter into the
 * second; answers status 5. Declaring the first as a non-nullable opaque
 * makes the conversion fail after the second was produced (custody regression).
 */
int32_t ffi_test_counter_open_pair(ffi_test_counter **missing, ffi_test_counter **out,
                                   int64_t initial) {
    *missing = NULL;
    *out = ffi_test_counter_new(initial);
    return 5;
}

int64_t ffi_test_counter_increment(ffi_test_counter *counter) { return ++counter->value; }

int32_t ffi_test_counter_free(ffi_test_counter *counter) {
    if (counter == NULL) {
        return -1;
    }
    free(counter);
    ffi_test_live--;
    ffi_test_destroyed++;
    return 0;
}

int32_t ffi_test_live_count(void) { return ffi_test_live; }

int32_t ffi_test_destroyed_count(void) { return ffi_test_destroyed; }

/* Accepts NULL; answers 1 for a NULL pointer and 0 otherwise. */
int32_t ffi_test_is_null(const ffi_test_counter *counter) { return counter == NULL; }

/* ---- borrowed memory with explicit length and later invalidation */

static unsigned char ffi_test_buffer[8];
static int32_t ffi_test_buffer_length;

/* Fills the static buffer with 0..length-1 (length <= 8) and answers it. */
const unsigned char *ffi_test_bytes(int32_t length) {
    if (length < 0 || length > 8) {
        return NULL;
    }
    for (int32_t i = 0; i < length; i++) {
        ffi_test_buffer[i] = (unsigned char)(i * 3 + 250);
    }
    ffi_test_buffer_length = length;
    return ffi_test_buffer;
}

int32_t ffi_test_bytes_length(void) { return ffi_test_buffer_length; }

/* Overwrites the buffer: borrowed regions obtained before are no longer valid. */
void ffi_test_bytes_clobber(void) {
    memset(ffi_test_buffer, 0xEE, sizeof ffi_test_buffer);
    ffi_test_buffer_length = 0;
}

const unsigned char *ffi_test_null_bytes(void) { return NULL; }
