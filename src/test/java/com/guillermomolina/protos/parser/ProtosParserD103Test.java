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

package com.guillermomolina.protos.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProtosParserD103Test {
    @Test
    void acceptsTerminalDynamicCaptureSegmentsInTheFinalLogicalInterface() {
        assertParses(
                "subject match {\n"
                        + "case matcher captures(first, ...rest) => first\n"
                        + "}");
        assertParses(
                "subject match {\n"
                        + "case [@head, matcher captures(first, ...rest)] => head\n"
                        + "}");
        assertParses(
                "subject match {\n"
                        + "case [matcher captures(first, ...rest), _, 42] => first\n"
                        + "}");
        assertParses(
                "subject match {\n"
                        + "case @whole: [matcher captures(first, ...rest)] => whole\n"
                        + "}");
    }

    @Test
    void rejectsAnyLaterLogicalBindingAfterADynamicCaptureSegment() {
        assertRejects(
                "subject match {\n"
                        + "case [matcher captures(first, ...middle), @last] => first\n"
                        + "}");
        assertRejects(
                "subject match {\n"
                        + "case [matcher captures(...middle), later captures(last)] => last\n"
                        + "}");
        assertRejects(
                "subject match {\n"
                        + "case [@whole: matcher captures(first, ...middle), @last] => whole\n"
                        + "}");
        assertRejects(
                "subject match {\n"
                        + "case %{ \"a\": matcher captures(first, ...middle), \"b\": @last } => first\n"
                        + "}");
    }

    @Test
    void structuralRemainderBindingIsOneFixedBindingNotAD103DynamicSegment() {
        assertParses(
                "subject match {\n"
                        + "case [@first, ...@middle, @last] => middle\n"
                        + "}");
        assertParses(
                "subject match {\n"
                        + "case %{ \"a\": @first, ...@rest } => rest\n"
                        + "}");
    }

    private static void assertParses(String source) {
        assertDoesNotThrow(() -> new ProtosParser(source).parseProgram());
    }

    private static void assertRejects(String source) {
        assertThrows(ParseError.class, () -> new ProtosParser(source).parseProgram());
    }
}
