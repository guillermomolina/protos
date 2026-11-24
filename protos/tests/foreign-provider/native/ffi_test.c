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

/* ---- LIB030 private length-carrying inputs */

/*
 * Copies a length-carrying input only when the destructor is the copy sentinel
 * ((void (*)(void *)) -1), as a copying C API does, and records whether the
 * region was non-NULL and NUL-guarded. Answers the length, or -1 on misuse.
 */
#define FFI_TEST_INPUT_MAX 64
static unsigned char ffi_test_input[FFI_TEST_INPUT_MAX];
static int32_t ffi_test_input_length = -1;
static int32_t ffi_test_input_calls;
static int32_t ffi_test_input_guarded;

int32_t ffi_test_input_copy(const unsigned char *data, int32_t length, void (*destructor)(void *)) {
    ffi_test_input_calls++;
    if (data == NULL || length < 0 || destructor != (void (*)(void *))-1) {
        return -1;
    }
    int32_t kept = length < FFI_TEST_INPUT_MAX ? length : FFI_TEST_INPUT_MAX;
    memcpy(ffi_test_input, data, (size_t)kept);
    ffi_test_input_length = length;
    ffi_test_input_guarded = data[length] == 0;
    return length;
}

/* Reads the private copy after the call that supplied it has returned. */
int32_t ffi_test_input_byte(int32_t index) {
    if (index < 0 || index >= ffi_test_input_length || index >= FFI_TEST_INPUT_MAX) {
        return -1;
    }
    return ffi_test_input[index];
}

int32_t ffi_test_input_calls_count(void) { return ffi_test_input_calls; }

int32_t ffi_test_input_was_guarded(void) { return ffi_test_input_guarded; }

/* Writes text + skip as the tail; a negative skip writes NULL. */
int32_t ffi_test_tail(const char *text, int32_t skip, const char **tail) {
    *tail = skip < 0 ? NULL : text + skip;
    return 0;
}

/* First octet of the private copy, readable from any session as int32_t f(void). */
int32_t ffi_test_input_byte_zero(void) { return ffi_test_input_byte(0); }

/* ---- fixed-width scalars: identities, outputs and an entry counter */

static int32_t ffi_test_scalar_calls;

int32_t ffi_test_scalar_calls_count(void) { return ffi_test_scalar_calls; }

int8_t ffi_test_i8_identity(int8_t value) {
    ffi_test_scalar_calls++;
    return value;
}

uint8_t ffi_test_u8_identity(uint8_t value) {
    ffi_test_scalar_calls++;
    return value;
}

int16_t ffi_test_i16_identity(int16_t value) {
    ffi_test_scalar_calls++;
    return value;
}

uint16_t ffi_test_u16_identity(uint16_t value) {
    ffi_test_scalar_calls++;
    return value;
}

uint32_t ffi_test_u32_identity(uint32_t value) {
    ffi_test_scalar_calls++;
    return value;
}

uint64_t ffi_test_u64_identity(uint64_t value) {
    ffi_test_scalar_calls++;
    return value;
}

float ffi_test_f32_identity(float value) {
    ffi_test_scalar_calls++;
    return value;
}

/* A valid C signature whose physical shape is deliberately not registered for Native Image. */
float ffi_test_f32_add(float left, float right) {
    ffi_test_scalar_calls++;
    return left + right;
}

/* Writes the extreme values of the narrow widths through real native pointers. */
int32_t ffi_test_out_narrow(int8_t *i8, uint8_t *u8, int16_t *i16, uint16_t *u16) {
    ffi_test_scalar_calls++;
    *i8 = INT8_MIN;
    *u8 = UINT8_MAX;
    *i16 = INT16_MIN;
    *u16 = UINT16_MAX;
    return 4;
}

/* Writes unsigned values above the signed ranges and one binary32 value. */
int32_t ffi_test_out_wide(uint32_t *u32, uint64_t *u64, float *f32) {
    ffi_test_scalar_calls++;
    *u32 = UINT32_MAX;
    *u64 = UINT64_MAX - 1;
    *f32 = -1.5f;
    return 3;
}

/* ---- C aggregate layouts measured by the C compiler itself */

struct ffi_test_padded {
    int8_t a;
    int32_t b;
    int16_t c;
};

struct ffi_test_nested {
    int8_t tag;
    struct ffi_test_padded inner;
    double d;
};

union ffi_test_mixed {
    int8_t small;
    int64_t wide;
    uint16_t half[5];
};

struct ffi_test_with_array {
    int16_t head;
    struct ffi_test_padded items[3];
    int8_t tail;
};

struct ffi_test_with_pointer {
    int8_t flag;
    void *data;
    float ratio;
};

