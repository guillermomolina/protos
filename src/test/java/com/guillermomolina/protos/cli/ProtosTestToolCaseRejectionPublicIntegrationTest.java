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

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TOOL009-G2 / D185 public `protos test --case CASE_REF` rejection before scheduling. */
final class ProtosTestToolCaseRejectionPublicIntegrationTest
        extends ProtosTestToolCaseSelectionPublicIntegrationTestSupport {
    @Test
    void invalidShardOptionsFailBeforeListingOrExecution() {
        List<List<String>> invalid = new ArrayList<>(List.of(
                List.of("--shard-count", "2"), List.of("--shard-run", "1"),
                List.of("--shard-count"), List.of("--shard-run"),
                List.of("--shard-count", "2", "--shard-run"),
                List.of("--shard-run", "1", "--shard-count"),
                List.of("--shard-count", "1", "--shard-run", "2"),
                List.of("--shard-count", "2", "--shard-count", "2", "--shard-run", "1"),
                List.of("--shard-count", "2", "--shard-run", "1", "--shard-run", "1"),
                List.of("--shard-count=2"), List.of("--shard-run=1"),
                List.of("--shard-count=2", "--shard-run=1"),
                List.of("--shard-count=2", "--shard-run", "1"),
                List.of("--shard-count", "2", "--shard-run=1")));
        for (String value : List.of("0", "-1", "1.5", "abc", "", "1e2", "+1", " 1")) {
            invalid.add(List.of("--shard-count", value, "--shard-run", "1"));
            invalid.add(List.of("--shard-count", "4", "--shard-run", value));
        }
        for (List<String> options : invalid) {
            assertRejectedWithAndWithoutListing(options);
        }
    }

    @Test
    void emptyShardCannotHideInvalidRefsOrSourceScopes() {
        for (List<String> selection : List.of(
                List.of("--case", ref(CORPUS, PARSE_SOURCE, "missing")),
                List.of("--case", parseRef(1), "--case", parseRef(1)),
                List.of("--case", "invalid"),
                List.of("--case", "v2." + parseRef(1).substring(3)),
                List.of("--case", ref(CORPUS, "uri/format.protos", "format constructed")),
                List.of("--file", "protos/tests/library/uri/missing.protos"),
                List.of("--directory", "protos/tests/library/uri/missing"))) {
            List<String> options = new ArrayList<>(selection);
            options.addAll(List.of("--shard-count", "9", "--shard-run", "9"));
            assertRejectedWithAndWithoutListing(options);
        }
    }

    private static void assertRejectedWithAndWithoutListing(List<String> options) {
        for (boolean listing : new boolean[] {false, true}) {
            List<String> arguments = new ArrayList<>(List.of("test", "--file", PARSE_FILE));
            if (listing) {
                arguments.add("--list-cases");
            }
            arguments.addAll(options);
            R result = run(arguments.toArray(String[]::new));
            assertFailsBeforeScheduling(result);
            assertFalse(result.err().contains(" passed, "), result::err);
        }
    }

    @Test
    void repeatedExactRefFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run("test", "--file", PARSE_FILE, "--case", parseRef(1), "--case", parseRef(1)));
    }

    @Test
    void wellFormedUnknownRefFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run("test", "--file", PARSE_FILE, "--case", ref(CORPUS, PARSE_SOURCE, "missing")));
    }

    @Test
    void malformedRefFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run("test", "--file", PARSE_FILE, "--case", "uri/parse.protos::parse valid"));
    }

    @Test
    void unsupportedRefVersionFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run(
                        "test",
                        "--file",
                        PARSE_FILE,
                        "--case",
                        "v2." + parseRef(2).substring("v1.".length())));
    }

    @Test
    void refOutsideActiveFileScopeFailsBeforeScheduling() {
        assertFailsBeforeScheduling(
                run(
                        "test",
                        "--file",
                        PARSE_FILE,
                        "--case",
                        ref(CORPUS, "uri/format.protos", "format constructed")));
    }
}
