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

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.CallTarget;
import java.util.Objects;

public final class ProtosSourceCompiler {
    private final Canonicalizer canonicalizer;
    private final CanonicalToTruffleLowerer lowerer;

    public ProtosSourceCompiler() {
        this(new Canonicalizer(), new CanonicalToTruffleLowerer());
    }

    ProtosSourceCompiler(
            Canonicalizer canonicalizer,
            CanonicalToTruffleLowerer lowerer) {
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
        this.lowerer = Objects.requireNonNull(lowerer, "lowerer");
    }

    public CallTarget compile(String source) {
        Objects.requireNonNull(source, "source");

        SurfaceSequence surface = new ProtosParser(source).parseProgram();
        CanonicalSequence canonical =
                (CanonicalSequence) canonicalizer.canonicalize(surface);
        return ProtosExecution.createCallTarget(lowerer.lower(canonical));
    }
}
