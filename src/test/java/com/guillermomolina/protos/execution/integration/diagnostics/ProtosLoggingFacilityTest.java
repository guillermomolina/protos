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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Non-dispatch event boundary and independent evidence for the source-owned decimal algorithm. */
final class ProtosLoggingFacilityTest {
    // Independent host-decimal oracle; production source never calls this implementation.
    private static BigDecimal shortestDecimal(double magnitude) {
        BigDecimal exact = new BigDecimal(magnitude);
        for (int digits = 1; digits <= 17; digits++) {
            BigDecimal below = exact.round(new MathContext(digits, RoundingMode.FLOOR));
            BigDecimal above = exact.round(new MathContext(digits, RoundingMode.CEILING));
            boolean belowRoundTrips = below.doubleValue() == magnitude;
            boolean aboveRoundTrips = above.doubleValue() == magnitude;
            if (belowRoundTrips && aboveRoundTrips) {
                int order = exact.subtract(below).compareTo(above.subtract(exact));
                if (order < 0) {
                    return below;
                }
                if (order > 0) {
                    return above;
                }
                return below.unscaledValue().testBit(0) ? above : below;
            }
            if (belowRoundTrips) {
                return below;
            }
            if (aboveRoundTrips) {
                return above;
            }
        }
        throw new IllegalStateException("binary64 value has no 17-digit round-trip decimal");
    }

    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void facilitiesAreFrozenStatelessMembersOfTheirExactModulesOnly() throws Exception {
        ProtosPrelude prelude = core();
        ProtosObjectValue events = eventFacility(prelude);
        assertTrue(events.isFrozen());
        assertEquals(Set.of("recognizes", "isAttachableError"), events.localSlotsSnapshot().keySet());
        assertTrue(prelude.isStandardModuleMemberForRuntime(events));

        for (String module : List.of(
                "std:logging/log_event", "std:logging/text_formatter",
                "std:logging/json_formatter", "std:logging/logger", "std:regex/regex",
                "std:collections/array")) {
            ProtosObjectValue context = prelude.newExecutionContext();
            prelude.installStandardModuleMembersForRuntime(ProtosModuleKey.module(module), context);
            assertEquals(module.equals("std:logging/log_event"),
                    context.hasLocalSlot(ProtosLoggingFacility.EVENT_BOOTSTRAP_SLOT));
            assertFalse(context.hasLocalSlot("_logTextFacility"));
            assertFalse(context.hasLocalSlot("_logJsonFacility"));
        }
    }

    @Test
    void shortestDecimalAnswersCoefficientAndExponentWithoutTrailingZeros() throws Exception {
        try (ProtosHostedExecutionTestFixture hosted = decimalSource()) {
            ProtosActivation activation = hosted.activation();
            ProtosObjectValue text = decimalModule(hosted);

            assertDecimal(hosted, text, activation, 1.0, "1", 0);
            assertDecimal(hosted, text, activation, 1.23, "123", -2);
            // Both adjacent shortest decimals round-trip; the exact midpoint chooses even.
            assertDecimal(hosted, text, activation, 562949953421312.25, "5629499534213122", -1);
            assertDecimal(hosted, text, activation, 562949953421312.75, "5629499534213128", -1);
            assertDecimal(hosted, text, activation, -1.5, "15", -1);
            assertDecimal(hosted, text, activation, 100.0, "1", 2);
            assertDecimal(hosted, text, activation, 0.1, "1", -1);
            assertDecimal(hosted, text, activation, 0.1 + 0.2, "30000000000000004", -17);
            assertDecimal(hosted, text, activation, 1e21, "1", 21);
            assertDecimal(hosted, text, activation, Double.MIN_VALUE, "5", -324);
            assertDecimal(hosted, text, activation, Double.MAX_VALUE, "17976931348623157", 292);
            assertDecimal(hosted, text, activation, Double.MIN_NORMAL, "22250738585072014", -324);
        }
    }

    @Test
    void shortestDecimalRejectsZeroNonFiniteAndNonFloatArguments() throws Exception {
        try (ProtosHostedExecutionTestFixture hosted = decimalSource()) {
            ProtosActivation activation = hosted.activation();
            ProtosObjectValue text = decimalModule(hosted);

            for (Object invalid :
                    List.of(
                            new ProtosFloatValue(0.0),
                            new ProtosFloatValue(-0.0),
                            new ProtosFloatValue(Double.NaN),
                            new ProtosFloatValue(Double.POSITIVE_INFINITY),
                            new ProtosFloatValue(Double.NEGATIVE_INFINITY),
                            Long.valueOf(1),
                            new ProtosStringValue("1.0"))) {
                assertThrows(
                        ProtosSignalException.class,
                        () -> hosted.callEntered(() -> ProtosInvocation.invokeMessage(
                                text, "shortestDecimal", List.of(invalid), activation)));
            }
        }
    }

