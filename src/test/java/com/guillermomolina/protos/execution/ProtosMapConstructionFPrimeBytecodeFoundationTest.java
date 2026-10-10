/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosNumericHashKey;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosMapConstructionFPrimeBytecodeFoundationTest {

    @Test
    void bytecodeConstructionFactoryPreparationUsesFPrimeEligibility()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue inherited =
                new ProtosObjectValue(prelude.mapPrototype());

        ProtosBytecodeRootNode.PreparedClosureCall prepared =
                ProtosBytecodeRootNode
                        .PrepareMapConstructionFactoryCall
                        .perform(activation, inherited);

        assertSame(inherited, prepared.activation().receiver());

        ProtosMapValue produced = new ProtosMapValue(inherited);

        assertSame(
                produced,
                ProtosBytecodeRootNode
                        .FinishMapConstructionFactoryCall
                        .perform(activation, produced));

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosBytecodeRootNode
                                .FinishMapConstructionFactoryCall
                                .perform(
                                        activation,
                                        ProtosBooleanValue.TRUE));

        ProtosObjectValue copied =
                new ProtosObjectValue(prelude.mapPrototype());

        copied.createLocalSlot(
                "call",
                prelude.mapPrototype()
                        .readLocalSlot("call")
                        .orElseThrow());

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosBytecodeRootNode
                                .PrepareMapConstructionFactoryCall
                                .perform(activation, copied));
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
                ProtosBooleanValue.TRUE,
                activation);

        ProtosStringValue query =
                new ProtosStringValue("key");

        ProtosBytecodeRootNode.PreparedMapInitialDefinition prepared =
                ProtosBytecodeRootNode
                        .PrepareMapInitialDefinition
                        .perform(
                                activation,
                                map,
                                query,
                                ProtosBooleanValue.FALSE);

        ProtosNumericHashKey hash =
                ProtosStandardMapProtocol.queryHash(
                        map,
                        query,
                        activation);

        prepared.acceptHash(ProtosInvocation.invokeMessage(
                query, "hash", java.util.List.of(), activation));

        assertTrue(prepared.needsEquality());

        prepared.acceptEquality(ProtosBooleanValue.TRUE);

        assertThrows(
                ProtosSignalException.class,
                prepared::finish);

        assertEquals(1, map.keyedSize());
        assertSame(
                representative,
                map.keyedSnapshot().get(0).key());
        assertSame(
                ProtosBooleanValue.TRUE,
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

        ProtosBytecodeRootNode.PreparedMapInitialDefinition prepared =
                ProtosBytecodeRootNode
                        .PrepareMapInitialDefinition
                        .perform(
                                activation,
                                map,
                                key,
                                ProtosBooleanValue.TRUE);

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
                ProtosBooleanValue.TRUE,
                map.keyedSnapshot().get(0).value());
    }

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}
