package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.Instruction;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * I109-A / PLAT063-C acceptance of materialized C-prime bytecode.
 *
 * <p>Unlike source-emission assertions, this oracle builds the
 * actual untagged C-prime root through Truffle Bytecode DSL and
 * inspects its materialized instruction sequence.
 *
 * <p>No guest callback needs to execute to verify the physical
 * number and identity of the emitted operations.
 */
final class ProtosI109ABytecodeMaterializationTest {

    /**
     * I113: the local each sequence. The phase operations are shared by
     * every structured protocol, so they are counted within the local each
     * branch, which the dispatcher emits before the foreign each branch.
     */
    private static final String[] LOCAL_EACH_OPERATIONS = {
        "IsStructuredLocalEach",
        "PrepareStructuredLocalEach",
        "StructuredHasNext",
        "StructuredPrepareChildCall",
        "StructuredAdvance",
        "StructuredFinish"
    };

    private static final String[] OLD_FAMILIES = {
        "Array",
        "Bytes",
        "Environment",
        "IdentityMap",
        "Map"
    };

    private static List<String> materializedInstructions() {
        var roots = ProtosBytecodeRootNodeGen.create(
                null,
                BytecodeConfig.DEFAULT,
                builder -> {
                    builder.beginRoot();
                    builder.beginBlock();

                    BytecodeLocal result =
                            builder.createLocal("result", null);
                    BytecodeLocal preparedCall =
                            builder.createLocal("preparedCall", null);
                    BytecodeLocal childResult =
                            builder.createLocal("childResult", null);
                    BytecodeLocal resumeValue =
                            builder.createLocal("resumeValue", null);

                    builder.beginStoreLocal(preparedCall);
                    builder.emitLoadArgument(0);
                    builder.endStoreLocal();

                    ProtosStructuredDispatchLowerer
                            .emitStructuredInvocationForRuntime(
                                    builder,
                                    result,
                                    preparedCall,
                                    childResult,
                                    resumeValue);

                    builder.beginReturn();
                    builder.emitLoadLocal(result);
                    builder.endReturn();

                    builder.endBlock();
                    builder.endRoot();
                });

        roots.ensureComplete();

        return roots.getNode(0)
                .getBytecodeNode()
                .getInstructionsAsList()
                .stream()
                .map(Instruction::getName)
                .toList();
    }

    /**
     * Exact operation match: shared I113 names are prefixes of one another
     * (for example {@code StructuredFinish} and
     * {@code StructuredFinishWithChildResult}).
     */
    private static boolean matchesOperation(
            String instructionName,
            String operation) {
        return java.util.regex.Pattern.compile(
                        "(^|\\.)" + java.util.regex.Pattern.quote(operation) + "($|[$#_\\[])",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(instructionName)
                .find();
    }

    private static List<String> localEachBranch(List<String> instructions) {
        int start = position(instructions, "IsStructuredLocalEach");
        int end = position(instructions, "IsStructuredForeignEach");
        assertTrue(start >= 0 && end > start, instructions::toString);
        return instructions.subList(start, end);
    }

    private static long countOperation(
            List<String> instructions,
            String operation) {
        return instructions.stream()
                .filter(name -> matchesOperation(name, operation))
                .count();
    }

    private static int position(
            List<String> instructions,
            String operation) {
        for (int index = 0; index < instructions.size(); index++) {
            if (matchesOperation(instructions.get(index), operation)) {
                return index;
            }
        }
        return -1;
    }

    private static String relevantInstructions(
            List<String> instructions) {
        return instructions.stream()
                .filter(name -> {
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    return lower.contains("each")
                            || lower.contains("foreign")
                            || lower.contains("mapreadlookup");
                })
                .toList()
                .toString();
    }

    @Test
    void exactlyOnePhysicalSequenceForFiveLocalEachFamilies() {
        List<String> instructions = materializedInstructions();

        assertFalse(instructions.isEmpty());
        List<String> branch = localEachBranch(instructions);

        for (String operation : LOCAL_EACH_OPERATIONS) {
            assertEquals(
                    1,
                    countOperation(branch, operation),
                    "Unexpected materialized count: " + operation
                            + " in " + relevantInstructions(instructions));
        }
    }

    @Test
    void oldPerFamilyOperationsAreNotMaterialized() {
        List<String> instructions = materializedInstructions();

        for (String family : OLD_FAMILIES) {
            String[] oldOperations = {
                "IsStructured" + family + "EachCall",
                "PrepareStructured" + family + "EachCall",
                "Structured" + family + "EachHasNext",
                "AdvanceStructured" + family + "Each",
                "FinishStructured" + family + "Each"
            };

            for (String operation : oldOperations) {
                assertEquals(
                        0,
                        countOperation(instructions, operation),
                        "Legacy operation remains: " + operation);
            }
        }
    }

    @Test
    void unrelatedDispatchOperationsRemainMaterialized() {
        List<String> instructions = materializedInstructions();

        assertEquals(
                1,
                countOperation(
                        instructions,
                        "IsStructuredForeignEach"));

        assertEquals(
                1,
                countOperation(
                        instructions,
                        "IsStructuredIdentityMapAtIfAbsent"));

        assertEquals(
                1,
                countOperation(
                        instructions,
                        "IsStructuredMapReadLookup"));
    }

    @Test
    void everyStructuredProtocolMaterializesConcreteAdmission() {
        List<String> instructions = materializedInstructions();
        String[] admissions = {
            "IsStructuredMapMatch",
            "IsStructuredCaseOf",
            "IsStructuredChildOutcomeSequence",
            "IsStructuredObjectCall",
            "IsStructuredImportCall",
            "IsStructuredEnsure",
            "IsStructuredErrorHandler",
            "IsStructuredBoolean",
            "IsStructuredLocalEach",
            "IsStructuredForeignEach",
            "IsStructuredIdentityMapAtIfAbsent",
            "IsStructuredMapReadLookup",
            "IsStructuredMapAtPut",
            "IsStructuredMapRemove"
        };

        for (String admission : admissions) {
            assertTrue(countOperation(instructions, admission) >= 1,
                    "Missing concrete admission: " + admission);
        }
        assertEquals(0, countOperation(instructions, "IsStructuredCall"));
        assertEquals(0, countOperation(instructions, "PrepareStructuredCall"));
    }

    @Test
    void sharedIterationOperationsRetainExecutionOrder() {
        List<String> instructions = localEachBranch(materializedInstructions());

        int preparation = position(instructions,
                "PrepareStructuredLocalEach");
        int condition = position(instructions,
                "StructuredHasNext");
        int callback = position(instructions,
                "StructuredPrepareChildCall");
        int advance = position(instructions,
                "StructuredAdvance");
        int finish = position(instructions,
                "StructuredFinish");

        assertTrue(
                preparation >= 0,
                () -> "Preparation missing in "
                        + relevantInstructions(instructions));
        assertTrue(condition > preparation, "Condition order");
        assertTrue(callback > condition, "Callback preparation order");
        assertTrue(advance > callback, "Advance order");
        assertTrue(finish > advance, "Finish order");
    }
}
