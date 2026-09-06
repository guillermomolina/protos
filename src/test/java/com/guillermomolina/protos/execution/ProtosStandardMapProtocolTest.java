/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
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

        new ProtosSourceCompiler().compile("m.atPut(1,1)").call(activation);

        map.close();
        new ProtosSourceCompiler().compile("m.atPut(1,2)").call(activation);
        assertThrows(
                ProtosSignalException.class,
                () -> new ProtosSourceCompiler().compile("m.atPut(2,2)").call(activation));
        assertThrows(
                ProtosSignalException.class,
                () -> new ProtosSourceCompiler().compile("m.remove(1)").call(activation));

        map.freeze();
        assertThrows(
                ProtosSignalException.class,
                () -> new ProtosSourceCompiler().compile("m.atPut(1,3)").call(activation));
    }
}
