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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosComplexArithmetic;
import com.guillermomolina.protos.runtime.ProtosComplexNumericSupport;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosMatrixValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosVectorValue;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * LIB024 Java-side guards for the D214 C1/S1 {@code std:math/LinearAlgebra} Vector and Matrix:
 * publication, representation, failure atomicity, D214-F1 reductions and the Complex contract.
 * Source-level API behavior lives in {@code protos/tests/library/math/linear-algebra/}.
 */
final class ProtosStandardLinearAlgebraProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final String MODULE = "std:math/LinearAlgebra";

    /* ---- A. Publication and import ---- */

    @Test
    void moduleExportsFrozenStandardPrototypesWithoutPreludeBindings() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosObjectValue vector = vectorPrototype(prelude);
        ProtosObjectValue matrix = matrixPrototype(prelude);

        assertTrue(prelude.bindings().readLocalSlot("Vector").isEmpty());
        assertTrue(prelude.bindings().readLocalSlot("Matrix").isEmpty());
        for (ProtosObjectValue prototype : List.of(vector, matrix)) {
            assertTrue(prototype.isFrozen());
            assertSame(ProtosObjectValue.rootObject(), prototype.parent().orElseThrow());
            assertTrue(prelude.isStandardModuleMemberForRuntime(prototype));
        }
        assertEquals(
                Set.of("call", "size", "at", "atPut", "add", "scale", "dot", "copy"),
                vector.localSlotsSnapshot().keySet());
        assertEquals(
                Set.of(
                        "call", "rows", "columns", "at", "atPut", "add", "scale", "matvec",
                        "matmul", "transpose", "copy"),
                matrix.localSlotsSnapshot().keySet());
        for (ProtosObjectValue prototype : List.of(vector, matrix)) {
            for (Object behavior : prototype.localSlotsSnapshot().values()) {
                assertTrue(((ProtosObjectValue) behavior).isFrozen());
            }
        }
        String source =
                Files.readString(STANDARD_LIBRARY.resolve("math").resolve("LinearAlgebra.protos"));
        // Doc examples may bind the members; no code line may redeclare them.
        for (String line : source.lines().toList()) {
            if (!line.strip().startsWith("//")) {
                assertFalse(line.contains("Vector:"), line);
                assertFalse(line.contains("Matrix:"), line);
            }
        }
    }

    @Test
    void explicitImportPublishesTheSameStandardPrototypesInEveryModuleInstance()
            throws Exception {
        ProtosPrelude prelude = prelude();
        String program =
                """
                LA: import("std:math/LinearAlgebra")
                Vector: LA.Vector
                Matrix: LA.Matrix
                [LA.Vector, LA.Matrix, Vector(1, 2), Matrix(1, 1, 5)]
                """;
        List<Object> first = elements(evaluate(program, prelude.newModuleActivation()));
        List<Object> second = elements(evaluate(program, prelude.newModuleActivation()));

        assertSame(vectorPrototype(prelude), first.get(0));
        assertSame(matrixPrototype(prelude), first.get(1));
        assertSame(first.get(0), second.get(0));
        assertSame(first.get(1), second.get(1));
        ProtosVectorValue vector = assertInstanceOf(ProtosVectorValue.class, first.get(2));
        ProtosMatrixValue matrix = assertInstanceOf(ProtosMatrixValue.class, first.get(3));
        assertTrue(vector.isOpen());
        assertTrue(matrix.isOpen());
        assertSame(first.get(0), vector.parent().orElseThrow());
        assertSame(first.get(1), matrix.parent().orElseThrow());
    }

    @Test
    void constructionFollowsOrdinaryInvocationOfChildPrototypes() throws Exception {
        ProtosPrelude prelude = prelude();
        List<Object> result =
                elements(
                        evaluate(
                                """
                                LA: import("std:math/LinearAlgebra")
                Vector: LA.Vector
                Matrix: LA.Matrix
                                Tagged: LA.Vector { label: 7 }
                                [Tagged, Tagged(1, 2), Tagged(1, 2).add(Tagged(3, 4))]
                                """,
                                prelude.newModuleActivation()));
        ProtosVectorValue tagged = assertInstanceOf(ProtosVectorValue.class, result.get(1));
        assertSame(result.get(0), tagged.parent().orElseThrow());
        // An algebraic result delegates directly to the standard prototype.
        ProtosVectorValue sum = assertInstanceOf(ProtosVectorValue.class, result.get(2));
        assertSame(vectorPrototype(prelude), sum.parent().orElseThrow());
    }

    /* ---- B. Construction ---- */

    @Test
    void constructorsStoreExactlyTheSuppliedElements() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        Object half = fraction(1, 2, prelude);
        Object complex = complex(new ProtosIntegerValue(1), new ProtosIntegerValue(2), prelude);
        Object big = big(BigInteger.ONE.shiftLeft(90), prelude);

        ProtosVectorValue empty = vector(a, prelude);
        assertEquals(0, empty.indexedSize());
        ProtosVectorValue three = vector(a, prelude, integer(3));
        assertEquals(1, three.indexedSize());
        assertSame(three.indexedAt(integer(0)), three.indexedSnapshot().get(0));
        assertTrue(ProtosIdentity.identical(integer(3), three.indexedAt(integer(0))));

        Object[] mixed = {integer(1), big, half, new ProtosFloatValue(2.5d), complex};
        ProtosVectorValue heterogeneous = vector(a, prelude, mixed);
        for (int index = 0; index < mixed.length; index++) {
            assertSame(mixed[index], heterogeneous.indexedAt(integer(index)));
        }

        ProtosMatrixValue matrix =
                matrix(a, prelude, 2, 3, integer(1), integer(2), integer(3), integer(4),
                        integer(5), integer(6));
        assertTrue(ProtosIdentity.identical(integer(2), matrix.rowsValue()));
        assertTrue(ProtosIdentity.identical(integer(3), matrix.columnsValue()));
        assertTrue(ProtosIdentity.identical(integer(4), matrix.indexedAt(integer(1), integer(0))));
        assertTrue(ProtosIdentity.identical(integer(3), matrix.indexedAt(integer(0), integer(2))));

        List<Object> spread =
                elements(
                        evaluate(
                                """
                                LA: import("std:math/LinearAlgebra")
                Vector: LA.Vector
                Matrix: LA.Matrix
                                values: [1, 2, 3, 4]
                                [Vector(...values).size(), Matrix(2, 2, ...values).at(1, 0)]
                                """,
                                a));
        assertTrue(ProtosIdentity.identical(integer(4), spread.get(0)));
        assertTrue(ProtosIdentity.identical(integer(3), spread.get(1)));
    }

    @Test
    void zeroVolumeShapesKeepTheirExactDimensionsIncludingBigIntegers() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        Object huge = big(BigInteger.ONE.shiftLeft(100), prelude);

        for (Object[] shape :
                new Object[][] {{integer(0), integer(5)}, {integer(5), integer(0)},
                    {huge, integer(0)}, {integer(0), huge}}) {
            ProtosMatrixValue matrix =
                    assertInstanceOf(
                            ProtosMatrixValue.class,
                            ProtosInvocation.invokeMessage(
                                    matrixPrototype(prelude), "call", List.of(shape), a));
            assertSame(shape[0], invoke(matrix, "rows", a));
            assertSame(shape[1], invoke(matrix, "columns", a));
            assertTrue(matrix.indexedSnapshot().isEmpty());
            ProtosMatrixValue transposed =
                    assertInstanceOf(ProtosMatrixValue.class, invoke(matrix, "transpose", a));
            assertSame(shape[1], transposed.rowsValue());
            assertSame(shape[0], transposed.columnsValue());
        }
    }

    @Test
    void constructorsRejectWrongCountsDimensionsAndNumberImpostors() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosObjectValue matrix = matrixPrototype(prelude);
        ProtosObjectValue vector = vectorPrototype(prelude);
        Object huge = big(BigInteger.ONE.shiftLeft(100), prelude);

        List<List<Object>> invalidMatrices =
                List.of(
                        List.of(),
                        List.of(integer(2)),
                        List.of(integer(2), integer(2), integer(1), integer(2), integer(3)),
                        List.of(integer(1), integer(2), integer(1), integer(2), integer(3)),
                        List.of(integer(-1), integer(0)),
                        List.of(integer(0), integer(-1)),
                        List.of(new ProtosFloatValue(1.0d), integer(1), integer(1)),
                        List.of(integer(1), fraction(1, 2, prelude), integer(1)),
                        List.of(Long.valueOf(1L), integer(1), integer(1)),
                        List.of(huge, integer(1), integer(1)),
                        List.of(huge, huge, integer(1)),
                        List.of(integer(0), integer(0), integer(1)));
        for (List<Object> arguments : invalidMatrices) {
            assertSignals(() -> ProtosInvocation.invokeMessage(matrix, "call", arguments, a));
        }
        for (Object impostor : impostors(prelude)) {
            assertSignals(
                    () -> ProtosInvocation.invokeMessage(vector, "call", List.of(impostor), a));
            assertSignals(
                    () ->
                            ProtosInvocation.invokeMessage(
                                    matrix,
                                    "call",
                                    List.of(integer(1), integer(2), integer(1), impostor),
                                    a));
        }
    }

    /* ---- C. Indexing ---- */

    @Test
    void indexingAdmitsOnlyExactInRangeIntegers() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosVectorValue vector = vector(a, prelude, integer(10), integer(20), integer(30));
        ProtosMatrixValue matrix =
                matrix(a, prelude, 2, 2, integer(1), integer(2), integer(3), integer(4));

        assertTrue(ProtosIdentity.identical(integer(20), invoke(vector, "at", a, integer(1))));
        assertTrue(
                ProtosIdentity.identical(
                        integer(3), invoke(matrix, "at", a, integer(1), integer(0))));
        Object bracket =
                evaluate(
                        """
                        LA: import("std:math/LinearAlgebra")
                Vector: LA.Vector
                Matrix: LA.Matrix
                        v: Vector(10, 20, 30)
                        v[2]
                        """,
                        a);
        assertTrue(ProtosIdentity.identical(integer(30), bracket));

        Object[] invalid = {
            integer(-1),
            integer(3),
            integer(Integer.MAX_VALUE),
            new ProtosIntegerValue((1L << 32) + 1),
            big(BigInteger.ONE.shiftLeft(64).add(BigInteger.ONE), prelude),
            new ProtosFloatValue(1.0d),
            fraction(1, 2, prelude),
            Long.valueOf(1L),
            BigInteger.ONE,
            ProtosNullValue.INSTANCE,
        };
        for (Object bad : invalid) {
            assertSignals(() -> invoke(vector, "at", a, bad));
            assertSignals(() -> invoke(vector, "atPut", a, bad, integer(0)));
            assertSignals(() -> invoke(matrix, "at", a, bad, integer(0)));
            assertSignals(() -> invoke(matrix, "at", a, integer(0), bad));
            assertSignals(() -> invoke(matrix, "atPut", a, integer(0), bad, integer(0)));
        }
        assertSignals(() -> invoke(matrix, "at", a, integer(2), integer(0)));
        assertSignals(() -> invoke(matrix, "at", a, integer(0), integer(2)));
        // m[row] and m[row, column] reduce to at with the wrong arity: no bracket API.
        assertSignals(() -> invoke(matrix, "at", a, integer(0)));
        assertSignals(() -> invoke(matrix, "at", a, integer(0), integer(0), integer(0)));
        assertSignals(() -> invoke(vector, "at", a));
        assertSignals(() -> invoke(vector, "at", a, integer(0), integer(0)));
        assertContents(vector, integer(10), integer(20), integer(30));
        assertContents(matrix, integer(1), integer(2), integer(3), integer(4));
    }

    /* ---- D. Failure order and atomicity ---- */

    @Test
    void atPutValidatesFrozenThenBoundsThenNumberAndFailsAtomically() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosStringValue notNumber = new ProtosStringValue("x");
        ProtosVectorValue vector = vector(a, prelude, integer(1), integer(2));
        ProtosMatrixValue matrix = matrix(a, prelude, 1, 2, integer(1), integer(2));

        // Index invalid and value invalid; index valid and value invalid.
        assertSignals(() -> invoke(vector, "atPut", a, integer(5), notNumber));
        assertSignals(() -> invoke(vector, "atPut", a, integer(0), notNumber));
        assertSignals(() -> invoke(matrix, "atPut", a, integer(1), integer(0), notNumber));
        assertSignals(() -> invoke(matrix, "atPut", a, integer(0), integer(0), notNumber));
        for (Object impostor : impostors(prelude)) {
            assertSignals(() -> invoke(vector, "atPut", a, integer(0), impostor));
            assertSignals(() -> invoke(matrix, "atPut", a, integer(0), integer(1), impostor));
        }
        assertContents(vector, integer(1), integer(2));
        assertContents(matrix, integer(1), integer(2));

        // Closed objects still accept writes to existing elements; atPut answers the value.
        vector.close();
        matrix.close();
        Object half = fraction(1, 2, prelude);
        assertSame(half, invoke(vector, "atPut", a, integer(1), half));
        assertSame(half, invoke(matrix, "atPut", a, integer(0), integer(1), half));
        assertContents(vector, integer(1), half);
        assertContents(matrix, integer(1), half);
        assertEquals(2, vector.indexedSize());

        // Frozen first: every combination fails and nothing changes.
        vector.freeze();
        matrix.freeze();
        assertSignals(() -> invoke(vector, "atPut", a, integer(9), notNumber));
        assertSignals(() -> invoke(vector, "atPut", a, integer(0), integer(7)));
        assertSignals(() -> invoke(matrix, "atPut", a, integer(9), integer(9), notNumber));
        assertSignals(() -> invoke(matrix, "atPut", a, integer(0), integer(0), integer(7)));
        assertContents(vector, integer(1), half);
        assertContents(matrix, integer(1), half);
    }

    /* ---- E. Algebra ---- */

    @Test
    void vectorAlgebraAnswersFreshIndependentResults() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosVectorValue left = vector(a, prelude, integer(1), fraction(1, 2, prelude));
        ProtosVectorValue right = vector(a, prelude, integer(3), new ProtosFloatValue(0.5d));

        ProtosVectorValue sum = assertInstanceOf(ProtosVectorValue.class, invoke(left, "add", a, right));
        assertContents(sum, integer(4), new ProtosFloatValue(1.0d));
        ProtosVectorValue scaled =
                assertInstanceOf(ProtosVectorValue.class, invoke(left, "scale", a, integer(4)));
        assertContents(scaled, integer(4), integer(2));
        assertTrue(
                ProtosIdentity.identical(
                        new ProtosFloatValue(3.25d), invoke(left, "dot", a, right)));
        ProtosVectorValue copy = assertInstanceOf(ProtosVectorValue.class, invoke(left, "copy", a));
        assertNotSame(left, copy);
        assertTrue(copy.isOpen());
        invoke(copy, "atPut", a, integer(0), integer(99));
        assertContents(left, integer(1), fraction(1, 2, prelude));
        invoke(left, "atPut", a, integer(1), integer(5));
        assertContents(copy, integer(99), fraction(1, 2, prelude));
        assertContents(right, integer(3), new ProtosFloatValue(0.5d));

        ProtosVectorValue shorter = vector(a, prelude, integer(1));
        for (String selector : new String[] {"add", "dot"}) {
            assertSignals(() -> invoke(left, selector, a, shorter));
            assertSignals(() -> invoke(left, selector, a, integer(1)));
            assertSignals(() -> invoke(left, selector, a));
        }
        // No broadcasting of a scalar or a one-element Vector.
        assertSignals(() -> invoke(left, "scale", a, shorter));
        assertSignals(() -> invoke(left, "scale", a, new ProtosStringValue("2")));
        for (Object impostor : impostors(prelude)) {
            assertSignals(() -> invoke(left, "scale", a, impostor));
        }
        assertSignals(() -> invoke(left, "copy", a, integer(1)));
    }

    @Test
    void matrixAlgebraAnswersFreshIndependentResults() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosMatrixValue m =
                matrix(a, prelude, 2, 3, integer(1), integer(2), integer(3), integer(4),
                        integer(5), integer(6));
        ProtosMatrixValue n =
                matrix(a, prelude, 3, 2, integer(7), integer(8), integer(9), integer(10),
                        integer(11), integer(12));

        assertContents(
                assertInstanceOf(ProtosMatrixValue.class, invoke(m, "add", a, m)),
                integer(2), integer(4), integer(6), integer(8), integer(10), integer(12));
        assertContents(
                assertInstanceOf(ProtosMatrixValue.class, invoke(m, "scale", a, integer(-1))),
                integer(-1), integer(-2), integer(-3), integer(-4), integer(-5), integer(-6));
        assertContents(
                assertInstanceOf(
                        ProtosVectorValue.class,
                        invoke(m, "matvec", a, vector(a, prelude, integer(1), integer(0), integer(-1)))),
                integer(-2), integer(-2));
        ProtosMatrixValue product = assertInstanceOf(ProtosMatrixValue.class, invoke(m, "matmul", a, n));
        assertTrue(ProtosIdentity.identical(integer(2), product.rowsValue()));
        assertTrue(ProtosIdentity.identical(integer(2), product.columnsValue()));
        assertContents(product, integer(58), integer(64), integer(139), integer(154));
        ProtosMatrixValue transposed = assertInstanceOf(ProtosMatrixValue.class, invoke(m, "transpose", a));
        assertTrue(ProtosIdentity.identical(integer(3), transposed.rowsValue()));
        assertContents(
                transposed, integer(1), integer(4), integer(2), integer(5), integer(3), integer(6));
        ProtosMatrixValue copy = assertInstanceOf(ProtosMatrixValue.class, invoke(m, "copy", a));
        assertNotSame(m, copy);
        assertNotSame(m, transposed);
        invoke(copy, "atPut", a, integer(0), integer(0), integer(0));
        invoke(transposed, "atPut", a, integer(0), integer(0), integer(0));
        assertContents(
                m, integer(1), integer(2), integer(3), integer(4), integer(5), integer(6));

        // Empty contractions produce exact zeros of the full result shape.
        ProtosMatrixValue wide = matrix(a, prelude, 2, 0);
        ProtosMatrixValue tall = matrix(a, prelude, 0, 3);
        ProtosMatrixValue zeros = assertInstanceOf(ProtosMatrixValue.class, invoke(wide, "matmul", a, tall));
        assertContents(zeros, integer(0), integer(0), integer(0), integer(0), integer(0), integer(0));
        assertContents(
                assertInstanceOf(ProtosVectorValue.class, invoke(wide, "matvec", a, vector(a, prelude))),
                integer(0), integer(0));

        // Strict shapes.
        assertSignals(() -> invoke(m, "add", a, n));
        assertSignals(() -> invoke(m, "add", a, transposed));
        assertSignals(() -> invoke(m, "matmul", a, m));
        assertSignals(() -> invoke(m, "matvec", a, vector(a, prelude, integer(1), integer(2))));
        assertSignals(() -> invoke(m, "matvec", a, n));
        assertSignals(() -> invoke(m, "matmul", a, vector(a, prelude, integer(1))));
        assertSignals(() -> invoke(m, "scale", a, m));
        assertSignals(() -> invoke(m, "transpose", a, integer(1)));

        // A result volume beyond physical storage fails instead of truncating.
        Object huge = big(BigInteger.ONE.shiftLeft(70), prelude);
        ProtosMatrixValue hugeTall =
                assertInstanceOf(
                        ProtosMatrixValue.class,
                        ProtosInvocation.invokeMessage(
                                matrixPrototype(prelude), "call", List.of(huge, integer(0)), a));
        ProtosMatrixValue hugeWide =
                assertInstanceOf(ProtosMatrixValue.class, invoke(hugeTall, "transpose", a));
        assertSignals(() -> invoke(hugeTall, "matmul", a, hugeWide));
        assertSignals(() -> invoke(hugeTall, "matvec", a, vector(a, prelude)));
        ProtosMatrixValue emptyProduct =
                assertInstanceOf(ProtosMatrixValue.class, invoke(hugeWide, "matmul", a, hugeTall));
        assertTrue(ProtosIdentity.identical(integer(0), emptyProduct.rowsValue()));
        assertTrue(ProtosIdentity.identical(integer(0), emptyProduct.columnsValue()));
    }

    /* ---- F. D214-F1 strict reductions ---- */

    @Test
    void emptyContractionIsExactIntegerZero() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        Object dot = invoke(vector(a, prelude), "dot", a, vector(a, prelude));
        assertInstanceOf(ProtosIntegerValue.class, dot);
        assertTrue(ProtosIdentity.identical(integer(0), dot));
    }

    @Test
    void floatReductionsStartWithTheFirstProductInAscendingOrder() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        double big = 1.0e16d;

        // -1.0 * 0.0 is -0.0; a +0.0 initial accumulator would turn it into +0.0.
        assertFloat(-0.0d, reductions(a, prelude, floats(-1.0d), floats(0.0d)));
        // (1e16 + 1) - 1e16 is 0.0 in ascending order; any other association answers 1.0.
        assertFloat(0.0d, reductions(a, prelude, floats(big, 1.0d, -big), floats(1.0d, 1.0d, 1.0d)));
        assertFloat(1.0d, reductions(a, prelude, floats(big, -big, 1.0d), floats(1.0d, 1.0d, 1.0d)));
        assertFloat(Double.NaN, reductions(a, prelude, floats(Double.NaN, 1.0d), floats(1.0d, 1.0d)));
        assertFloat(
                Double.NaN,
                reductions(
                        a, prelude,
                        floats(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY),
                        floats(1.0d, 1.0d)));
        assertFloat(
                Double.NEGATIVE_INFINITY,
                reductions(a, prelude, floats(Double.POSITIVE_INFINITY, 2.0d), floats(-1.0d, 1.0d)));
        assertFloat(
                2 * Double.MIN_VALUE,
                reductions(
                        a, prelude,
                        floats(Double.MIN_VALUE, Double.MIN_VALUE),
                        floats(1.0d, 1.0d)));
        assertFloat(
                Double.MIN_NORMAL,
                reductions(a, prelude, floats(Double.MIN_NORMAL * 2), floats(0.5d)));
        assertFloat(6.0d, reductions(a, prelude, floats(1.0d), floats(6.0d)));
        assertFloat(7.0d, reductions(a, prelude, floats(1.0d, 2.0d), floats(1.0d, 3.0d)));
    }

    @Test
    void exactAndMixedFamilyReductionsUseCanonicalNumberArithmetic() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        Object half = fraction(1, 2, prelude);
        Object third = fraction(1, 3, prelude);
        Object big = big(BigInteger.ONE.shiftLeft(70), prelude);

        for (Object result :
                reductions(a, prelude, new Object[] {half, third}, new Object[] {integer(1), integer(1)})) {
            assertTrue(ProtosIdentity.identical(fraction(5, 6, prelude), result));
        }
        for (Object result :
                reductions(a, prelude, new Object[] {big, integer(-1)}, new Object[] {big, big})) {
            assertTrue(
                    ProtosIdentity.identical(
                            big(
                                    BigInteger.ONE.shiftLeft(140)
                                            .subtract(BigInteger.ONE.shiftLeft(70)),
                                    prelude),
                            result));
        }
        // The Integer product starts the sum; the Float term then promotes operand-first.
        for (Object result :
                reductions(
                        a, prelude,
                        new Object[] {integer(1), new ProtosFloatValue(0.5d)},
                        new Object[] {integer(2), integer(2)})) {
            assertTrue(ProtosIdentity.identical(new ProtosFloatValue(3.0d), result));
        }
        for (Object result :
                reductions(a, prelude, new Object[] {half, half}, new Object[] {integer(2), integer(2)})) {
            assertInstanceOf(ProtosIntegerValue.class, result);
            assertTrue(ProtosIdentity.identical(integer(2), result));
        }
    }

    /* ---- G. Complex ---- */

    @Test
    void dotConjugatesOnlyTheReceiverWhileMatrixProductsConjugateNothing() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        Object i = complex(integer(0), integer(1), prelude);

        assertTrue(ProtosIdentity.identical(integer(1), invoke(vector(a, prelude, i), "dot", a, vector(a, prelude, i))));
        assertTrue(ProtosIdentity.identical(i, invoke(vector(a, prelude, integer(1)), "dot", a, vector(a, prelude, i))));
        assertTrue(
                ProtosIdentity.identical(
                        complex(integer(0), integer(-1), prelude),
                        invoke(vector(a, prelude, i), "dot", a, vector(a, prelude, integer(1)))));
        Object matvec =
                elementAt(invoke(matrix(a, prelude, 1, 1, i), "matvec", a, vector(a, prelude, i)), 0);
        assertTrue(ProtosIdentity.identical(integer(-1), matvec));
        Object matmul =
                invoke(
                        invoke(matrix(a, prelude, 1, 1, i), "matmul", a, matrix(a, prelude, 1, 1, i)),
                        "at", a, integer(0), integer(0));
        assertTrue(ProtosIdentity.identical(integer(-1), matmul));

        // Exact Fraction components stay exact.
        Object z = complex(fraction(1, 2, prelude), integer(1), prelude);
        Object expected =
                ProtosComplexArithmetic.binary(
                        ProtosComplexArithmetic.Operation.MULTIPLY,
                        complex(fraction(1, 2, prelude), integer(-1), prelude),
                        z,
                        prelude);
        assertTrue(ProtosIdentity.identical(expected, invoke(vector(a, prelude, z), "dot", a, vector(a, prelude, z))));
    }

    @Test
    void conjugationPreservesSignedZeroComponents() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        Object z = complex(new ProtosFloatValue(1.0d), new ProtosFloatValue(-0.0d), prelude);
        ProtosVectorValue left = vector(a, prelude, z);
        ProtosVectorValue one = vector(a, prelude, new ProtosFloatValue(1.0d));

        Object dot = invoke(left, "dot", a, one);
        assertTrue(ProtosIdentity.identical(complex(new ProtosFloatValue(1.0d), new ProtosFloatValue(0.0d), prelude), dot));
        Object matvec = elementAt(invoke(matrix(a, prelude, 1, 1, z), "matvec", a, one), 0);
        assertTrue(ProtosIdentity.identical(z, matvec));
    }

    @Test
    void complexProductsKeepTheCoupledSingleRoundingContract() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        double x = 1.0d + Math.scalb(1.0d, -30);
        Object z = complex(new ProtosFloatValue(x), new ProtosFloatValue(1.0d), prelude);
        Object conjugate = complex(new ProtosFloatValue(x), new ProtosFloatValue(-1.0d), prelude);

        // Re(z * z) = x*x - 1 = 2^-29 + 2^-60 exactly; separate host rounding answers 2^-29.
        Object expected =
                ProtosComplexArithmetic.binary(
                        ProtosComplexArithmetic.Operation.MULTIPLY, z, z, prelude);
        assertEquals(
                Math.scalb(1.0d, -29) + Math.scalb(1.0d, -60),
                ProtosNumericValueSupport.currentFloatValue(ProtosComplexNumericSupport.real(expected)));
        assertTrue(Math.scalb(1.0d, -29) == x * x - 1.0d, "the host rounding differs");
        Object matvec = elementAt(invoke(matrix(a, prelude, 1, 1, z), "matvec", a, vector(a, prelude, z)), 0);
        assertTrue(ProtosIdentity.identical(expected, matvec));
        Object matmul =
                invoke(
                        invoke(matrix(a, prelude, 1, 1, z), "matmul", a, matrix(a, prelude, 1, 1, z)),
                        "at", a, integer(0), integer(0));
        assertTrue(ProtosIdentity.identical(expected, matmul));
        Object hermitian =
                ProtosComplexArithmetic.binary(
                        ProtosComplexArithmetic.Operation.MULTIPLY, conjugate, z, prelude);
        assertTrue(
                ProtosIdentity.identical(
                        hermitian, invoke(vector(a, prelude, z), "dot", a, vector(a, prelude, z))));

        // Two products summed in order with the canonical addition.
        Object w = complex(new ProtosFloatValue(0.5d), new ProtosFloatValue(-2.0d), prelude);
        Object sum =
                ProtosComplexArithmetic.binary(
                        ProtosComplexArithmetic.Operation.ADD,
                        ProtosComplexArithmetic.binary(
                                ProtosComplexArithmetic.Operation.MULTIPLY, z, w, prelude),
                        ProtosComplexArithmetic.binary(
                                ProtosComplexArithmetic.Operation.MULTIPLY, w, z, prelude),
                        prelude);
        Object matvecSum =
                elementAt(
                        invoke(matrix(a, prelude, 1, 2, z, w), "matvec", a, vector(a, prelude, w, z)),
                        0);
        assertTrue(ProtosIdentity.identical(sum, matvecSum));
    }

    /* ---- H. Identity ---- */

    @Test
    void valuesKeepOrdinaryIdentityAndPrivatePayload() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosVectorValue first = vector(a, prelude, integer(1), integer(2));
        ProtosVectorValue second = vector(a, prelude, integer(1), integer(2));
        ProtosMatrixValue m = matrix(a, prelude, 1, 2, integer(1), integer(2));
        ProtosMatrixValue n = matrix(a, prelude, 1, 2, integer(1), integer(2));

        assertFalse(ProtosIdentity.identical(first, second));
        assertFalse(ProtosIdentity.identical(m, n));
        assertTrue(ProtosIdentity.identical(first, first));
        assertTrue(first.localSlotsSnapshot().isEmpty());
        assertTrue(m.localSlotsSnapshot().isEmpty());
        ArrayList<String> names = new ArrayList<>();
        m.appendLocalBindingsTo(names, new ArrayList<>());
        assertTrue(names.isEmpty());
        assertFalse(first.hasLocalSlot("elements"));
        assertFalse(m.hasLocalSlot("rows"));

        Object equal =
                evaluate(
                        """
                        LA: import("std:math/LinearAlgebra")
                Vector: LA.Vector
                Matrix: LA.Matrix
                        v: Vector(1, 2)
                        w: Vector(1, 2)
                        [v == w, v === w, v == v, v.copy() === v]
                        """,
                        a);
        List<Object> outcomes = elements(equal);
        assertSame(ProtosBooleanValue.FALSE, outcomes.get(0));
        assertSame(ProtosBooleanValue.FALSE, outcomes.get(1));
        assertSame(ProtosBooleanValue.TRUE, outcomes.get(2));
        assertSame(ProtosBooleanValue.FALSE, outcomes.get(3));

        ProtosVectorValue scaled = assertInstanceOf(ProtosVectorValue.class, invoke(first, "scale", a, integer(1)));
        invoke(scaled, "atPut", a, integer(0), integer(42));
        assertContents(first, integer(1), integer(2));
    }

    /* ---- I. PLAT068 R2-Float packed storage (I124) ---- */

    @Test
    void floatMatmulPacksLazilyOnceAndAnswersPackedResultsForChains() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosMatrixValue m = matrix(a, prelude, 2, 2, floats(1.0d, 2.0d, 3.0d, 4.0d));
        assertNotNull(references(m));
        assertNull(packed(m));

        ProtosMatrixValue first = assertInstanceOf(ProtosMatrixValue.class, invoke(m, "matmul", a, m));
        double[] backing = packed(m);
        assertNotNull(backing);
        assertNull(references(m));
        assertNotNull(packed(first));
        assertNull(references(first));
        assertNotSame(backing, packed(first));
        assertSame(matrixPrototype(prelude), first.parent().orElseThrow());
        assertTrue(first.isOpen());
        assertContents(first, floats(7.0d, 10.0d, 15.0d, 22.0d));

        ProtosMatrixValue again = assertInstanceOf(ProtosMatrixValue.class, invoke(m, "matmul", a, m));
        assertSame(backing, packed(m));
        double[] firstBacking = packed(first);
        ProtosMatrixValue chained =
                assertInstanceOf(ProtosMatrixValue.class, invoke(first, "matmul", a, again));
        assertSame(firstBacking, packed(first));
        assertNotNull(packed(chained));
        assertContents(chained, floats(199.0d, 290.0d, 435.0d, 634.0d));
        assertContents(m, floats(1.0d, 2.0d, 3.0d, 4.0d));

        // A rectangular 2 × 3 by 3 × 1 product keeps its exact shape.
        ProtosMatrixValue wide = matrix(a, prelude, 2, 3, floats(1.0d, 2.0d, 3.0d, 4.0d, 5.0d, 6.0d));
        ProtosMatrixValue column = matrix(a, prelude, 3, 1, floats(1.0d, 0.0d, -1.0d));
        ProtosMatrixValue rectangular =
                assertInstanceOf(ProtosMatrixValue.class, invoke(wide, "matmul", a, column));
        assertTrue(ProtosIdentity.identical(integer(2), rectangular.rowsValue()));
        assertTrue(ProtosIdentity.identical(integer(1), rectangular.columnsValue()));
        assertNotNull(packed(rectangular));
        assertContents(rectangular, floats(-2.0d, -2.0d));
    }

    @Test
    void nonFloatOperandsNeverEnterTheFloatKernel() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosMatrixValue floats = matrix(a, prelude, 1, 2, floats(0.5d, 2.0d));
        Object[][] mixedColumns = {
            {new ProtosFloatValue(2.0d), integer(3)},
            {new ProtosFloatValue(2.0d), big(BigInteger.ONE.shiftLeft(70), prelude)},
            {new ProtosFloatValue(2.0d), fraction(1, 2, prelude)},
            {new ProtosFloatValue(2.0d), complex(integer(0), integer(1), prelude)},
        };
        for (Object[] values : mixedColumns) {
            ProtosMatrixValue mixed = matrix(a, prelude, 2, 1, values);
            ProtosMatrixValue product =
                    assertInstanceOf(ProtosMatrixValue.class, invoke(floats, "matmul", a, mixed));
            assertNull(packed(mixed));
            assertNull(packed(product));
            Object expected =
                    ProtosComplexArithmetic.canonicalNumberBinary(
                            ProtosComplexArithmetic.Operation.ADD,
                            new ProtosFloatValue(1.0d),
                            ProtosComplexArithmetic.canonicalNumberBinary(
                                    ProtosComplexArithmetic.Operation.MULTIPLY,
                                    new ProtosFloatValue(2.0d),
                                    values[1],
                                    prelude),
                            prelude);
            assertContents(product, expected);
            assertContents(mixed, values);
            // The reversed product leaves the mixed receiver in R1 too.
            invoke(mixed, "matmul", a, floats);
            assertNull(packed(mixed));
        }
        // An Integer-only product keeps exact Integers.
        ProtosMatrixValue exact = matrix(a, prelude, 1, 1, integer(3));
        ProtosMatrixValue square = assertInstanceOf(ProtosMatrixValue.class, invoke(exact, "matmul", a, exact));
        assertNull(packed(square));
        assertContents(square, integer(9));
    }

    @Test
    void packedWritesUpdateLanesOrReturnTheSameMatrixToReferences() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        Object evaluated =
                evaluate(
                        """
                        LA: import("std:math/LinearAlgebra")
                        Tagged: LA.Matrix { label: 7 }
                        [Tagged(1, 2, 1.0, 2.0), Tagged]
                        """,
                        a);
        List<Object> pair = elements(evaluated);
        ProtosMatrixValue m = assertInstanceOf(ProtosMatrixValue.class, pair.get(0));
        m.createLocalSlot("note", integer(9));
        ProtosMatrixValue column = matrix(a, prelude, 2, 1, floats(1.0d, 1.0d));
        invoke(m, "matmul", a, column);
        double[] backing = packed(m);
        assertNotNull(backing);

        Object half = new ProtosFloatValue(0.5d);
        assertSame(half, invoke(m, "atPut", a, integer(0), integer(1), half));
        assertSame(backing, packed(m));
        assertContents(m, floats(1.0d, 0.5d));
        assertFloat(0.5d, List.of(invoke(m, "at", a, integer(0), integer(1))));

        // Failed writes change neither the state nor the contents.
        assertSignals(() -> invoke(m, "atPut", a, integer(0), integer(2), integer(1)));
        assertSignals(() -> invoke(m, "atPut", a, integer(0), integer(0), new ProtosStringValue("x")));
        for (Object impostor : impostors(prelude)) {
            assertSignals(() -> invoke(m, "atPut", a, integer(0), integer(0), impostor));
        }
        assertSame(backing, packed(m));
        assertContents(m, floats(1.0d, 0.5d));

        Object third = fraction(1, 3, prelude);
        assertSame(third, invoke(m, "atPut", a, integer(0), integer(0), third));
        assertNull(packed(m));
        assertNotNull(references(m));
        assertContents(m, third, new ProtosFloatValue(0.5d));
        assertInstanceOf(ProtosFloatValue.class, m.indexedAt(integer(0), integer(1)));
        assertSame(pair.get(1), m.parent().orElseThrow());
        assertTrue(ProtosIdentity.identical(integer(9), m.readLocalSlot("note").orElseThrow()));
        assertTrue(ProtosIdentity.identical(integer(1), m.rowsValue()));
        assertTrue(ProtosIdentity.identical(integer(2), m.columnsValue()));

        // Heterogeneous contents keep the generic product; restoring Floats re-admits it.
        assertContents(
                assertInstanceOf(ProtosMatrixValue.class, invoke(m, "matmul", a, column)),
                ProtosComplexArithmetic.canonicalNumberBinary(
                        ProtosComplexArithmetic.Operation.ADD,
                        ProtosComplexArithmetic.canonicalNumberBinary(
                                ProtosComplexArithmetic.Operation.MULTIPLY,
                                third,
                                new ProtosFloatValue(1.0d),
                                prelude),
                        new ProtosFloatValue(0.5d),
                        prelude));
        assertNull(packed(m));
        invoke(m, "atPut", a, integer(0), integer(0), new ProtosFloatValue(2.0d));
        invoke(m, "matmul", a, column);
        assertNotNull(packed(m));
        assertContents(m, floats(2.0d, 0.5d));

        // Frozen first, in either state.
        m.freeze();
        assertSignals(() -> invoke(m, "atPut", a, integer(9), integer(9), new ProtosStringValue("x")));
        assertSignals(() -> invoke(m, "atPut", a, integer(0), integer(0), new ProtosFloatValue(3.0d)));
        assertSignals(() -> invoke(m, "atPut", a, integer(0), integer(0), integer(3)));
        assertContents(m, floats(2.0d, 0.5d));
        assertContents(
                assertInstanceOf(ProtosMatrixValue.class, invoke(m, "matmul", a, column)),
                floats(2.5d));
    }

    @Test
    void frozenReferenceMatricesComputePackedProductsWithoutChangingState() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosMatrixValue m = matrix(a, prelude, 1, 2, floats(3.0d, -0.0d));
        ProtosMatrixValue column = matrix(a, prelude, 2, 1, floats(2.0d, 1.0d));
        m.createLocalSlot("note", integer(9));
        m.freeze();
        assertNotNull(references(m));
        assertNull(packed(m));

        ProtosMatrixValue first = assertInstanceOf(ProtosMatrixValue.class, invoke(m, "matmul", a, column));
        double[] backing = packed(m);
        assertNotNull(backing);
        assertNull(references(m));
        ProtosMatrixValue second = assertInstanceOf(ProtosMatrixValue.class, invoke(m, "matmul", a, column));
        assertSame(backing, packed(m));
        assertNotSame(first, second);
        assertNotNull(packed(first));
        assertFalse(first.isFrozen());
        assertContents(first, floats(6.0d));
        assertContents(second, floats(6.0d));

        assertTrue(m.isFrozen());
        assertContents(m, floats(3.0d, -0.0d));
        assertFloat(-0.0d, List.of(invoke(m, "at", a, integer(0), integer(1))));
        assertSame(matrixPrototype(prelude), m.parent().orElseThrow());
        assertTrue(ProtosIdentity.identical(integer(9), m.readLocalSlot("note").orElseThrow()));
        assertTrue(ProtosIdentity.identical(integer(1), m.rowsValue()));
        assertTrue(ProtosIdentity.identical(integer(2), m.columnsValue()));
        assertSignals(() -> invoke(m, "atPut", a, integer(0), integer(0), new ProtosFloatValue(1.0d)));
        assertSignals(() -> invoke(m, "atPut", a, integer(0), integer(0), integer(1)));
        assertSame(backing, packed(m));
        assertContents(m, floats(3.0d, -0.0d));
    }

    @Test
    void concurrentFirstPackingOfAFrozenMatrixPublishesOnePersistentArray() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        int volume = 4096;
        double[] values = new double[volume];
        for (int index = 0; index < volume; index++) {
            values[index] = index - 0.5d;
        }
        ProtosMatrixValue m = matrix(a, prelude, 1, volume, floats(values));
        m.freeze();
        int readers = 8;
        java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(readers);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(readers);
        try {
            List<java.util.concurrent.Future<double[]>> seen = new ArrayList<>();
            for (int reader = 0; reader < readers; reader++) {
                seen.add(pool.submit(() -> {
                    start.await();
                    return m.readonlyPackedFloatsForRuntime();
                }));
            }
            List<double[]> answers = new ArrayList<>();
            for (java.util.concurrent.Future<double[]> answer : seen) {
                answers.add(answer.get(10, java.util.concurrent.TimeUnit.SECONDS));
            }
            double[] persistent = packed(m);
            assertNotNull(persistent);
            for (double[] packed : answers) {
                assertSame(persistent, packed);
            }
            for (int index = 0; index < volume; index++) {
                assertEquals(
                        Double.doubleToRawLongBits(values[index]),
                        Double.doubleToRawLongBits(persistent[index]));
            }
        } finally {
            pool.shutdownNow();
        }
        assertContents(m, floats(values));
    }

    @Test
    void packedMatricesKeepIndependentSnapshotsCopiesAndAlgebra() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosMatrixValue identity = matrix(a, prelude, 2, 2, floats(1.0d, 0.0d, 0.0d, 1.0d));
        ProtosMatrixValue m = assertInstanceOf(
                ProtosMatrixValue.class,
                invoke(matrix(a, prelude, 2, 2, floats(1.0d, 2.0d, 3.0d, 4.0d)), "matmul", a, identity));
        assertNotNull(packed(m));

        List<Object> snapshot = m.indexedSnapshot();
        ProtosMatrixValue copy = assertInstanceOf(ProtosMatrixValue.class, invoke(m, "copy", a));
        ProtosMatrixValue transposed = assertInstanceOf(ProtosMatrixValue.class, invoke(m, "transpose", a));
        invoke(m, "atPut", a, integer(0), integer(0), new ProtosFloatValue(-1.0d));
        assertContents(snapshot, floats(1.0d, 2.0d, 3.0d, 4.0d));
        assertContents(copy, floats(1.0d, 2.0d, 3.0d, 4.0d));
        assertContents(transposed, floats(1.0d, 3.0d, 2.0d, 4.0d));
        invoke(copy, "matmul", a, identity);
        assertNotSame(packed(m), packed(copy));
        invoke(copy, "atPut", a, integer(1), integer(1), new ProtosFloatValue(8.0d));
        assertContents(m, floats(-1.0d, 2.0d, 3.0d, 4.0d));

        assertContents(
                assertInstanceOf(ProtosMatrixValue.class, invoke(m, "add", a, m)),
                floats(-2.0d, 4.0d, 6.0d, 8.0d));
        assertContents(
                assertInstanceOf(ProtosMatrixValue.class, invoke(m, "scale", a, integer(2))),
                floats(-2.0d, 4.0d, 6.0d, 8.0d));
        assertContents(
                assertInstanceOf(
                        ProtosVectorValue.class,
                        invoke(m, "matvec", a, vector(a, prelude, floats(1.0d, 1.0d)))),
                floats(1.0d, 7.0d));
        Object evaluated =
                evaluate(
                        """
                        LA: import("std:math/LinearAlgebra")
                        Matrix: LA.Matrix
                        unit: Matrix(2, 2, 1.0, 0.0, 0.0, 1.0)
                        packed: Matrix(2, 2, 1.0, 2.0, 3.0, 4.0).matmul(unit)
                        square: packed.matmul(packed)
                        [square.at(1, 1), square.rows(), square == square, square === packed]
                        """,
                        a);
        List<Object> outcomes = elements(evaluated);
        assertFloat(22.0d, List.of(outcomes.get(0)));
        assertTrue(ProtosIdentity.identical(integer(2), outcomes.get(1)));
        assertSame(ProtosBooleanValue.TRUE, outcomes.get(2));
        assertSame(ProtosBooleanValue.FALSE, outcomes.get(3));
    }

    @Test
    void packedKernelReproducesStrictD214F1Rounding() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        double big = 1.0e16d;
        double x = 1.0d + Math.scalb(1.0d, -30);

        assertFloat(-0.0d, packedProducts(a, prelude, floats(-1.0d), floats(0.0d)));
        assertFloat(0.0d, packedProducts(a, prelude, floats(big, 1.0d, -big), floats(1.0d, 1.0d, 1.0d)));
        assertFloat(1.0d, packedProducts(a, prelude, floats(big, -big, 1.0d), floats(1.0d, 1.0d, 1.0d)));
        // x*x - 1 rounds the product first; a fused multiply-add would keep 2^-60.
        assertTrue(Math.fma(x, x, -1.0d) != x * x - 1.0d, "fused and separate rounding differ");
        assertFloat(x * x - 1.0d, packedProducts(a, prelude, floats(x, -1.0d), floats(x, 1.0d)));
        assertFloat(Double.NaN, packedProducts(a, prelude, floats(Double.NaN, 1.0d), floats(1.0d, 1.0d)));
        assertFloat(
                Double.NaN,
                packedProducts(
                        a, prelude,
                        floats(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY),
                        floats(1.0d, 1.0d)));
        assertFloat(
                Double.POSITIVE_INFINITY,
                packedProducts(a, prelude, floats(Double.MAX_VALUE, Double.MAX_VALUE), floats(2.0d, 2.0d)));
        assertFloat(
                Double.NEGATIVE_INFINITY,
                packedProducts(a, prelude, floats(Double.POSITIVE_INFINITY, 2.0d), floats(-1.0d, 1.0d)));
        assertFloat(
                2 * Double.MIN_VALUE,
                packedProducts(a, prelude, floats(Double.MIN_VALUE, Double.MIN_VALUE), floats(1.0d, 1.0d)));
        assertFloat(Double.MIN_NORMAL, packedProducts(a, prelude, floats(Double.MIN_NORMAL * 2), floats(0.5d)));
        assertFloat(0.0d, packedProducts(a, prelude, floats(Double.MIN_VALUE), floats(0.5d)));
        assertFloat(6.0d, packedProducts(a, prelude, floats(1.0d), floats(6.0d)));
    }

    @Test
    void floatMatricesWithEmptyContractionOrVolumeKeepExactIntegerResults() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation a = prelude.newModuleActivation();
        ProtosMatrixValue wide = matrix(a, prelude, 2, 0);
        ProtosMatrixValue tall = matrix(a, prelude, 0, 2);
        ProtosMatrixValue zeros = assertInstanceOf(ProtosMatrixValue.class, invoke(wide, "matmul", a, tall));
        assertNull(packed(zeros));
        for (Object zero : zeros.indexedSnapshot()) {
            assertInstanceOf(ProtosIntegerValue.class, zero);
            assertTrue(ProtosIdentity.identical(integer(0), zero));
        }
        ProtosMatrixValue row = matrix(a, prelude, 1, 2, floats(1.0d, 2.0d));
        ProtosMatrixValue empty =
                assertInstanceOf(ProtosMatrixValue.class, invoke(row, "matmul", a, matrix(a, prelude, 2, 0)));
        assertTrue(ProtosIdentity.identical(integer(1), empty.rowsValue()));
        assertTrue(ProtosIdentity.identical(integer(0), empty.columnsValue()));
        assertTrue(empty.indexedSnapshot().isEmpty());
        assertNull(packed(row));
    }

    /* ---- Helpers ---- */

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(CORE, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
    }

    private static ProtosObjectValue vectorPrototype(ProtosPrelude prelude) {
        return ProtosStandardModuleMemberTestSupport.member(prelude, MODULE, "Vector");
    }

    private static ProtosObjectValue matrixPrototype(ProtosPrelude prelude) {
        return ProtosStandardModuleMemberTestSupport.member(prelude, MODULE, "Matrix");
    }

    private static ProtosVectorValue vector(
            ProtosActivation a, ProtosPrelude prelude, Object... elements) {
        return assertInstanceOf(
                ProtosVectorValue.class,
                ProtosInvocation.invokeMessage(
                        vectorPrototype(prelude), "call", List.of(elements), a));
    }

    private static ProtosMatrixValue matrix(
            ProtosActivation a, ProtosPrelude prelude, long rows, long columns, Object... values) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(integer(rows));
        arguments.add(integer(columns));
        arguments.addAll(List.of(values));
        return assertInstanceOf(
                ProtosMatrixValue.class,
                ProtosInvocation.invokeMessage(matrixPrototype(prelude), "call", arguments, a));
    }

    /* dot, matvec (1 × n) and matmul (1 × n by n × 1) of the same contraction. */
    private static List<Object> reductions(
            ProtosActivation a, ProtosPrelude prelude, Object[] left, Object[] right) {
        Object dot = invoke(vector(a, prelude, left), "dot", a, vector(a, prelude, right));
        Object matvec =
                elementAt(
                        invoke(matrix(a, prelude, 1, left.length, left), "matvec", a, vector(a, prelude, right)),
                        0);
        Object matmul =
                invoke(
                        invoke(
                                matrix(a, prelude, 1, left.length, left),
                                "matmul",
                                a,
                                matrix(a, prelude, right.length, 1, right)),
                        "at", a, integer(0), integer(0));
        return List.of(dot, matvec, matmul);
    }

    /* matmul of a 1 × n by n × 1 all-Float pair through the packed kernel, then again packed. */
    private static List<Object> packedProducts(
            ProtosActivation a, ProtosPrelude prelude, Object[] left, Object[] right) {
        ProtosMatrixValue row = matrix(a, prelude, 1, left.length, left);
        ProtosMatrixValue column = matrix(a, prelude, right.length, 1, right);
        ProtosMatrixValue first =
                assertInstanceOf(ProtosMatrixValue.class, invoke(row, "matmul", a, column));
        ProtosMatrixValue second =
                assertInstanceOf(ProtosMatrixValue.class, invoke(row, "matmul", a, column));
        assertNotNull(packed(row));
        assertNotNull(packed(column));
        assertNotNull(packed(first));
        return List.of(
                invoke(first, "at", a, integer(0), integer(0)),
                second.indexedSnapshot().get(0));
    }

    /* Test-side inspection of the private PLAT068 storage states; no guest surface exposes them. */
    private static double[] packed(ProtosMatrixValue matrix) {
        return storage(matrix) instanceof double[] packed ? packed : null;
    }

    private static Object[] references(ProtosMatrixValue matrix) {
        return storage(matrix) instanceof Object[] references ? references : null;
    }

    private static Object storage(ProtosMatrixValue matrix) {
        try {
            Field field = ProtosMatrixValue.class.getDeclaredField("storage");
            field.setAccessible(true);
            return field.get(matrix);
        } catch (ReflectiveOperationException unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    private static Object[] floats(double... values) {
        Object[] result = new Object[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = new ProtosFloatValue(values[index]);
        }
        return result;
    }

    private static void assertFloat(double expected, List<Object> results) {
        for (Object result : results) {
            assertInstanceOf(ProtosFloatValue.class, result);
            assertEquals(
                    Double.doubleToLongBits(expected),
                    Double.doubleToLongBits(ProtosNumericValueSupport.currentFloatValue(result)),
                    String.valueOf(expected));
            if (expected == 0.0d) {
                assertEquals(
                        Double.doubleToRawLongBits(expected),
                        Double.doubleToRawLongBits(ProtosNumericValueSupport.currentFloatValue(result)),
                        "signed zero");
            }
        }
    }

    private static Object elementAt(Object vector, long index) {
        return assertInstanceOf(ProtosVectorValue.class, vector).indexedAt(integer(index));
    }

    private static void assertContents(ProtosVectorValue vector, Object... expected) {
        assertContents(vector.indexedSnapshot(), expected);
    }

    private static void assertContents(ProtosMatrixValue matrix, Object... expected) {
        assertContents(matrix.indexedSnapshot(), expected);
    }

    private static void assertContents(List<Object> actual, Object... expected) {
        assertEquals(expected.length, actual.size());
        for (int index = 0; index < expected.length; index++) {
            assertTrue(
                    ProtosIdentity.identical(expected[index], actual.get(index)),
                    "element " + index);
        }
    }

    private static List<Object> impostors(ProtosPrelude prelude) {
        ProtosObjectValue number =
                (ProtosObjectValue) prelude.bindings().readLocalSlot("Number").orElseThrow();
        return List.of(
                ProtosNullValue.INSTANCE,
                ProtosBooleanValue.TRUE,
                new ProtosStringValue("1"),
                new ProtosObjectValue(ProtosObjectValue.rootObject()),
                new ProtosObjectValue(number),
                new ProtosObjectValue(prelude.integerPrototype()),
                Long.valueOf(1L),
                Double.valueOf(1.0d),
                BigInteger.ONE);
    }

    private static Object invoke(Object receiver, String selector, ProtosActivation a, Object... arguments) {
        return ProtosInvocation.invokeMessage(receiver, selector, List.of(arguments), a);
    }

    private static void assertSignals(Runnable operation) {
        assertThrows(ProtosSignalException.class, operation::run);
    }

    private static Object evaluate(String source, ProtosActivation activation) {
        return ProtosTestExecutionSupport.evaluate(source, activation);
    }

    private static List<Object> elements(Object array) {
        return assertInstanceOf(ProtosArrayValue.class, array).indexedSnapshot();
    }

    private static Object integer(long value) {
        return new ProtosIntegerValue(value);
    }

    private static Object big(BigInteger value, ProtosPrelude prelude) {
        return ProtosNumericValueSupport.integerFromHost(value, prelude);
    }

    private static Object fraction(long numerator, long denominator, ProtosPrelude prelude) {
        return ProtosNumericValueSupport.divideIntegersExactly(
                integer(numerator), integer(denominator), prelude);
    }

    private static Object complex(Object real, Object imaginary, ProtosPrelude prelude) {
        return ProtosComplexNumericSupport.normalize(real, imaginary, prelude);
    }
}
