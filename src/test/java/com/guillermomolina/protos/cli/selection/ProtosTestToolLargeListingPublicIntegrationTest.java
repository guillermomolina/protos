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
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Full-repository listing admission and complete shard reconstruction. */
final class ProtosTestToolLargeListingPublicIntegrationTest
        extends ProtosTestToolCaseSelectionPublicIntegrationTestSupport {
    @Test
    void bug043FullListingExceedsSingleWriteAdmissionCapacity() throws Exception {
        List<List<ListedCase>> shards = new ArrayList<>();
        Set<String> union = new HashSet<>();
        int minimum = Integer.MAX_VALUE;
        int maximum = 0;
        for (int run = 1; run <= 4; run++) {
            R result = run("test", "--list-cases", "--shard-count", "4",
                    "--shard-run", Integer.toString(run));
            assertEquals(0, result.code(), result::err);
            assertEquals("", result.err());
            List<ListedCase> records = completeListing(result.out());
            minimum = Math.min(minimum, records.size());
            maximum = Math.max(maximum, records.size());
            for (ListedCase record : records) {
                assertTrue(union.add(record.ref()), "duplicate CaseRef: " + record.ref());
            }
            shards.add(records);
        }
        assertTrue(maximum - minimum <= 1);
        for (int index = 1; index < shards.size(); index++) {
            assertTrue(shards.get(index - 1).size() >= shards.get(index).size());
        }
        List<ListedCase> ordered = new ArrayList<>();
        for (int row = 0; row < maximum; row++) {
            for (List<ListedCase> shard : shards) {
                if (row < shard.size()) {
                    ordered.add(shard.get(row));
                }
            }
        }
        assertEquals(union.size(), ordered.size());
        String expected = listingDocument(ordered);
        assertTrue(expected.getBytes(StandardCharsets.UTF_8).length > 1_048_576);
        R actual = unfilteredRepositoryListing();
        assertEquals(0, actual.code(), actual::err);
        assertEquals("", actual.err());
        assertEquals(ordered, completeListing(actual.out()));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.out().getBytes(StandardCharsets.UTF_8));
    }

}
