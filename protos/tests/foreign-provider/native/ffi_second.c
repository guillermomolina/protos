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
 * Second, independent C library for the native library allowlist and scalar
 * ABI tests. It depends on nothing beyond the C runtime and is compiled only
 * by the test hosts (dist/test_external_foreign_providers.py,
 * dist/validate_native.py and ProtosI144FfmFixtureAbiTest). Its symbols never overlap with
 * native/ffi_test.c, so each authorized alias proves its own exact target.
 */

#include <stdint.h>
#include <stdio.h>

/* ---- deterministic integer function */

int32_t ffi_second_triple(int32_t value) { return value * 3; }

/* ---- native global state, observable through two functions */

static int64_t ffi_second_state;

void ffi_second_store(int64_t value) { ffi_second_state = value; }

int64_t ffi_second_load(void) { return ffi_second_state; }

/*
 * ---- native write to a caller-selected file
 *
 * Writes one fixed line to path and answers 0, or -1 when the file cannot be
 * written. The effect uses only operating-system process authority.
 */
int32_t ffi_second_write_marker(const char *path) {
    FILE *file = fopen(path, "w");
    if (file == NULL) {
        return -1;
    }
    int written = fputs("PROTOS-FFI-SECOND-MARKER\n", file);
    int closed = fclose(file);
    return written < 0 || closed != 0 ? -1 : 0;
}

/* ---- the u8(u8) physical shape under a second, independently authorized library */

uint8_t ffi_second_u8_complement(uint8_t value) { return (uint8_t)~value; }

/* ---- a pointer obtained from another authorized library, read here */

static int32_t ffi_second_pointer_calls;

/* Reads the octet at index 1; answers -1 for NULL. */
int32_t ffi_second_pointer_second(const uint8_t *data) {
    ffi_second_pointer_calls++;
    return data == NULL ? -1 : data[1];
}

int32_t ffi_second_pointer_calls_count(void) { return ffi_second_pointer_calls; }

/* Checks only for NULL: never dereferences a pointer whose layout belongs to another library. */
int32_t ffi_second_pointer_present(const void *data) {
    ffi_second_pointer_calls++;
    return data == NULL ? 0 : 1;
}

/*
 * ---- a by-value group under a second, independently authorized library
 *
 * Its physical layout equals ffi_test_memory_inner of the first library, so it
 * reuses the same native registration; the transformation differs.
 */

typedef struct ffi_second_pair {
    int32_t a;
    double b;
} ffi_second_pair;

ffi_second_pair ffi_second_group_transform(ffi_second_pair value) {
    value.a = value.a * 5 + 1;
    value.b = value.b - 2.0;
    return value;
}
