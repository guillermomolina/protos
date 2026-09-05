/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosFilesystemTransferTest {
    @Test
    void fileAndFilesystemAuthorityAreNotActorTransferable() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        for (Object capability : List.of(new ProtosFileValue(), new ProtosFilesystemValue())) {
            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () -> ProtosActorValueTransfer.snapshotValue(capability, activation));
            assertSame(
                    prelude.standardErrorPrototype("NonTransferableValue"),
                    signal.error().parent().orElseThrow());
        }
    }

    @Test
    void ordinaryDescendantCannotSmuggleLiveAuthorityAcrossActorBoundary() throws Exception {
        ProtosPrelude prelude =
                new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
        ProtosActivation activation = prelude.newModuleActivation();

        for (ProtosObjectValue capability :
                List.of(new ProtosFileValue(), new ProtosFilesystemValue())) {
            ProtosObjectValue descendant = new ProtosObjectValue(capability);
            descendant.createLocalSlot("ordinaryData", new ProtosIntegerValue(java.math.BigInteger.ONE));

            ProtosSignalException signal =
                    assertThrows(
                            ProtosSignalException.class,
                            () -> ProtosActorValueTransfer.snapshotValue(descendant, activation));
            assertSame(
                    prelude.standardErrorPrototype("NonTransferableValue"),
                    signal.error().parent().orElseThrow());
        }
    }
}