struct ffi_test_with_union {
    uint8_t kind;
    union ffi_test_mixed value;
    uint32_t after;
};

/* C has no pointer nullability: a pointer that may be NULL has the pointer layout. */
struct ffi_test_with_nullable {
    uint8_t tag;
    struct ffi_test_padded *next;
    float ratio;
};

/*
 * One numbered C fact per query, computed with sizeof, _Alignof and offsetof;
 * answers -1 for an unknown query.
 */
int64_t ffi_test_layout(int64_t query) {
    switch (query) {
    case 0: return (int64_t)sizeof(int8_t);
    case 1: return (int64_t)_Alignof(int8_t);
    case 2: return (int64_t)sizeof(uint8_t);
    case 3: return (int64_t)_Alignof(uint8_t);
    case 4: return (int64_t)sizeof(int16_t);
    case 5: return (int64_t)_Alignof(int16_t);
    case 6: return (int64_t)sizeof(uint16_t);
    case 7: return (int64_t)_Alignof(uint16_t);
    case 8: return (int64_t)sizeof(int32_t);
    case 9: return (int64_t)_Alignof(int32_t);
    case 10: return (int64_t)sizeof(uint32_t);
    case 11: return (int64_t)_Alignof(uint32_t);
    case 12: return (int64_t)sizeof(int64_t);
    case 13: return (int64_t)_Alignof(int64_t);
    case 14: return (int64_t)sizeof(uint64_t);
    case 15: return (int64_t)_Alignof(uint64_t);
    case 16: return (int64_t)sizeof(size_t);
    case 17: return (int64_t)_Alignof(size_t);
    case 18: return (int64_t)sizeof(float);
    case 19: return (int64_t)_Alignof(float);
    case 20: return (int64_t)sizeof(double);
    case 21: return (int64_t)_Alignof(double);
    case 22: return (int64_t)sizeof(void *);
    case 23: return (int64_t)_Alignof(void *);

    case 30: return (int64_t)sizeof(struct ffi_test_padded);
    case 31: return (int64_t)_Alignof(struct ffi_test_padded);
    case 32: return (int64_t)offsetof(struct ffi_test_padded, a);
    case 33: return (int64_t)offsetof(struct ffi_test_padded, b);
    case 34: return (int64_t)offsetof(struct ffi_test_padded, c);

    case 40: return (int64_t)sizeof(struct ffi_test_nested);
    case 41: return (int64_t)_Alignof(struct ffi_test_nested);
    case 42: return (int64_t)offsetof(struct ffi_test_nested, inner);
    case 43: return (int64_t)offsetof(struct ffi_test_nested, inner.c);
    case 44: return (int64_t)offsetof(struct ffi_test_nested, d);

    case 50: return (int64_t)sizeof(union ffi_test_mixed);
    case 51: return (int64_t)_Alignof(union ffi_test_mixed);
    case 52: return (int64_t)offsetof(union ffi_test_mixed, wide);
    case 53: return (int64_t)offsetof(union ffi_test_mixed, half[4]);

    case 60: return (int64_t)sizeof(struct ffi_test_padded[4]);
    case 61: return (int64_t)_Alignof(struct ffi_test_padded[4]);

    case 70: return (int64_t)sizeof(struct ffi_test_with_array);
    case 71: return (int64_t)_Alignof(struct ffi_test_with_array);
    case 72: return (int64_t)offsetof(struct ffi_test_with_array, items);
    case 73: return (int64_t)offsetof(struct ffi_test_with_array, items[2].c);
    case 74: return (int64_t)offsetof(struct ffi_test_with_array, tail);

    case 80: return (int64_t)sizeof(struct ffi_test_with_pointer);
    case 81: return (int64_t)_Alignof(struct ffi_test_with_pointer);
    case 82: return (int64_t)offsetof(struct ffi_test_with_pointer, data);
    case 83: return (int64_t)offsetof(struct ffi_test_with_pointer, ratio);

    case 90: return (int64_t)sizeof(struct ffi_test_with_union);
    case 91: return (int64_t)_Alignof(struct ffi_test_with_union);
    case 92: return (int64_t)offsetof(struct ffi_test_with_union, value);
    case 93: return (int64_t)offsetof(struct ffi_test_with_union, value.half[3]);
    case 94: return (int64_t)offsetof(struct ffi_test_with_union, after);

    case 100: return (int64_t)sizeof(struct ffi_test_with_nullable);
    case 101: return (int64_t)_Alignof(struct ffi_test_with_nullable);
    case 102: return (int64_t)offsetof(struct ffi_test_with_nullable, tag);
    case 103: return (int64_t)offsetof(struct ffi_test_with_nullable, next);
    case 104: return (int64_t)offsetof(struct ffi_test_with_nullable, ratio);
    default: return -1;
    }
}
