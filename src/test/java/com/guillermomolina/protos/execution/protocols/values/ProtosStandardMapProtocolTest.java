/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosStandardMapProtocolTest {
    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    // Deliberately Java-side: the historical lifecycle test establishes
    // represented Map close/freeze state through ProtosMapValue directly.
    // Observable open-state Map semantics are owned by executable Protos tests.
    @Test
    void closedAndFrozenBoundaries() throws Exception {
        ProtosPrelude prelude = core();
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosMapValue map = new ProtosMapValue(prelude.mapPrototype());
        activation.context().createLocalSlot("m", map);

        ProtosTestExecutionSupport.evaluate("m.atPut(1,1)", activation);

        map.close();
        ProtosTestExecutionSupport.evaluate("m.atPut(1,2)", activation);
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate("m.atPut(2,2)", activation));
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate("m.remove(1)", activation));

        map.freeze();
        assertThrows(
                ProtosSignalException.class,
                () -> ProtosTestExecutionSupport.evaluate("m.atPut(1,3)", activation));
    }

    /*
     * I091: a key whose hash and == are guest methods; make(id, h) answers a fresh key whose
     * equality is by id and whose hash is the exact Integer h.
     */
    private static final String KEYS =
            "source: {\n"
                    + "    hash: () => { this.h }\n"
                    + "    equals: (other) => { this.id == other.id }\n"
                    + "}\n"
                    + "view: source.alias(\"equals\", \"==\")\n"
                    + "make: (i, hv) => view {\n"
                    + "    id: i\n"
                    + "    h: hv\n"
                    + "}\n";

    private static Object evaluate(String source) throws Exception {
        ProtosActivation activation = core().newModuleActivation();
        return ProtosTestExecutionSupport.evaluate(KEYS + source, activation);
    }

    @Test
    void hashesBeyondSignedLongAreExactKeysFoundThroughEquivalentObjects() throws Exception {
        assertSame(Boolean.TRUE, evaluate(
                "big: 1180591620717411303424\n"
                        + "m: Map()\n"
                        + "m[make(1, big)] = \"positive\"\n"
                        + "m[make(2, 0 - big)] = \"negative\"\n"
                        + "m[make(3, 9223372036854775807 + 1)] = \"boundary\"\n"
                        + "(m.size() == 3) &&\n"
                        + "    (m[make(1, 1180591620717411303424)] == \"positive\") &&\n"
                        + "    (m[make(2, 0 - 1180591620717411303424)] == \"negative\") &&\n"
                        + "    (m[make(3, 9223372036854775808)] == \"boundary\")"));
    }

    @Test
    void largeHashesAreNeverTruncatedToTheirLowBits() throws Exception {
        // 2^64 + 5 and 5 share their low 64 bits; equal keys under distinct hashes never match.
        assertSame(Boolean.TRUE, evaluate(
                "m: Map()\n"
                        + "m[make(1, 18446744073709551621)] = \"wide\"\n"
                        + "!m.containsKey(make(1, 5)) &&\n"
                        + "    !m.containsKey(make(1, 0 - 18446744073709551611)) &&\n"
                        + "    m.containsKey(make(1, 18446744073709551616 + 5))"));
    }

    @Test
    void legitimateLargeHashCollisionsKeepDistinctKeys() throws Exception {
        assertSame(Boolean.TRUE, evaluate(
                "big: 0 - 340282366920938463463374607431768211455\n"
                        + "m: Map()\n"
                        + "m[make(1, big)] = \"one\"\n"
                        + "m[make(2, big)] = \"two\"\n"
                        + "(m.size() == 2) && (m[make(1, big)] == \"one\")"
                        + " && (m[make(2, big)] == \"two\")"));
    }

    @Test
    void recordedLargeHashSurvivesALaterObservableHashChange() throws Exception {
        assertSame(Boolean.TRUE, evaluate(
                "key: make(1, 1180591620717411303424)\n"
                        + "m: Map()\n"
                        + "m[key] = 1\n"
                        + "key.h = 1180591620717411303425\n"
                        + "missing: !m.containsKey(key)\n"
                        + "key.h = 1180591620717411303423 + 1\n"
                        + "missing && m.containsKey(key) && (m.size() == 1)"));
    }

    @Test
    void mutationDuringLargeHashEqualityIsRejected() throws Exception {
        assertThrows(ProtosSignalException.class, () -> evaluate(
                "m: Map()\n"
                        + "hostile: source {\n"
                        + "    hash: () => { this.h }\n"
                        + "    equals: (other) => {\n"
                        + "        m[\"nested\"] = 1\n"
                        + "        true\n"
                        + "    }\n"
                        + "}\n"
                        + "hostileView: hostile.alias(\"equals\", \"==\")\n"
                        + "probe: hostileView {\n"
                        + "    id: 1\n"
                        + "    h: 1180591620717411303424\n"
                        + "}\n"
                        + "m[make(1, 1180591620717411303424)] = 1\n"
                        + "m.containsKey(probe)"));
    }

    @Test
    void stableSnapshotIsAnIndependentInsertionOrderedCaptureOfRecordedHashes() throws Exception {
        ProtosActivation activation = core().newModuleActivation();
        ProtosMapValue map = (ProtosMapValue) ProtosTestExecutionSupport.evaluate(
                KEYS
                        + "k1: make(1, 11)\n"
                        + "k2: make(2, 22)\n"
                        + "k3: make(3, 33)\n"
                        + "m: Map()\n"
                        + "m[k2] = \"b\"\n"
                        + "m[k1] = \"a\"\n"
                        + "m[k3] = \"c\"\n"
                        + "m",
                activation);

        List<ProtosStandardMapProtocol.StableAssociation> snapshot =
                ProtosStandardMapProtocol.stableSnapshot(map);
        List<ProtosMapValue.Entry> entries = map.keyedSnapshot();
        assertEquals(3, snapshot.size());
        assertEquals(List.of(22L, 11L, 33L), hashSources(snapshot));
        for (int index = 0; index < snapshot.size(); index++) {
            ProtosStandardMapProtocol.StableAssociation association = snapshot.get(index);
            assertSame(entries.get(index).key(), association.key());
            assertSame(entries.get(index).recordedHash(), association.recordedHash());
            assertSame(entries.get(index).value(), association.value());
        }
        Object capturedReplacedValue = snapshot.get(1).value();
        Object capturedRemovedValue = snapshot.get(2).value();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> entries.remove(0));

        ProtosTestExecutionSupport.evaluate(
                "k2.h = 99\n"
                        + "m[k1] = \"replaced\"\n"
                        + "m.remove(k3)\n"
                        + "m[make(4, 44)] = \"d\"\n",
                activation);

        assertEquals(3, snapshot.size());
        assertEquals(List.of(22L, 11L, 33L), hashSources(snapshot));
        assertSame(capturedReplacedValue, snapshot.get(1).value());
        assertSame(capturedRemovedValue, snapshot.get(2).value());
        assertEquals(3, entries.size());
    }

    private static List<Long> hashSources(
            List<ProtosStandardMapProtocol.StableAssociation> snapshot) {
        Long[] hashes = new Long[snapshot.size()];
        for (int index = 0; index < hashes.length; index++) {
            ProtosNumericHashKey recorded = snapshot.get(index).recordedHash();
            for (long candidate : new long[] {11L, 22L, 33L, 44L, 99L}) {
                if (recorded.equals(ProtosNumericHashKey.ofLong(candidate))) {
                    hashes[index] = candidate;
                }
            }
        }
        return List.of(hashes);
    }
}