    @Test
    void shortestDecimalRoundTripsWithMinimalDigitsAndNearestChoice() throws Exception {
        try (ProtosHostedExecutionTestFixture hosted = decimalSource()) {
            ProtosObjectValue text = decimalModule(hosted);
            SplittableRandom random = new SplittableRandom(0x1B015L);
            for (int index = 0; index < 20_000; index++) {
                double value = Double.longBitsToDouble(random.nextLong() & Long.MAX_VALUE);
                if (!Double.isFinite(value) || value == 0.0) {
                    continue;
                }
                assertShortest(hosted, text, value);
            }
            for (double value :
                    new double[] {
                        Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE, 1.0, 2.0, 0.5,
                        9007199254740993.0, 2e23, 1e-7, 1e21, 1e22, 123456.789, 5e-324, 4.9e-324
                    }) {
                assertShortest(hosted, text, value);
            }
        }
    }

    private static void assertShortest(
            ProtosHostedExecutionTestFixture hosted, ProtosObjectValue text, double value) {
        BigDecimal expected = shortestDecimal(value).stripTrailingZeros();
        ProtosArrayValue actual = assertDecimal(
                hosted, text, hosted.activation(), value,
                expected.unscaledValue().toString(), -expected.scale());
        BigDecimal decimal = new BigDecimal(
                ProtosNumericValueSupport.integerDecimalText(actual.indexedAt(0)))
                .scaleByPowerOfTen(ProtosNumericValueSupport.exactInt(actual.indexedAt(1)));
        assertTrue(decimal.unscaledValue().mod(java.math.BigInteger.TEN).signum() != 0);
        BigDecimal exact = new BigDecimal(value);
        MathContext floor = new MathContext(decimal.precision(), RoundingMode.FLOOR);
        MathContext ceiling = new MathContext(decimal.precision(), RoundingMode.CEILING);
        BigDecimal below = exact.round(floor);
        BigDecimal above = exact.round(ceiling);
        if (below.doubleValue() == value && above.doubleValue() == value) {
            int distance = exact.subtract(below).compareTo(above.subtract(exact));
            BigDecimal nearest = distance < 0 ? below : distance > 0 ? above
                    : below.unscaledValue().testBit(0) ? above : below;
            assertEquals(0, decimal.compareTo(nearest), () -> "nearest/even choice of " + value);
        }
        assertEquals(value, decimal.doubleValue(), () -> "round trip of " + value);
        int digits = decimal.stripTrailingZeros().precision();
        if (digits > 1) {
            MathContext shorter = new MathContext(digits - 1, RoundingMode.FLOOR);
            MathContext shorterUp = new MathContext(digits - 1, RoundingMode.CEILING);
            assertFalse(exact.round(shorter).doubleValue() == value, () -> "shorter below " + value);
            assertFalse(exact.round(shorterUp).doubleValue() == value, () -> "shorter above " + value);
        }
        BigDecimal toStringDigits = new BigDecimal(Double.toString(value)).stripTrailingZeros();
        assertTrue(digits <= toStringDigits.precision(), () -> "not shorter than JDK for " + value);
    }

    private static ProtosArrayValue assertDecimal(
            ProtosHostedExecutionTestFixture hosted,
            ProtosObjectValue text,
            ProtosActivation activation,
            double value,
            String coefficient,
            int exponent) {
        ProtosArrayValue result =
                assertInstanceOf(
                        ProtosArrayValue.class,
                        hosted.callEntered(() -> ProtosInvocation.invokeMessage(
                                text,
                                "shortestDecimal",
                                List.of(new ProtosFloatValue(value)),
                                activation)));
        assertTrue(result.isFrozen());
        assertEquals(2, result.indexedSize());
        assertTrue(ProtosNumericValueSupport.isCurrentInteger(result.indexedAt(0)));
        assertTrue(ProtosNumericValueSupport.isCurrentInteger(result.indexedAt(1)));
        assertEquals(coefficient, ProtosNumericValueSupport.integerDecimalText(result.indexedAt(0)));
        assertEquals(
                exponent,
                ProtosNumericValueSupport.exactInt(
                        assertInstanceOf(Long.class, result.indexedAt(1))));
        return result;
    }

    private static ProtosObjectValue eventFacility(ProtosPrelude prelude) {
        return ProtosStandardModuleMemberTestSupport.member(
                prelude,
                ProtosLoggingFacility.EVENT_MODULE_KEY.canonicalId(),
                ProtosLoggingFacility.EVENT_BOOTSTRAP_SLOT);
    }

    private static ProtosHostedExecutionTestFixture decimalSource() throws Exception {
        ProtosExactModuleOverlayResolver resolver = new ProtosExactModuleOverlayResolver(
                Map.of("logging-decimal-test", new ProtosExactModuleOverlayResolver.ExactModule(
                        ProtosModuleKey.source("logging-test:Decimal.protos"),
                        STANDARD_LIBRARY.resolve("logging/internal/Decimal.protos"))),
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
        return ProtosHostedExecutionTestFixture.open(new ProtosCoreBootstrap().bootstrap(CORE, resolver));
    }

    private static ProtosObjectValue decimalModule(ProtosHostedExecutionTestFixture hosted) {
        return assertInstanceOf(ProtosObjectValue.class,
                hosted.evaluatePersistent("<logging-decimal>", "import(\"logging-decimal-test\")"));
    }

    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(CORE, new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY));
    }
}
