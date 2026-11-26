/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.numeric.ProtosNumericHashKey;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosMapConstructionFPrimeBytecodeFoundationTest {

    @Test
    void bytecodeDirectConstructionUsesFPrimeEligibility()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue inherited =
                new ProtosObjectValue(prelude.mapPrototype());

        // The direct construction makes the actual factory the parent and
        // prepares no guest call.
        ProtosMapValue produced =
                ProtosStandardMapProtocol.constructStandardForMapConstruction(
                        inherited, activation);

        assertSame(inherited, produced.parent().orElseThrow());
        assertSame(
                inherited,
                ProtosStandardMapProtocol
                        .standardMapConstructionOrNull(inherited, prelude)
                        .parent()
                        .orElseThrow());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosStandardMapProtocol.constructStandardForMapConstruction(
                                Boolean.TRUE, activation));

        ProtosObjectValue copied =
                new ProtosObjectValue(prelude.mapPrototype());

        copied.createLocalSlot(
                "construct",
                prelude.mapPrototype()
                        .readLocalSlot("construct")
                        .orElseThrow());

        assertNull(
                ProtosStandardMapProtocol.standardMapConstructionOrNull(
                        copied, prelude));
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosStandardMapProtocol.constructStandardForMapConstruction(
                                copied, activation));

        /*
         * D239-C1: a customized call selection on the factory receiver is
         * rejected before construct is consulted.
         */
        ProtosObjectValue customCall =
                new ProtosObjectValue(prelude.mapPrototype());
        customCall.createLocalSlot("call", Boolean.TRUE);

        assertNull(
                ProtosStandardMapProtocol.standardMapConstructionOrNull(
                        customCall, prelude));
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosStandardMapProtocol.constructStandardForMapConstruction(
                                customCall, activation));
    }

    @Test
    void initialDefinitionStateMachineRejectsDuplicateWithoutReplacement()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosMapValue map =
                ProtosStandardMapProtocol.constructForMapConstruction(
                        prelude.mapPrototype(),
                        activation);

        ProtosStringValue representative =
                new ProtosStringValue("key");

        ProtosStandardMapProtocol.defineInitialAssociationForMapConstruction(
                map,
                representative,
                Boolean.TRUE,
                activation);

        ProtosStringValue query =
                new ProtosStringValue("key");

        ProtosStructuredMapUpdateCalls.PreparedMapInitialDefinition prepared =
                ProtosBytecodeRootNode.PrepareMapInitialDefinition
                        .perform(
                                activation,
                                map,
                                query,
                                Boolean.FALSE);

        ProtosNumericHashKey hash =
                ProtosStandardMapProtocol.queryHash(
                        map,
                        query,
                        activation);

        prepared.acceptHash(ProtosInvocation.invokeMessage(
                query, "hash", java.util.List.of(), activation));

        assertTrue(prepared.needsEquality());

        prepared.acceptEquality(Boolean.TRUE);

        assertThrows(
                ProtosSignalException.class,
                prepared::finish);

        assertEquals(1, map.keyedSize());
        assertSame(
                representative,
                map.keyedSnapshot().get(0).key());
        assertSame(
                Boolean.TRUE,
                map.keyedSnapshot().get(0).value());
    }

    @Test
    void initialDefinitionStateMachineAppendsUniqueAssociation()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosMapValue map =
                ProtosStandardMapProtocol.constructForMapConstruction(
                        prelude.mapPrototype(),
                        activation);

        ProtosStringValue key =
                new ProtosStringValue("new");

        ProtosStructuredMapUpdateCalls.PreparedMapInitialDefinition prepared =
                ProtosBytecodeRootNode.PrepareMapInitialDefinition
                        .perform(
                                activation,
                                map,
                                key,
                                Boolean.TRUE);

        ProtosNumericHashKey hash =
                ProtosStandardMapProtocol.queryHash(
                        map,
                        key,
                        activation);

        prepared.acceptHash(ProtosInvocation.invokeMessage(
                key, "hash", java.util.List.of(), activation));
        prepared.finish();

        assertEquals(1, map.keyedSize());
        assertSame(key, map.keyedSnapshot().get(0).key());
        assertEquals(
                hash,
                map.keyedSnapshot().get(0).recordedHash());
        assertSame(
                Boolean.TRUE,
                map.keyedSnapshot().get(0).value());
    }

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}
