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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.List;
import java.util.Objects;

public final class ProtosParameterBindingNode extends ProtosExpressionNode {
    private final List<CanonicalParameter> parameters;

    @Children
    private final ProtosExpressionNode[] defaultNodes;

    public ProtosParameterBindingNode(
            SourceSpan span,
            List<CanonicalParameter> parameters,
            ProtosExpressionNode[] defaultNodes) {
        super(span);
        this.parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(defaultNodes, "defaultNodes");
        if (defaultNodes.length != this.parameters.size()) {
            throw new IllegalArgumentException(
                    "defaultNodes must align one-for-one with parameters");
        }
        this.defaultNodes = defaultNodes.clone();

        for (int index = 0; index < this.parameters.size(); index++) {
            CanonicalParameter parameter = this.parameters.get(index);
            ProtosExpressionNode defaultNode = this.defaultNodes[index];
            if (parameter.defaultValue().isPresent() != (defaultNode != null)) {
                throw new IllegalArgumentException(
                        "default node presence must match canonical parameter default");
            }
            if (parameter.rest() && index != this.parameters.size() - 1) {
                throw new IllegalArgumentException("rest parameter must be trailing");
            }
        }
    }

    @Override
    protected Object executeDirect(VirtualFrame frame) {
        ProtosActivation activation = ProtosFrameArguments.activation(frame);
        ProtosArrayValue args =
                activation.arguments()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "parameter binding requires an invocation activation"));
        ProtosPrelude prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "parameter binding requires an owning Core prelude"));
        List<Object> supplied = args.indexedSnapshot();

        int suppliedIndex = 0;
        for (int parameterIndex = 0;
                parameterIndex < parameters.size();
                parameterIndex++) {
            CanonicalParameter parameter = parameters.get(parameterIndex);

            if (parameter.rest()) {
                ProtosArrayValue rest =
                        prelude.newFrozenArray(
                                supplied.subList(suppliedIndex, supplied.size()));
                createParameterSlot(activation, parameter.name(), rest);
                suppliedIndex = supplied.size();
                continue;
            }

            Object value;
            if (suppliedIndex < supplied.size()) {
                value = supplied.get(suppliedIndex);
                suppliedIndex++;
            } else if (parameter.defaultValue().isPresent()) {
                value = defaultNodes[parameterIndex].execute(frame);
            } else {
                throw argumentCountError(activation);
            }

            createParameterSlot(activation, parameter.name(), value);
        }

        if (suppliedIndex < supplied.size()) {
            throw argumentCountError(activation);
        }

        return ProtosNullValue.INSTANCE;
    }

    private static void createParameterSlot(
            ProtosActivation activation,
            String name,
            Object value) {
        try {
            activation.context().createLocalSlot(name, value);
        } catch (IllegalStateException invalidCreation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
    }

    private static ProtosSignalException argumentCountError(
            ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
