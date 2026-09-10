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
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import java.util.Objects;

/**
 * Parallel canonical-to-Bytecode lowering seam for the PERF006-B migration.
 *
 * <p>PERF006-B2A extends the B1 subset from literals to lexical
 * {@link CanonicalLookup} while retaining top-level/body
 * {@link CanonicalSequence}. The ordinary {@link ProtosSourceCompiler} remains
 * on the established AST lowerer
 * until later PERF006-B slices have migrated calls, suspension and control
 * semantics coherently.</p>
 */
final class CanonicalToBytecodeLowerer {
    private final ProtosLanguage language;
    private final Source source;

    CanonicalToBytecodeLowerer(ProtosLanguage language, Source source) {
        this.language = Objects.requireNonNull(language, "language");
        this.source = Objects.requireNonNull(source, "source");
    }

    CallTarget lower(CanonicalSequence sequence) {
        return lowerRoot(sequence).getCallTarget();
    }

    ProtosBytecodeRootNode lowerRoot(CanonicalSequence sequence) {
        Objects.requireNonNull(sequence, "sequence");
        validateSupported(sequence);
        validateSpan(sequence.span());

        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            SourceSpan rootSpan = sequence.span();

                            /*
                             * Root-inside-SourceSection is the Bytecode DSL shape
                             * that gives the root a reliable exact source section
                             * once lazy source information is materialized.
                             */
                            builder.beginSource(source);
                            builder.beginSourceSection(
                                    rootSpan.startOffset(),
                                    rootSpan.length());
                            builder.beginRoot();

                            if (sequence.expressions().isEmpty()) {
                                builder.beginReturn();
                                builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                                builder.endReturn();
                            } else {
                                BytecodeLocal result =
                                        builder.createLocal("sequenceResult", null);

                                for (CanonicalExpression expression :
                                        sequence.expressions()) {
                                    SourceSpan span = expression.span();

                                    builder.beginSourceSection(
                                            span.startOffset(),
                                            span.length());
                                    builder.beginTag(
                                            StandardTags.StatementTag.class);

                                    builder.beginStoreLocal(result);
                                    emitExpression(builder, expression);
                                    builder.endStoreLocal();

                                    builder.endTag(
                                            StandardTags.StatementTag.class);
                                    builder.endSourceSection();
                                }

                                builder.beginReturn();
                                builder.emitLoadLocal(result);
                                builder.endReturn();
                            }

                            builder.endRoot();
                            builder.endSourceSection();
                            builder.endSource();
                        });

        return roots.getNode(0);
    }

    private void validateSupported(CanonicalSequence sequence) {
        for (CanonicalExpression expression : sequence.expressions()) {
            if (!(expression instanceof CanonicalLiteral)
                    && !(expression instanceof CanonicalLookup)) {
                throw new UnsupportedOperationException(
                        "PERF006-B2A Bytecode lowerer does not yet support "
                                + expression.getClass().getSimpleName());
            }
            validateSpan(expression.span());
        }
    }

    private void validateSpan(SourceSpan span) {
        Objects.requireNonNull(span, "span");
        if (span.endOffset() > source.getLength()) {
            throw new IllegalArgumentException(
                    "source span "
                            + span
                            + " exceeds owning Truffle Source length "
                            + source.getLength()
                            + " for "
                            + source.getName());
        }
    }

    private static void emitExpression(
            ProtosBytecodeRootNodeGen.Builder builder,
            CanonicalExpression expression) {
        if (expression instanceof CanonicalLiteral literal) {
            builder.emitLoadConstant(materialize(literal));
            return;
        }
        if (expression instanceof CanonicalLookup lookup) {
            builder.beginLookup();
            builder.emitLoadArgument(0);
            builder.emitLoadConstant(lookup.name());
            builder.endLookup();
            return;
        }
        throw new AssertionError(
                "validated Bytecode expression became unsupported: "
                        + expression.getClass().getSimpleName());
    }

    private static Object materialize(CanonicalLiteral literal) {
        return switch (literal.kind()) {
            case NUMBER -> ProtosNumberLiteral.materialize(literal.value());
            case STRING -> new ProtosStringValue(literal.value());
            case TRUE -> ProtosBooleanValue.TRUE;
            case FALSE -> ProtosBooleanValue.FALSE;
            case NULL -> ProtosNullValue.INSTANCE;
        };
    }
}
