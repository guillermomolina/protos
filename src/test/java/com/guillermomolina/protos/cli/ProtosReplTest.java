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

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class ProtosReplTest {
    private R repl(String source) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int code = new ProtosCli().run(
                new String[0],
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out),
                new PrintStream(err));
        return new R(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void persistenceCommandsEofAndRecovery() {
        R r = repl("x: 10\nx\n:help\nmissingName\n1 + 1\n:quit\n");
        assertEquals(0, r.c);
        assertTrue(r.o.contains("10\nprotos> 10\n"));
        assertTrue(r.o.contains(":help"));
        assertTrue(r.o.contains("Arrow keys"));
        assertTrue(r.o.contains("2\n"));
        assertFalse(r.e.isBlank());
        assertEquals(0, repl("").c);
    }

    @Test
    void multilineClosureDeclarationPersistsTopLevelContext() {
        R r = repl("identity: (value) => {\n"
                + "    value\n"
                + "}\n"
                + "identity(7)\n"
                + ":quit\n");
        assertEquals(0, r.c);
        assertTrue(r.o.contains("protos> 7\n"), r.o);
        assertTrue(r.e.isBlank(), r.e);
    }

    @Test
    void nestedClosureMultilineInputExecutesOnlyWhenComplete() {
        R r = repl("outer: () => {\n"
                + "    () => {\n"
                + "        42\n"
                + "    }\n"
                + "}\n"
                + "inner: outer()\n"
                + "inner()\n"
                + ":quit\n");
        assertEquals(0, r.c);
        assertTrue(r.o.contains("protos> 42\n"), r.o);
        assertTrue(r.e.isBlank(), r.e);
    }

    @Test
    void multilineCounterClosureIncrementsAcrossCalls() {
        R r = repl("makeCounter: () => {\n"
                + "    count: 0\n"
                + "    () => {\n"
                + "        count = count + 1\n"
                + "        count\n"
                + "    }\n"
                + "}\n"
                + "counter: makeCounter()\n"
                + "counter()\n"
                + "counter()\n"
                + ":quit\n");
        assertEquals(0, r.c);
        assertTrue(r.o.contains("protos> 1\nprotos> 2\n"), r.o);
        assertTrue(r.e.isBlank(), r.e);
    }

    @Test
    void multilineStreamReplInputWaitsForCompleteUnit() {
        R r = repl("answer: () => {\n"
                + "    21 + 21\n"
                + "}\n"
                + "answer()\n"
                + ":quit\n");
        assertEquals(0, r.c);
        assertTrue(r.o.contains("protos> 42\n"), r.o);
        assertTrue(r.e.isBlank(), r.e);
    }

    @Test
    void blankLinesRemainInsideIncompleteMultilineInput() {
        R r = repl("value: () => {\n"
                + "\n"
                + "    9\n"
                + "\n"
                + "}\n"
                + "value()\n"
                + ":quit\n");
        assertEquals(0, r.c);
        assertTrue(r.o.contains("protos> 9\n"), r.o);
        assertTrue(r.e.isBlank(), r.e);
    }

    @Test
    void multilineObjectInputIsAccumulated() {
        R r = repl("holder: {\n"
                + "    answer: 42\n"
                + "}\n"
                + "holder.answer\n"
                + ":quit\n");
        assertEquals(0, r.c);
        assertTrue(r.o.contains("protos> 42\n"), r.o);
        assertTrue(r.e.isBlank(), r.e);
    }

    @Test
    void invalidSyntaxDoesNotPoisonNextReplInput() {
        R r = repl("1 + )\n1 + 1\n:quit\n");
        assertEquals(0, r.c);
        assertTrue(r.e.contains("Syntax error:"), r.e);
        assertTrue(r.o.contains("protos> 2\n"), r.o);
    }

    @Test
    void bracketedPastePayloadExecutesAsOneCompilationUnit() throws Exception {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var cli = new ProtosCli();
        Method sessionMethod = ProtosCli.class.getDeclaredMethod("session");
        sessionMethod.setAccessible(true);
        Object session = sessionMethod.invoke(cli);
        Method method = ProtosCli.class.getDeclaredMethod(
                "processReturnedInput", String.class, session.getClass(), PrintStream.class, PrintStream.class);
        method.setAccessible(true);

        Object result = method.invoke(
                cli,
                "x: 10\nx\nx + 5",
                session,
                new PrintStream(out),
                new PrintStream(err));

        assertEquals("COMPLETE", result.toString());
        assertEquals("15\n", out.toString(StandardCharsets.UTF_8));
        assertTrue(err.toString(StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void bracketedPasteSupportsNestedMultilineClosureAsOneUnit() throws Exception {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var cli = new ProtosCli();
        Method sessionMethod = ProtosCli.class.getDeclaredMethod("session");
        sessionMethod.setAccessible(true);
        Object session = sessionMethod.invoke(cli);
        Method method = ProtosCli.class.getDeclaredMethod(
                "processReturnedInput", String.class, session.getClass(), PrintStream.class, PrintStream.class);
        method.setAccessible(true);

        Object result = method.invoke(
                cli,
                "make: () => {\n    () => {\n        5\n    }\n}\ninner: make()\ninner()",
                session,
                new PrintStream(out),
                new PrintStream(err));

        assertEquals("COMPLETE", result.toString());
        assertEquals("5\n", out.toString(StandardCharsets.UTF_8));
        assertTrue(err.toString(StandardCharsets.UTF_8).isBlank());
    }

    @Test
    void historySplitsMultilineInputIntoIndependentEntries() {
        var history = new ProtosCli.ReplHistory();
        history.add(Instant.EPOCH, "m: Map()\nm.atPut(\"a\", 10)\nm.at(\"no-existe\")\n1 + 1");
        assertEquals(4, history.size());
        assertEquals("m: Map()", history.get(history.first()));
        assertEquals("m.atPut(\"a\", 10)", history.get(history.first() + 1));
        assertEquals("m.at(\"no-existe\")", history.get(history.first() + 2));
        assertEquals("1 + 1", history.get(history.last()));
    }

    @Test
    void streamFallbackDoesNotRequireATerminal() {
        R r = repl("1 + 1\n");
        assertEquals(0, r.c);
        assertTrue(r.o.contains("2\n"));
        assertTrue(r.e.isBlank(), r.e);
    }

    private record R(int c, String o, String e) {}
}
