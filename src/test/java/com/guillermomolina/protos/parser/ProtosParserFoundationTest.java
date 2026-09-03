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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.parser.ast.SurfaceIntrinsic;
import com.guillermomolina.protos.parser.ast.SurfaceLiteral;
import com.guillermomolina.protos.parser.ast.SurfaceName;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosParserFoundationTest {
    @Test
    void parsesFoundationPrimaryExpressionsAcrossLogicalLines() {
        SurfaceSequence program = new ProtosParser(
                "42\n\"x\"\nname\nthis\ncontext\nargs\ntrue\nfalse\nnull")
                .parseProgram();

        assertEquals(9, program.expressions().size());
        assertEquals(new SurfaceLiteral(SurfaceLiteral.Kind.NUMBER, "42", new SourceSpan(0, 2)),
                program.expressions().get(0));
        assertEquals(new SurfaceLiteral(SurfaceLiteral.Kind.STRING, "x", new SourceSpan(3, 6)),
                program.expressions().get(1));
        assertEquals(new SurfaceName("name", new SourceSpan(7, 11)), program.expressions().get(2));
        assertEquals(SurfaceIntrinsic.Kind.THIS,
                ((SurfaceIntrinsic) program.expressions().get(3)).kind());
        assertEquals(SurfaceLiteral.Kind.NULL,
                ((SurfaceLiteral) program.expressions().get(8)).kind());
        assertEquals(new SourceSpan(0, 49), program.span());
    }

    @Test
    void acceptsBlankLinesWithoutCreatingExpressions() {
        SurfaceSequence program = new ProtosParser("\n\nalpha\n\n\nbeta\n").parseProgram();

        assertEquals(
                List.of(
                        new SurfaceName("alpha", new SourceSpan(2, 7)),
                        new SurfaceName("beta", new SourceSpan(10, 14))),
                program.expressions());
        assertEquals(new SourceSpan(2, 14), program.span());
    }

    @Test
    void emptyProgramProducesEmptySequenceAtEof() {
        SurfaceSequence program = new ProtosParser("").parseProgram();

        assertEquals(List.of(), program.expressions());
        assertEquals(new SourceSpan(0, 0), program.span());
    }

    @Test
    void parseErrorsCarryTheUnexpectedTokenSpan() {
        ParseError error = assertThrows(
                ParseError.class,
                () -> new ProtosParser("name\n)").parseProgram());

        assertEquals(new SourceSpan(5, 6), error.span());
    }
}
