/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosMapConstructionFPrimeProtocolTest {

    @Test
    void inheritedCanonicalFactoryIsEligibleAndInitialDefinitionBypassesAtPut()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue derivedMap =
                new ProtosObjectValue(prelude.mapPrototype());
        derivedMap.createLocalSlot("atPut", ProtosBooleanValue.FALSE);

        ProtosMapValue result =
                ProtosStandardMapProtocol.constructForMapConstruction(
                        derivedMap, activation);

        assertSame(derivedMap, result.parent().orElseThrow());

        ProtosStandardMapProtocol.defineInitialAssociationForMapConstruction(
                result,
                new ProtosStringValue("answer"),
                ProtosBooleanValue.TRUE,
                activation);

        assertSame(
                ProtosBooleanValue.TRUE,
                ProtosInvocation.invokeMessage(
                        result,
                        "at",
                        List.of(new ProtosStringValue("answer")),
                        activation));
    }

    @Test
    void copiedCanonicalCallRemainsOrdinarilyInvokableButIsNotConstructionAuthority()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue derivedMap =
                new ProtosObjectValue(prelude.mapPrototype());

        derivedMap.createLocalSlot(
                "call",
                prelude.mapPrototype()
                        .readLocalSlot("call")
                        .orElseThrow());

        assertInstanceOf(
                ProtosMapValue.class,
                ProtosInvocation.invoke(
                        derivedMap,
                        List.of(),
                        activation));

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosStandardMapProtocol.constructForMapConstruction(
                                derivedMap,
                                activation));
    }

    @Test
    void duplicateInitialDefinitionErrorsWithoutReplacingStoredAssociation()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosMapValue result =
                ProtosStandardMapProtocol.constructForMapConstruction(
                        prelude.mapPrototype(),
                        activation);

        ProtosStringValue representative =
                new ProtosStringValue("key");

        ProtosStandardMapProtocol.defineInitialAssociationForMapConstruction(
                result,
                representative,
                ProtosBooleanValue.TRUE,
                activation);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosStandardMapProtocol.defineInitialAssociationForMapConstruction(
                                result,
                                new ProtosStringValue("key"),
                                ProtosBooleanValue.FALSE,
                                activation));

        assertEquals(1, result.keyedSize());
        assertSame(
                representative,
                result.keyedSnapshot().get(0).key());
        assertSame(
                ProtosBooleanValue.TRUE,
                result.keyedSnapshot().get(0).value());
    }

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}
