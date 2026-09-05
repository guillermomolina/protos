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
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosStandardEncodingProtocolTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

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

    @Test
    void utf8OneShotIsStrictConsumesOnlyInitialMatchingBomAndReturnsFreshBytes()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosEncodingValue utf8 = descriptor(prelude, "UTF8");

        ProtosBytesValue first =
                (ProtosBytesValue)
                        ProtosInvocation.invokeMessage(
                                utf8,
                                "encode",
                                List.of(new ProtosStringValue("A😀")),
                                activation);
        ProtosBytesValue second =
                (ProtosBytesValue)
                        ProtosInvocation.invokeMessage(
                                utf8,
                                "encode",
                                List.of(new ProtosStringValue("A😀")),
                                activation);

        assertNotSame(first, second);
        assertEquals(List.of(0x41, 0xf0, 0x9f, 0x98, 0x80), octets(first));
        assertEquals(octets(first), octets(second));

        assertEquals(
                "A",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        utf8,
                                        "decode",
                                        List.of(bytes(0xef, 0xbb, 0xbf, 0x41)),
                                        activation))
                        .value());
        assertEquals(
                "A\uFEFFB",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        utf8,
                                        "decode",
                                        List.of(bytes(0x41, 0xef, 0xbb, 0xbf, 0x42)),
                                        activation))
                        .value());

        ProtosSignalException malformed =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        utf8,
                                        "decode",
                                        List.of(bytes(0xc0, 0xaf)),
                                        activation));
        assertSame(
                prelude.bindings().readLocalSlot("EncodingError").orElseThrow(),
                malformed.error().parent().orElseThrow());
    }

    @Test
    void utf16DescriptorsUseSelectedEndiannessAndConsumeMatchingInitialBom()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosEncodingValue le = descriptor(prelude, "UTF16LE");
        ProtosEncodingValue be = descriptor(prelude, "UTF16BE");

        assertEquals(
                List.of(0x41, 0x00, 0x3d, 0xd8, 0x00, 0xde),
                octets(
                        (ProtosBytesValue)
                                ProtosInvocation.invokeMessage(
                                        le,
                                        "encode",
                                        List.of(new ProtosStringValue("A😀")),
                                        activation)));
        assertEquals(
                List.of(0x00, 0x41, 0xd8, 0x3d, 0xde, 0x00),
                octets(
                        (ProtosBytesValue)
                                ProtosInvocation.invokeMessage(
                                        be,
                                        "encode",
                                        List.of(new ProtosStringValue("A😀")),
                                        activation)));

        assertEquals(
                "A",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        le,
                                        "decode",
                                        List.of(bytes(0xff, 0xfe, 0x41, 0x00)),
                                        activation))
                        .value());
        assertEquals(
                "A",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        be,
                                        "decode",
                                        List.of(bytes(0xfe, 0xff, 0x00, 0x41)),
                                        activation))
                        .value());

        ProtosSignalException incomplete =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        le,
                                        "decode",
                                        List.of(bytes(0x41)),
                                        activation));
        assertSame(
                prelude.bindings().readLocalSlot("EncodingError").orElseThrow(),
                incomplete.error().parent().orElseThrow());
    }

    @Test
    void latin1IsIso88591AndRejectsUnrepresentableText() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosEncodingValue latin1 = descriptor(prelude, "Latin1");

        ProtosBytesValue encoded =
                (ProtosBytesValue)
                        ProtosInvocation.invokeMessage(
                                latin1,
                                "encode",
                                List.of(new ProtosStringValue("\u0000\u0080\u00ff")),
                                activation);
        assertEquals(List.of(0x00, 0x80, 0xff), octets(encoded));
        assertEquals(
                "\u0000\u0080\u00ff",
                ((ProtosStringValue)
                                ProtosInvocation.invokeMessage(
                                        latin1,
                                        "decode",
                                        List.of(bytes(0x00, 0x80, 0xff)),
                                        activation))
                        .value());

        ProtosSignalException unrepresentable =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                ProtosInvocation.invokeMessage(
                                        latin1,
                                        "encode",
                                        List.of(new ProtosStringValue("€")),
                                        activation));
        assertSame(
                prelude.bindings().readLocalSlot("EncodingError").orElseThrow(),
                unrepresentable.error().parent().orElseThrow());
    }

    @Test
    void exactSemanticDomainsAndReceiverFamilyAreSynchronous() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosEncodingValue utf8 = descriptor(prelude, "UTF8");

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                utf8,
                                "encode",
                                List.of(new ProtosIntegerValue(BigInteger.ONE)),
                                activation));
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                utf8,
                                "decode",
                                List.of(new ProtosStringValue("not Bytes")),
                                activation));

        ProtosObjectValue masquerade = new ProtosObjectValue(utf8);
        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                masquerade,
                                "encode",
                                List.of(new ProtosStringValue("x")),
                                activation));
    }

    @Test
    void emptyEncodeProducesFreshOpenEmptyBytesWithoutBomByDefault() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosActivation activation = prelude.newModuleActivation();
        ProtosEncodingValue utf8 = descriptor(prelude, "UTF8");

        ProtosBytesValue first =
                (ProtosBytesValue)
                        ProtosInvocation.invokeMessage(
                                utf8,
                                "encode",
                                List.of(new ProtosStringValue("")),
                                activation);
        ProtosBytesValue second =
                (ProtosBytesValue)
                        ProtosInvocation.invokeMessage(
                                utf8,
                                "encode",
                                List.of(new ProtosStringValue("")),
                                activation);

        assertNotSame(first, second);
        assertEquals(BigInteger.ZERO, first.indexedSize());
        assertEquals(BigInteger.ZERO, second.indexedSize());
        assertTrue(first.isOpen());
        assertTrue(second.isOpen());
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

    private static List<Integer> octets(ProtosBytesValue bytes) {
        return bytes.indexedSnapshot().stream()
                .map(value -> ((ProtosIntegerValue) value).value().intValueExact())
                .toList();
    }
}
