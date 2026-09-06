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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosStandardEncodingProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    // Deliberately Java-side: physical bootstrap representation, exact local-slot
    // inventory, internal portable descriptor kind, and represented parent are
    // implementation-owned checks rather than source-level conversion behavior.
    @Test
    void bootstrapExposesFrozenEncodingFactoryAndExactlyFourPortableDescriptors()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosObjectValue encoding = prelude.encodingPrototype();

        assertSame(ProtosObjectValue.rootObject(), encoding.parent().orElseThrow());
        assertTrue(encoding.isFrozen());
        assertEquals(
                Set.of("UTF8", "UTF16LE", "UTF16BE", "Latin1", "encode", "decode"),
                encoding.localSlotsSnapshot().keySet());

        assertPortable(encoding, "UTF8", ProtosEncodingValue.PortableKind.UTF8);
        assertPortable(encoding, "UTF16LE", ProtosEncodingValue.PortableKind.UTF16LE);
        assertPortable(encoding, "UTF16BE", ProtosEncodingValue.PortableKind.UTF16BE);
        assertPortable(encoding, "Latin1", ProtosEncodingValue.PortableKind.LATIN1);
        assertFalse(encoding.hasLocalSlot("call"));
    }

    // Deliberately Java-side: the generic executable Protos manifest can assert
    // that conversion signals, but cannot inspect the exact signaled Error object
    // after terminal control transfer. Preserve the normative EncodingError category.
    @Test
    void strictConversionFailuresUseExactEncodingErrorCategory() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();

        assertEncodingError(
                prelude,
                () ->
                        ProtosInvocation.invokeMessage(
                                descriptor(prelude, "UTF8"),
                                "decode",
                                List.of(bytes(0xc0, 0xaf)),
                                activation));

        assertEncodingError(
                prelude,
                () ->
                        ProtosInvocation.invokeMessage(
                                descriptor(prelude, "UTF16LE"),
                                "decode",
                                List.of(bytes(0x41)),
                                activation));

        assertEncodingError(
                prelude,
                () ->
                        ProtosInvocation.invokeMessage(
                                descriptor(prelude, "Latin1"),
                                "encode",
                                List.of(new ProtosStringValue("€")),
                                activation));
    }

    private static void assertEncodingError(ProtosPrelude prelude, Runnable action) {
        ProtosSignalException signal =
                assertThrows(ProtosSignalException.class, action::run);
        assertSame(
                prelude.bindings().readLocalSlot("EncodingError").orElseThrow(),
                signal.error().parent().orElseThrow());
    }

    private static void assertPortable(
            ProtosObjectValue encoding,
            String slot,
            ProtosEncodingValue.PortableKind kind) {
        ProtosEncodingValue descriptor =
                assertInstanceOf(
                        ProtosEncodingValue.class,
                        encoding.readLocalSlot(slot).orElseThrow());
        assertSame(encoding, descriptor.representedDelegationParent(null));
        assertTrue(descriptor.isPortableForRuntime());
        assertEquals(kind, descriptor.portableKindForRuntime());
    }

    private static ProtosEncodingValue descriptor(ProtosPrelude prelude, String name) {
        return assertInstanceOf(
                ProtosEncodingValue.class,
                prelude.encodingPrototype().readLocalSlot(name).orElseThrow());
    }

    private static ProtosBytesValue bytes(int... values) {
        ProtosObjectValue parent =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(parent);
        ProtosBytesValue bytes = new ProtosBytesValue(parent);
        for (int value : values) {
            bytes.indexedAdd(new ProtosIntegerValue(BigInteger.valueOf(value)));
        }
        return bytes;
    }
}
