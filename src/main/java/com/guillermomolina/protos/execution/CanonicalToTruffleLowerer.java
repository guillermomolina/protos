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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumberLiteral;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import com.guillermomolina.protos.semantic.ast.CanonicalIdentity;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import java.util.Objects;

public final class CanonicalToTruffleLowerer {
    public ProtosExpressionNode lower(CanonicalExpression expression) {
        Objects.requireNonNull(expression, "expression");

        if (expression instanceof CanonicalLiteral literal) {
            return lowerLiteral(literal);
        }
        if (expression instanceof CanonicalSequence sequence) {
            return lowerSequence(sequence);
        }
        if (expression instanceof CanonicalIdentity identity) {
            return new ProtosIdentityNode(
                    identity.span(),
                    lower(identity.left()),
                    lower(identity.right()));
        }
        if (expression instanceof CanonicalLookup lookup) {
            return new ProtosLookupNode(lookup.span(), lookup.name());
        }

        throw new UnsupportedOperationException(
                "Canonical expression is not supported by this Truffle lowering slice: "
                        + expression.getClass().getSimpleName());
    }

    private ProtosExpressionNode lowerLiteral(CanonicalLiteral literal) {
        Object value =
                switch (literal.kind()) {
                    case TRUE -> ProtosBooleanValue.TRUE;
                    case FALSE -> ProtosBooleanValue.FALSE;
                    case NULL -> ProtosNullValue.INSTANCE;
                    case STRING -> new ProtosStringValue(literal.value());
                    case NUMBER -> ProtosNumberLiteral.materialize(literal.value());
                };

        return new ProtosConstantNode(literal.span(), value);
    }

    private ProtosExpressionNode lowerSequence(CanonicalSequence sequence) {
        if (sequence.expressions().isEmpty()) {
            throw new UnsupportedOperationException(
                    "Empty canonical Sequence execution remains blocked by B001");
        }

        ProtosExpressionNode[] expressions =
                sequence.expressions().stream().map(this::lower).toArray(ProtosExpressionNode[]::new);
        return new ProtosSequenceNode(sequence.span(), expressions);
    }
}
