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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * D237 regression evidence for specialized collection iteration.
 *
 * <p>The standard path retains its native iterator. The ordinary alternative
 * must construct the callback argument before selecting the method, exactly
 * as an ordinary send does. Both paths share the receiver evaluated once.
 */
final class ProtosI115EachSelectionOrderingTest {

    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void arrayEachRetainsNativeIterationAndArgumentFirstFallback()
            throws Exception {
        assertEachOrdering(
                """
                run: (collection) => {
                    marker: 0
                    collection.each((element) => element)
                }
                run(Array(1, 2))
                """,
                "ArrayEachNext");
    }

    @Test
    void associationEachRetainsNativeIterationAndArgumentFirstFallback()
            throws Exception {
        assertEachOrdering(
                """
                run: (collection) => {
                    marker: 0
                    collection.each((key, value) => value)
                }
                run(Map())
                """,
                "AssociationEachNext");
    }

    private static void assertEachOrdering(
            String characters, String nativeIterator) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosBytecodeRootNode root = lowerRoot(characters);
                root.getRootNodes().ensureComplete();

                ProtosBytecodeRootNode runRoot =
                        assertInstanceOf(
                                ProtosBytecodeRootNode.class,
                                root.getRootNodes().getNode(1));

                List<String> instructions =
                        runRoot.getBytecodeNode()
                                .getInstructionsAsList()
                                .stream()
                                .map(com.oracle.truffle.api.bytecode.Instruction::getName)
                                .toList();

                int iterator = firstIndex(instructions, nativeIterator);
                int argument =
                        firstIndex(instructions, "MaterializeCurrentClosure");
                int selection = firstIndex(instructions, "GetMethod");
                int invocation = firstIndex(instructions, "CallUnaryMethod");

                assertTrue(
                        iterator >= 0,
                        () -> "Native iterator is missing: " + instructions);
                assertTrue(
                        argument >= 0,
                        () -> "Callback materialization is missing: " + instructions);
                assertTrue(
                        selection >= 0,
                        () -> "Ordinary method selection is missing: " + instructions);
                assertTrue(
                        invocation >= 0,
                        () -> "Ordinary method invocation is missing: " + instructions);

                assertTrue(
                        argument < selection,
                        () -> "The each argument must precede method selection: "
                                + instructions);
                assertTrue(
                        selection < invocation,
                        () -> "The selected each method must precede invocation: "
                                + instructions);
            } finally {
                context.leave();
            }
        }
    }

    private static int firstIndex(List<String> instructions, String suffix) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).endsWith(suffix)) {
                return index;
            }
        }
        return -1;
    }

    private static ProtosBytecodeRootNode lowerRoot(String characters) {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                characters,
                                "i115-each-selection-ordering.protos")
                        .build();

        CanonicalSequence sequence =
                (CanonicalSequence)
                        new Canonicalizer()
                                .canonicalize(
                                        new ProtosParser(characters).parseProgram());

        return new CanonicalToBytecodeLowerer(
                        LANGUAGE_REF.get(null), source)
                .lowerRoot(sequence);
    }
}
