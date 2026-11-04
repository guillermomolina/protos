/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT at the repository root.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * I115: falsifiable structural oracle for the exact factorial ifTrue body.
 * A correct numerical result alone does not establish physical inlining.
 * The sole source callback is the body of ifTrue; it must not produce
 * a fourth physical Bytecode DSL root.
 */
final class ProtosI115FactorialControlInliningTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    private static final String FACTORIAL = """
            factorial: (n) => {
                result: 1

                (n > 1).ifTrue() {
                    result = n * factorial(n - 1)
                }

                result
            }

            run: () => {
                factorial(20)
            }

            run()
            """;

    @Test
    void factorialControlCallbackHasNoPhysicalRoot() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                CanonicalSequence sequence = (CanonicalSequence) new Canonicalizer()
                        .canonicalize(new ProtosParser(FACTORIAL).parseProgram());
                Source source = Source.newBuilder(
                        ProtosLanguage.ID, FACTORIAL, "i115-factorial-inline.protos").build();
                ProtosBytecodeRootNode module =
                        new CanonicalToBytecodeLowerer(LANGUAGE_REF.get(null), source)
                                .lowerRoot(sequence);
                module.getRootNodes().ensureComplete();
                List<ProtosBytecodeRootNode> roots = module.getRootNodes().getNodes()
                        .stream().map(ProtosBytecodeRootNode.class::cast).toList();

                System.out.println("I115_FACTORIAL_PHYSICAL_ROOT_COUNT=" + roots.size());
                for (int index = 0; index < roots.size(); index++) {
                    ProtosBytecodeRootNode candidate = roots.get(index);
                    var section = candidate.getBytecodeNode().getSourceSection();
                    System.out.println("I115_FACTORIAL_ROOT_" + index + "="
                            + (section == null ? "NO_SOURCE" : section.getCharacters()
                                    .toString().replace('\n', ' ')));
                }

                Object result = module.getCallTarget().call(
                        new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation());
                assertEquals("2432902008176640000",
                        ProtosNumericValueSupport.integerDecimalText(result),
                        "factorial(20) must still compute correctly");
                assertEquals(3, roots.size(),
                        "Expected only module, factorial and run roots. "
                        + "A fourth root indicates the ifTrue callback still "
                        + "has a physical Closure activation.");
                System.out.println("I115_FACTORIAL_IFTRUE_PHYSICAL_INLINE=PASS");
            } finally {
                context.leave();
            }
        }
    }
}
