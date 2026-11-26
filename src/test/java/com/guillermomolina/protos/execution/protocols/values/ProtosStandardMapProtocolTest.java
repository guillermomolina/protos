/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

        ProtosStandardMapProtocol.StableAssociation[] snapshot =
                ProtosStandardMapProtocol.stableSnapshot(map);
        List<ProtosMapValue.Entry> entries = map.keyedSnapshot();
        assertEquals(3, snapshot.length);
        assertEquals(List.of(22L, 11L, 33L), hashSources(snapshot));
        for (int index = 0; index < snapshot.length; index++) {
            ProtosStandardMapProtocol.StableAssociation association = snapshot[index];
            assertSame(entries.get(index).key(), association.key());
            assertSame(entries.get(index).recordedHash(), association.recordedHash());
            assertSame(entries.get(index).value(), association.value());
        }
        Object capturedKey = snapshot[0].key();
        ProtosNumericHashKey capturedHash = snapshot[0].recordedHash();
        Object capturedReplacedValue = snapshot[1].value();
        Object capturedRemovedValue = snapshot[2].value();
        assertThrows(UnsupportedOperationException.class, () -> entries.remove(0));

        ProtosMapValue.Entry[] runtimeEntries = map.keyedEntrySnapshotForRuntime();
        assertNotSame(runtimeEntries, map.keyedEntrySnapshotForRuntime());
        assertNotSame(snapshot, ProtosStandardMapProtocol.stableSnapshot(map));
        runtimeEntries[0] = null;
        ProtosStandardMapProtocol.StableAssociation overwritten = snapshot[0];
        snapshot[0] = null;
        assertEquals(3, map.keyedSize());
        assertSame(entries.get(0), map.keyedEntrySnapshotForRuntime()[0]);
        assertSame(capturedKey, ProtosStandardMapProtocol.stableSnapshot(map)[0].key());
        snapshot[0] = overwritten;

        ProtosTestExecutionSupport.evaluate(
                "k2.h = 99\n"
                        + "m[k1] = \"replaced\"\n"
                        + "m.remove(k3)\n"
                        + "m[make(4, 44)] = \"d\"\n",
                activation);

        assertEquals(3, snapshot.length);
        assertEquals(List.of(22L, 11L, 33L), hashSources(snapshot));
        assertSame(capturedKey, snapshot[0].key());
        assertSame(capturedHash, snapshot[0].recordedHash());
        assertSame(capturedReplacedValue, snapshot[1].value());
        assertSame(capturedRemovedValue, snapshot[2].value());
        assertEquals(3, entries.size());

        ProtosStandardMapProtocol.StableAssociation[] later =
                ProtosStandardMapProtocol.stableSnapshot(map);
        assertEquals(3, later.length);
        assertEquals(List.of(22L, 11L, 44L), hashSources(later));
        assertSame(capturedKey, later[0].key());
        assertNotSame(capturedReplacedValue, later[1].value());
        assertSame(map.keyedSnapshot().get(1).value(), later[1].value());
        assertSame(map.keyedSnapshot().get(2).value(), later[2].value());
    }

    @Test
    void standardKeysServeEveryLookupAndUpdateOperation() throws Exception {
        assertSame(Boolean.TRUE, evaluate(
                "o: {}\n"
                        + "m: Map()\n"
                        + "m[1] = \"int\"\n"
                        + "m[2.5] = \"float\"\n"
                        + "m[\"s\"] = \"string\"\n"
                        + "m[o] = \"object\"\n"
                        + "m[1180591620717411303424] = \"big\"\n"
                        + "m[1.0] = \"replaced\"\n"
                        + "found: (m.at(1) == \"replaced\") && (m[2.5] == \"float\") &&\n"
                        + "    (m.at(\"s\") == \"string\") && (m[o] == \"object\") &&\n"
                        + "    (m[1180591620717411303424.0] == \"big\") && (m.size() == 5)\n"
                        + "contained: m.containsKey(1/1) && !m.containsKey(2) && !m.containsKey({})\n"
                        + "absent: (m.atIfAbsent(3, () => \"fallback\") == \"fallback\") &&\n"
                        + "    (m.atIfAbsent(\"s\", () => \"fallback\") == \"string\")\n"
                        + "removed: (m.remove(2.5) == \"float\") && (m.remove(o) == \"object\") &&\n"
                        + "    !m.containsKey(2.5) && (m.size() == 3)\n"
                        + "found && contained && absent && removed"));
    }

    @Test
    void customKeysKeepOrdinaryHashAndEqualityAcrossOperations() throws Exception {
        assertSame(Boolean.TRUE, evaluate(
                "counts: {\n"
                        + "    hash: 0\n"
                        + "    equality: 0\n"
                        + "}\n"
                        + "counted: {\n"
                        + "    hash: () => { counts.hash = counts.hash + 1\n this.h }\n"
                        + "    equals: (other) => {\n"
                        + "        counts.equality = counts.equality + 1\n"
                        + "        this.id == other.id\n"
                        + "    }\n"
                        + "}.alias(\"equals\", \"==\")\n"
                        + "key: (i, hv) => counted {\n"
                        + "    id: i\n"
                        + "    h: hv\n"
                        + "}\n"
                        + "m: Map()\n"
                        + "m[key(1, 5)] = \"a\"\n"
                        + "m[key(2, 5)] = \"b\"\n"
                        + "afterPut: (counts.hash == 2) && (counts.equality == 1)\n"
                        + "read: m.at(key(2, 5)) == \"b\"\n"
                        + "afterRead: (counts.hash == 3) && (counts.equality == 3)\n"
                        + "missing: !m.containsKey(key(3, 6))\n"
                        + "afterMissing: (counts.hash == 4) && (counts.equality == 3)\n"
                        + "fallback: m.atIfAbsent(key(3, 5), () => \"none\") == \"none\"\n"
                        + "afterFallback: (counts.hash == 5) && (counts.equality == 5)\n"
                        + "removed: m.remove(key(1, 5)) == \"a\"\n"
                        + "afterRemove: (counts.hash == 6) && (counts.equality == 6)\n"
                        + "afterPut && read && afterRead && missing && afterMissing &&\n"
                        + "    fallback && afterFallback && removed && afterRemove && (m.size() == 1)"));
    }

    @Test
    void warmedLookupSiteInvokesOverridingKeyBehavior() throws Exception {
        assertSame(Boolean.TRUE, evaluate(
                "calls: 0\n"
                        + "m: Map()\n"
                        + "m[1] = \"one\"\n"
                        + "m[\"s\"] = \"string\"\n"
                        + "probe: (k) => m.containsKey(k)\n"
                        + "[1, 2, 3, 4, 5, 6].each((i) => { probe(1)\n probe(\"s\")\n probe(2.5) })\n"
                        + "Liar: { hash: () => { calls = calls + 1\n 1 }\n"
                        + "    equals: (other) => { calls = calls + 1\n true } }.alias(\"equals\", \"==\")\n"
                        + "(probe(Liar {}) == true) && (calls == 2) && probe(1) && !probe(2)"));
    }

    @Test
    void storedHashesAreNotRecomputedByLookups() throws Exception {
        assertSame(Boolean.TRUE, evaluate(
                "counts: { hash: 0 }\n"
                        + "counted: {\n"
                        + "    hash: () => { counts.hash = counts.hash + 1\n 9 }\n"
                        + "}\n"
                        + "stored: counted {}\n"
                        + "m: Map()\n"
                        + "m[stored] = 1\n"
                        + "m[\"other\"] = 2\n"
                        + "lookups: (m[\"other\"] == 2) && !m.containsKey(9) && (m.at(stored) == 1)\n"
                        + "lookups && (counts.hash == 2)"));
    }

    private static List<Long> hashSources(
            ProtosStandardMapProtocol.StableAssociation[] snapshot) {
        Long[] hashes = new Long[snapshot.length];
        for (int index = 0; index < hashes.length; index++) {
            ProtosNumericHashKey recorded = snapshot[index].recordedHash();
            for (long candidate : new long[] {11L, 22L, 33L, 44L, 99L}) {
                if (recorded.equals(ProtosNumericHashKey.ofLong(candidate))) {
                    hashes[index] = candidate;
                }
            }
        }
        return List.of(hashes);
    }
}
