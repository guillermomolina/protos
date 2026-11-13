/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
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
        derivedMap.createLocalSlot("atPut", Boolean.FALSE);

        ProtosMapValue result =
                ProtosStandardMapProtocol.constructForMapConstruction(
                        derivedMap, activation);

        assertSame(derivedMap, result.parent().orElseThrow());

        ProtosStandardMapProtocol.defineInitialAssociationForMapConstruction(
                result,
                new ProtosStringValue("answer"),
                Boolean.TRUE,
                activation);

        assertSame(
                Boolean.TRUE,
                ProtosInvocation.invokeMessage(
                        result,
                        "at",
                        List.of(new ProtosStringValue("answer")),
                        activation));
    }

    @Test
    void copiedCanonicalConstructRemainsOrdinarilyInvokableButIsNotConstructionAuthority()
            throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue derivedMap =
                new ProtosObjectValue(prelude.mapPrototype());

        derivedMap.createLocalSlot(
                "construct",
                prelude.mapPrototype()
                        .readLocalSlot("construct")
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
    void canonicalMapNoLongerOwnsLocalCallAndInheritsStandardObjectCall()
            throws Exception {
        ProtosPrelude prelude = prelude();

        assertFalse(prelude.mapPrototype().hasLocalSlot("call"));
        assertTrue(prelude.mapPrototype().hasLocalSlot("construct"));
    }

    @Test
    void customizedCallSelectionIsRejectedBeforeConstruct() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue shadowedCall =
                new ProtosObjectValue(prelude.mapPrototype());
        shadowedCall.createLocalSlot("call", Boolean.TRUE);
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosStandardMapProtocol.constructForMapConstruction(
                                shadowedCall,
                                activation));

        ProtosObjectValue copiedObjectCall =
                new ProtosObjectValue(prelude.mapPrototype());
        copiedObjectCall.createLocalSlot(
                "call",
                ProtosObjectValue.rootObject()
                        .readLocalSlot("call")
                        .orElseThrow());
        assertInstanceOf(
                ProtosMapValue.class,
                ProtosInvocation.invoke(
                        copiedObjectCall,
                        List.of(),
                        activation));
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosStandardMapProtocol.constructForMapConstruction(
                                copiedObjectCall,
                                activation));
    }

    @Test
    void shadowedConstructSelectionIsRejected() throws Exception {
        ProtosPrelude prelude = prelude();
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosObjectValue shadowedConstruct =
                new ProtosObjectValue(prelude.mapPrototype());
        shadowedConstruct.createLocalSlot("construct", Boolean.TRUE);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosStandardMapProtocol.constructForMapConstruction(
                                shadowedConstruct,
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
                Boolean.TRUE,
                activation);

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosStandardMapProtocol.defineInitialAssociationForMapConstruction(
                                result,
                                new ProtosStringValue("key"),
                                Boolean.FALSE,
                                activation));

        assertEquals(1, result.keyedSize());
        assertSame(
                representative,
                result.keyedSnapshot().get(0).key());
        assertSame(
                Boolean.TRUE,
                result.keyedSnapshot().get(0).value());
    }

    private static ProtosPrelude prelude() throws Exception {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}
