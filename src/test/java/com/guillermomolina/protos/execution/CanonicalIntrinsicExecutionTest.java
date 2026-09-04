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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.semantic.ast.CanonicalIntrinsic;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalIntrinsicExecutionTest {
    private final CanonicalToTruffleLowerer lowerer = new CanonicalToTruffleLowerer();

    @Test
    void thisReturnsDynamicReceiver() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue context = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosActivation activation =
                new ProtosActivation(context, List.of(), receiver);

        assertSame(receiver, execute(intrinsic(CanonicalIntrinsic.Kind.THIS), activation));
    }

    @Test
    void contextReturnsCurrentExecutionContextObject() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue context = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosActivation activation =
                new ProtosActivation(context, List.of(), receiver);

        assertSame(context, execute(intrinsic(CanonicalIntrinsic.Kind.CONTEXT), activation));
    }

    @Test
    void argsRemainsExplicitlyUnimplementedUntilStandardArrayMaterializationExists() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> lowerer.lower(intrinsic(CanonicalIntrinsic.Kind.ARGS)));
    }

    private Object execute(
            CanonicalIntrinsic intrinsic,
            ProtosActivation activation) {
        return ProtosExecution.createCallTarget(lowerer.lower(intrinsic)).call(activation);
    }

    private CanonicalIntrinsic intrinsic(CanonicalIntrinsic.Kind kind) {
        return new CanonicalIntrinsic(kind, new SourceSpan(0, 1));
    }
}
