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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.semantic.ast.CanonicalSpread;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B1BytecodeLiteralSequenceTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void canonicalLiteralAndSequenceSubsetMatchesTheCurrentAstBackend() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                assertEquivalent("true");
                assertEquivalent("false");
                assertEquivalent("null");
                assertEquivalent("\"hello\"");
                assertEquivalent("92233720368547758081234567890");
                assertEquivalent("0xFF");
                assertEquivalent("1.25");
                assertEquivalent("\"discarded\"\nfalse\n0x2A");
            } finally {
                context.leave();
            }
        }

        System.out.println("PERF006_B1_LITERAL_EQUIVALENCE=PASS");
        System.out.println("PERF006_B1_SEQUENCE_EQUIVALENCE=PASS");
    }

    @Test
    void emptyCanonicalSequenceRetainsCanonicalNullResult() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        "",
                                        "perf006-b1-empty.protos")
                                .build();

                CanonicalSequence sequence =
                        new CanonicalSequence(List.of(), new SourceSpan(0, 0));

                Object ast = executeAst(language, source, sequence);
                Object bytecode =
                        new CanonicalToBytecodeLowerer(language, source)
                                .lower(sequence)
                                .call();

                assertSame(ProtosNullValue.INSTANCE, ast);
                assertSame(ProtosNullValue.INSTANCE, bytecode);
            } finally {
                context.leave();
            }
        }

        System.out.println("PERF006_B1_EMPTY_SEQUENCE=PASS");
    }

    @Test
    void bytecodeRootRetainsExactLazySourceOwnership() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                String characters = "1\n2\n3";
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        characters,
                                        "perf006-b1-source.protos")
                                .build();
                CanonicalSequence sequence = canonicalize(characters);

                ProtosBytecodeRootNode root =
                        new CanonicalToBytecodeLowerer(language, source)
                                .lowerRoot(sequence);

                SourceSection section = root.ensureSourceSection();
                assertSame(source, section.getSource());
                assertEquals(sequence.span().startOffset(), section.getCharIndex());
                assertEquals(sequence.span().length(), section.getCharLength());
                assertEquals(
                        characters.substring(
                                sequence.span().startOffset(),
                                sequence.span().endOffset()),
                        section.getCharacters().toString());
            } finally {
                context.leave();
            }
        }

        System.out.println("PERF006_B1_ROOT_SOURCE_SECTION=PASS");
    }

    @Test
    void unsupportedCanonicalExpressionFailsClosedBeforePartialLowering() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                CanonicalSequence supported = canonicalize("1");
                Source source =
                        Source.newBuilder(
                                        ProtosLanguage.ID,
                                        "1",
                                        "perf006-b1-unsupported-root-spread.protos")
                                .build();

                CanonicalSequence sequence =
                        new CanonicalSequence(
                                List.of(
                                        new CanonicalSpread(
                                                supported.expressions().get(0),
                                                supported.expressions().get(0).span())),
                                supported.span());

                UnsupportedOperationException failure =
                        assertThrows(
                                UnsupportedOperationException.class,
                                () ->
                                        new CanonicalToBytecodeLowerer(
                                                        language, source)
                                                .lower(sequence));

                String unsupportedKind =
                        sequence.expressions().get(0).getClass().getSimpleName();
                assertTrue(
                        failure.getMessage().contains(unsupportedKind),
                        () ->
                                "fail-closed message did not report canonical kind "
                                        + unsupportedKind
                                        + ": "
                                        + failure.getMessage());
            } finally {
                context.leave();
            }
        }

        System.out.println("PERF006_B1_UNSUPPORTED_FAIL_CLOSED=PASS");
    }

    private static void assertEquivalent(String characters) throws Exception {
        ProtosLanguage language = LANGUAGE_REF.get(null);
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                characters,
                                "perf006-b1-equivalence.protos")
                        .build();
        CanonicalSequence sequence = canonicalize(characters);

        Object ast = executeAst(language, source, sequence);
        Object bytecode =
                new CanonicalToBytecodeLowerer(language, source)
                        .lower(sequence)
                        .call();

        assertEquivalentValue(ast, bytecode);
    }

    private static CanonicalSequence canonicalize(String characters) {
        SurfaceSequence surface =
                new ProtosParser(characters).parseProgram();
        return (CanonicalSequence)
                new Canonicalizer().canonicalize(surface);
    }

    private static Object executeAst(
            ProtosLanguage language,
            Source source,
            CanonicalSequence sequence) {
        ProtosRootFactory roots =
                ProtosRootFactory.sourceBound(language, source);
        CanonicalToTruffleLowerer lowerer =
                new CanonicalToTruffleLowerer(roots);
        CallTarget target =
                roots.createCallTarget(lowerer.lower(sequence));
        return target.call();
    }

    private static void assertEquivalentValue(Object ast, Object bytecode) {
        assertEquals(ast.getClass(), bytecode.getClass());

        if (ast == ProtosBooleanValue.TRUE
                || ast == ProtosBooleanValue.FALSE
                || ast == ProtosNullValue.INSTANCE) {
            assertSame(ast, bytecode);
            return;
        }

        if (ast instanceof ProtosIntegerValue astInteger) {
            ProtosIntegerValue bytecodeInteger =
                    assertInstanceOf(ProtosIntegerValue.class, bytecode);
            assertEquals(astInteger.value(), bytecodeInteger.value());
            return;
        }

        if (ast instanceof ProtosFloatValue astFloat) {
            ProtosFloatValue bytecodeFloat =
                    assertInstanceOf(ProtosFloatValue.class, bytecode);
            assertEquals(
                    Double.doubleToRawLongBits(astFloat.value()),
                    Double.doubleToRawLongBits(bytecodeFloat.value()));
            return;
        }

        if (ast instanceof ProtosStringValue astString) {
            ProtosStringValue bytecodeString =
                    assertInstanceOf(ProtosStringValue.class, bytecode);
            assertEquals(astString.value(), bytecodeString.value());
            return;
        }

        throw new AssertionError(
                "PERF006-B1 comparison does not know value family "
                        + ast.getClass().getName());
    }
}
