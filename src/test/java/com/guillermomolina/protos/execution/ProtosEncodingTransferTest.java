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

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosEncodingTransferTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void EncodingIsAuthorityFreeImmutableActorAndPTransferValue() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosEncodingValue utf8 =
                assertInstanceOf(
                        ProtosEncodingValue.class,
                        prelude.encodingPrototype().readLocalSlot("UTF8").orElseThrow());

        List<Object> actorCopy =
                ProtosActorValueTransfer.snapshotArguments(List.of(utf8, utf8), activation);
        assertSame(utf8, actorCopy.get(0));
        assertSame(actorCopy.get(0), actorCopy.get(1));

        Class<?> transfer =
                Class.forName(
                        "com.guillermomolina.protos.execution.ProtosParallelRuntime$Transfer");
        Method copy =
                transfer.getDeclaredMethod(
                        "copy", Object.class, ProtosActivation.class, IdentityHashMap.class);
        copy.setAccessible(true);
        IdentityHashMap<Object, Object> memo = new IdentityHashMap<>();

        Object first = copy.invoke(null, utf8, activation, memo);
        Object second = copy.invoke(null, utf8, activation, memo);
        assertSame(utf8, first);
        assertSame(first, second);
    }

    @Test
    void hostProvisionedDescriptorIsSemanticEncodingWithoutRegistryOrAuthority() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosEncodingValue host =
                ProtosEncodingValue.hostProvidedForRuntime(
                        prelude.encodingPrototype(),
                        new ProtosEncodingValue.HostCodec() {
                            @Override
                            public byte[] encode(String text) {
                                return new byte[] {(byte) text.length()};
                            }

                            @Override
                            public String decode(byte[] bytes) {
                                return "host:" + bytes.length;
                            }
                        });

        assertFalse(host.isPortableForRuntime());
        assertSame(prelude.encodingPrototype(), host.representedDelegationParent(prelude));
        assertSame(
                host,
                ProtosActorValueTransfer.snapshotValue(
                        host, prelude.newModuleActivation()));
    }
}
