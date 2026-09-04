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

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalToTruffleLowererTest {
    private final CanonicalToTruffleLowerer lowerer = new CanonicalToTruffleLowerer();

    @Test
    void lowersCanonicalBooleanAndNullSingletons() {
        assertSame(ProtosBooleanValue.TRUE, execute(literal(CanonicalLiteral.Kind.TRUE, "true")));
        assertSame(ProtosBooleanValue.FALSE, execute(literal(CanonicalLiteral.Kind.FALSE, "false")));
        assertSame(ProtosNullValue.INSTANCE, execute(literal(CanonicalLiteral.Kind.NULL, "null")));
    }

    @Test
    void lowersCanonicalStringValue() {
        Object result = execute(literal(CanonicalLiteral.Kind.STRING, "hello"));

        ProtosStringValue string = (ProtosStringValue) result;
        assertEquals("hello", string.value());
    }

    @Test
    void lowersNonEmptyCanonicalSequenceAndReturnsFinalValue() {
        CanonicalSequence sequence =
                new CanonicalSequence(
                        List.of(
                                literal(CanonicalLiteral.Kind.TRUE, "true"),
                                literal(CanonicalLiteral.Kind.FALSE, "false"),
                                literal(CanonicalLiteral.Kind.NULL, "null")),
                        new SourceSpan(0, 16));

        assertSame(ProtosNullValue.INSTANCE, execute(sequence));
    }

    private Object execute(com.guillermomolina.protos.semantic.ast.CanonicalExpression expression) {
        return ProtosExecution.createCallTarget(lowerer.lower(expression)).call();
    }

    private CanonicalLiteral literal(CanonicalLiteral.Kind kind, String spelling) {
        return new CanonicalLiteral(kind, spelling, new SourceSpan(0, spelling.length()));
    }
}
