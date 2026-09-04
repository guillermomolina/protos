/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
final class ProtosReplTest {
    private R repl(String s) {
        var o=new ByteArrayOutputStream(); var e=new ByteArrayOutputStream();
        int c=new ProtosCli().run(new String[0],new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)),new PrintStream(o),new PrintStream(e));
        return new R(c,o.toString(StandardCharsets.UTF_8),e.toString(StandardCharsets.UTF_8));
    }
    @Test void persistenceCommandsEofAndRecovery() {
        R r=repl("x: 10\nx\n:help\nmissingName\n1 + 1\n:quit\n");
        assertEquals(0,r.c); assertTrue(r.o.contains("10\nprotos> 10\n")); assertTrue(r.o.contains(":help"));
        assertTrue(r.o.contains("Arrow keys")); assertTrue(r.o.contains("2\n")); assertFalse(r.e.isBlank()); assertEquals(0,repl("").c);
    }
    @Test void pastedCompleteLinesAreIndependentEvaluations() {
        R r=repl("x: 10\nx\nx + 5\n:exit\n");
        assertEquals(0,r.c); assertTrue(r.o.contains("protos> 10\nprotos> 10\nprotos> 15\n")); assertTrue(r.e.isBlank(),r.e);
    }
    @Test void streamFallbackDoesNotRequireATerminal() {
        R r=repl("1 + 1\n"); assertEquals(0,r.c); assertTrue(r.o.contains("2\n")); assertTrue(r.e.isBlank(),r.e);
    }
    private record R(int c,String o,String e){}
}
