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

package com.guillermomolina.protos.runtime;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Immutable semantic Encoding descriptor; carries no I/O authority or mutable codec state. */
public final class ProtosEncodingValue implements ProtosRepresentedValue {
    public enum PortableKind { UTF8, UTF16LE, UTF16BE, LATIN1 }

    public interface HostCodec {
        byte[] encode(String text) throws ConversionFailure;
        String decode(byte[] bytes) throws ConversionFailure;
    }

    public static final class ConversionFailure extends Exception {
        public ConversionFailure(String message) { super(message); }
        public ConversionFailure(String message, Throwable cause) { super(message, cause); }
    }

    private final ProtosObjectValue prototype;
    private final PortableKind portableKind;
    private final HostCodec codec;

    private ProtosEncodingValue(
            ProtosObjectValue prototype, PortableKind portableKind, HostCodec codec) {
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.portableKind = portableKind;
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public static ProtosEncodingValue portableForRuntime(
            ProtosObjectValue encodingPrototype, PortableKind kind) {
        Objects.requireNonNull(kind, "kind");
        return new ProtosEncodingValue(encodingPrototype, kind, portableCodec(kind));
    }

    /** Explicit trusted boundary for additional host-provided Encoding descriptors. */
    public static ProtosEncodingValue hostProvidedForRuntime(
            ProtosObjectValue encodingPrototype, HostCodec codec) {
        return new ProtosEncodingValue(
                Objects.requireNonNull(encodingPrototype, "encodingPrototype"),
                null,
                Objects.requireNonNull(codec, "codec"));
    }

    public boolean isPortableForRuntime() { return portableKind != null; }

    public PortableKind portableKindForRuntime() {
        if (portableKind == null) {
            throw new IllegalStateException("host-provided Encoding has no portable kind");
        }
        return portableKind;
    }

    public byte[] encodeForRuntime(String text) throws ConversionFailure {
        byte[] encoded = codec.encode(Objects.requireNonNull(text, "text"));
        return Objects.requireNonNull(encoded, "codec returned null bytes").clone();
    }

    public String decodeForRuntime(byte[] bytes) throws ConversionFailure {
        return Objects.requireNonNull(
                codec.decode(Objects.requireNonNull(bytes, "bytes").clone()),
                "codec returned null text");
    }

    /** Immutable authority-free descriptors may be shared by Actor isolation transfer. */
    public ProtosEncodingValue transferForActorRuntime() { return this; }

    /** Immutable authority-free descriptors may be shared by isolated P transfer. */
    public ProtosEncodingValue transferForParallelRuntime() { return this; }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) { return prototype; }

    private static HostCodec portableCodec(PortableKind kind) {
        return switch (kind) {
            case UTF8 -> charsetCodec(
                    StandardCharsets.UTF_8,
                    new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
            case UTF16LE -> charsetCodec(
                    StandardCharsets.UTF_16LE,
                    new byte[] {(byte) 0xff, (byte) 0xfe});
            case UTF16BE -> charsetCodec(
                    StandardCharsets.UTF_16BE,
                    new byte[] {(byte) 0xfe, (byte) 0xff});
            case LATIN1 -> charsetCodec(StandardCharsets.ISO_8859_1, new byte[0]);
        };
    }

    private static HostCodec charsetCodec(Charset charset, byte[] matchingBom) {
        return new HostCodec() {
            @Override
            public byte[] encode(String text) throws ConversionFailure {
                CharsetEncoder encoder = charset.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                try {
                    ByteBuffer encoded = encoder.encode(CharBuffer.wrap(text));
                    byte[] result = new byte[encoded.remaining()];
                    encoded.get(result);
                    return result;
                } catch (CharacterCodingException failure) {
                    throw new ConversionFailure(
                            "text is not representable in selected Encoding", failure);
                }
            }

            @Override
            public String decode(byte[] bytes) throws ConversionFailure {
                byte[] payload = startsWith(bytes, matchingBom)
                        ? Arrays.copyOfRange(bytes, matchingBom.length, bytes.length)
                        : bytes.clone();
                CharsetDecoder decoder = charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                try {
                    return decoder.decode(ByteBuffer.wrap(payload)).toString();
                } catch (CharacterCodingException failure) {
                    throw new ConversionFailure(
                            "bytes are malformed for selected Encoding", failure);
                }
            }
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (prefix.length == 0 || bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }
}
