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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosReplCompletionTest {
    @Test
    void completesExistingCommandsInCatalogOrder() {
        assertEquals(
                List.of(":help", ":quit", ":exit"),
                ProtosReplCompletion.commandCandidates(":", 1));

        assertEquals(
                List.of(":help"),
                ProtosReplCompletion.commandCandidates(":he", 3));

        assertEquals(
                List.of(":quit"),
                ProtosReplCompletion.commandCandidates(":qu", 3));

        assertEquals(
                List.of(":exit"),
                ProtosReplCompletion.commandCandidates(":ex", 3));
    }

    @Test
    void recognizesCompleteCommandsWithoutInventingOthers() {
        assertEquals(
                List.of(":help"),
                ProtosReplCompletion.commandCandidates(":help", 5));

        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates(":history", 8));

        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates(":HE", 3));
    }

    @Test
    void respectsTextAfterTheCursor() {
        assertEquals(
                List.of(":help"),
                ProtosReplCompletion.commandCandidates(":hlp", 2));

        assertEquals(
                List.of(":help"),
                ProtosReplCompletion.commandCandidates(":help", 2));

        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates(":hxlp", 2));

        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates(":help extra", 3));
    }

    @Test
    void rejectsNonCommandContextsAndInvalidOffsets() {
        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates("", 0));

        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates(" :he", 4));

        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates("object.member", 13));

        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates(":he", 0));

        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates(":he", -1));

        assertEquals(
                List.of(),
                ProtosReplCompletion.commandCandidates(":he", 4));
    }
}
