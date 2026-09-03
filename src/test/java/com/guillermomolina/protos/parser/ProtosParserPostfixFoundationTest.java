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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.guillermomolina.protos.parser.ast.SurfaceArgument;
import com.guillermomolina.protos.parser.ast.SurfaceCall;
import com.guillermomolina.protos.parser.ast.SurfaceGroup;
import com.guillermomolina.protos.parser.ast.SurfaceIndex;
import com.guillermomolina.protos.parser.ast.SurfaceMember;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.source.SourceSpan;
import org.junit.jupiter.api.Test;

class ProtosParserPostfixFoundationTest {
    @Test
    void parsesLeftAssociatedMemberCallAndIndexChain() {
        SurfaceSequence program = new ProtosParser("factory().items[0].name").parseProgram();

        SurfaceMember name = assertInstanceOf(SurfaceMember.class, program.expressions().get(0));
        assertEquals("name", name.name());

        SurfaceIndex index = assertInstanceOf(SurfaceIndex.class, name.receiver());
        SurfaceMember items = assertInstanceOf(SurfaceMember.class, index.receiver());
        assertEquals("items", items.name());
        assertInstanceOf(SurfaceCall.class, items.receiver());
        assertEquals(new SourceSpan(0, 23), name.span());
    }

    @Test
    void reservedSpellingsAreAcceptedAsStructuralMemberNames() {
        SurfaceSequence program = new ProtosParser(
                "obj.this.context.args.super.true.false.null").parseProgram();

        SurfaceMember member = assertInstanceOf(
                SurfaceMember.class, program.expressions().get(0));
        assertEquals("null", member.name());

        int members = 0;
        while (member.receiver() instanceof SurfaceMember previous) {
            members++;
            member = previous;
        }
        assertEquals(7, members + 1);
        assertInstanceOf(SurfaceName.class, member.receiver());
    }

    @Test
    void parsesArgumentsSpreadAndLayoutInsideOpenCall() {
        SurfaceSequence program = new ProtosParser(
                "foo(\n  first,\n  ...rest\n)").parseProgram();

        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, program.expressions().get(0));
        assertEquals(2, call.arguments().size());
        assertEquals(false, call.arguments().get(0).spread());
        assertEquals(true, call.arguments().get(1).spread());
        assertEquals(new SourceSpan(0, 26), call.span());
    }

    @Test
    void leadingDotContinuesACompletedExpressionAcrossNewline() {
        SurfaceSequence program = new ProtosParser("value\n.member()").parseProgram();

        assertEquals(1, program.expressions().size());
        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, program.expressions().get(0));
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, call.receiver());
        assertEquals("member", member.name());
    }

    @Test
    void parenthesizedExpressionRemainsVisibleInSurfaceAst() {
        SurfaceSequence program = new ProtosParser("(value).member").parseProgram();

        SurfaceMember member = assertInstanceOf(
                SurfaceMember.class, program.expressions().get(0));
        SurfaceGroup group = assertInstanceOf(SurfaceGroup.class, member.receiver());
        assertEquals(new SourceSpan(0, 7), group.span());
    }

    @Test
    void callArgumentKeepsItsSurfaceExpression() {
        SurfaceSequence program = new ProtosParser("foo(bar.baz)").parseProgram();

        SurfaceCall call = assertInstanceOf(
                SurfaceCall.class, program.expressions().get(0));
        SurfaceArgument argument = call.arguments().get(0);
        SurfaceMember member = assertInstanceOf(SurfaceMember.class, argument.expression());
        assertEquals("baz", member.name());
    }
}
