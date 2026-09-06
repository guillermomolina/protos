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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorValueTransfer;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosCollectionsSetAlgebraModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void setDataTransfersAcrossActorsWhileModuleBehaviorRemainsActorLocal()
            throws Exception {
        for (String module : new String[] {"Set", "IdentitySet"}) {
            ProtosStandardLibraryModuleResolver resolver =
                    new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
            ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
            ProtosActivation activation = prelude.newModuleActivation();
            ProtosSourceCompiler compiler = new ProtosSourceCompiler();

            Object moduleValue =
                    compiler.compile("import(\"std:collections/" + module + "\")")
                            .call(activation);
            assertThrows(
                    ProtosSignalException.class,
                    () -> ProtosActorValueTransfer.snapshotValue(moduleValue, activation));

            Object data =
                    compiler.compile(
                                    """
                                    Collection: import("std:collections/%s")
                                    Collection.union(Collection(1, 2), Collection(2, 3))
                                    """
                                            .formatted(module))
                            .call(activation);
            ProtosObjectValue copied =
                    assertInstanceOf(
                            ProtosObjectValue.class,
                            ProtosActorValueTransfer.snapshotValue(data, activation));

            assertNotSame(data, copied);
            assertFalse(copied.isClosed());
            assertFalse(copied.isFrozen());

            if (module.equals("Set")) {
                ProtosMapValue copiedMap =
                        assertInstanceOf(ProtosMapValue.class, copied);
                assertSame(prelude.mapPrototype(), copiedMap.parent().orElseThrow());
                assertEquals(3, copiedMap.keyedSize());
                copiedMap.keyedSnapshot()
                        .forEach(entry -> assertSame(ProtosBooleanValue.TRUE, entry.value()));
            } else {
                ProtosIdentityMapValue copiedMap =
                        assertInstanceOf(ProtosIdentityMapValue.class, copied);
                assertSame(prelude.identityMapPrototype(), copiedMap.parent().orElseThrow());
                assertEquals(3, copiedMap.keyedSize());
                copiedMap.keyedSnapshot()
                        .forEach(entry -> assertSame(ProtosBooleanValue.TRUE, entry.value()));
            }
        }
    }
}
