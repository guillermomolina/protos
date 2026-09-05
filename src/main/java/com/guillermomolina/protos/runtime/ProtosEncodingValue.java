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
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable semantic Encoding descriptor; carries no I/O authority or mutable shared codec state. */
public final class ProtosEncodingValue implements ProtosRepresentedValue {
    public enum PortableKind { UTF8, UTF16LE, UTF16BE, LATIN1 }
    public enum DecodingErrorPolicy { STRICT, REPLACE }
    public enum InitialBomPolicy { CONSUME, PRESERVE }
    public enum DecodeStatus { NEED_INPUT, EOF, MALFORMED }

    /**
     * Transactional per-flow streaming decoder state. preview() MUST NOT mutate this state. Every
     * returned unit carries the independent decoder state that results after consuming that unit.
     */
    public interface StreamingDecoder {
        DecodePreview preview(byte[] bytes, boolean endOfInput) throws ConversionFailure;
    }

    /** One decoded Unicode scalar or one zero-text decoder-control/setup transition. */
    public record DecodedUnit(
            String text,
            int sourceBytes,
            boolean initialSetup,
            StreamingDecoder nextDecoder) {
        public DecodedUnit {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(nextDecoder, "nextDecoder");
            if (sourceBytes < 0) throw new IllegalArgumentException("negative decoded-unit extent");
            if (!text.isEmpty() && text.codePointCount(0, text.length()) != 1) {
                throw new IllegalArgumentException("decoded unit must contain exactly one Unicode scalar");
            }
            if (text.isEmpty() && sourceBytes == 0 && !initialSetup) {
                throw new IllegalArgumentException("zero-byte control unit must be initial setup");
            }
        }
    }

    public record DecodePreview(List<DecodedUnit> units, DecodeStatus status) {
        public DecodePreview {
            units = List.copyOf(Objects.requireNonNull(units, "units"));
            Objects.requireNonNull(status, "status");
        }
    }

    /** Transactional per-flow streaming encoder state. encode()/finish() do not mutate this state. */
    public interface StreamingEncoder {
        EncodePreview encode(String text) throws ConversionFailure;
        EncodePreview finish() throws ConversionFailure;
    }

    public record EncodePreview(byte[] bytes, StreamingEncoder nextEncoder) {
        public EncodePreview {
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
            Objects.requireNonNull(nextEncoder, "nextEncoder");
        }

        @Override
        public byte[] bytes() { return bytes.clone(); }
    }

    /**
     * Explicit trusted host Encoding boundary. The factories must implement the deterministic
     * semantic contract above; mutable encoder/decoder state is per returned flow, never shared by
     * the descriptor.
     */
    public interface HostCodec {
        StreamingDecoder newDecoder(
                DecodingErrorPolicy errorPolicy, InitialBomPolicy bomPolicy);
        StreamingEncoder newEncoder();
    }

    public static final class ConversionFailure extends Exception {
        public ConversionFailure(String message) { super(message); }
        public ConversionFailure(String message, Throwable cause) { super(message, cause); }
    }

    private final ProtosObjectValue prototype;
    private final PortableKind portableKind;
    private final DecodingErrorPolicy decodingErrorPolicy;
    private final InitialBomPolicy initialBomPolicy;
    private final HostCodec codec;

    private ProtosEncodingValue(
            ProtosObjectValue prototype,
            PortableKind portableKind,
            DecodingErrorPolicy decodingErrorPolicy,
            InitialBomPolicy initialBomPolicy,
            HostCodec codec) {
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.portableKind = portableKind;
        this.decodingErrorPolicy = Objects.requireNonNull(decodingErrorPolicy, "decodingErrorPolicy");
        this.initialBomPolicy = Objects.requireNonNull(initialBomPolicy, "initialBomPolicy");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public static ProtosEncodingValue portableForRuntime(
            ProtosObjectValue encodingPrototype, PortableKind kind) {
        return portableConfiguredForRuntime(
                encodingPrototype,
                kind,
                DecodingErrorPolicy.STRICT,
                InitialBomPolicy.CONSUME);
    }

    /** Explicit trusted boundary for a configured portable descriptor. */
    public static ProtosEncodingValue portableConfiguredForRuntime(
            ProtosObjectValue encodingPrototype,
            PortableKind kind,
            DecodingErrorPolicy errorPolicy,
            InitialBomPolicy bomPolicy) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(errorPolicy, "errorPolicy");
        Objects.requireNonNull(bomPolicy, "bomPolicy");
        return new ProtosEncodingValue(
                Objects.requireNonNull(encodingPrototype, "encodingPrototype"),
                kind,
                errorPolicy,
                bomPolicy,
                portableCodec(kind));
    }

    /** Explicit trusted boundary for an additional host-provided Encoding descriptor. */
    public static ProtosEncodingValue hostProvidedForRuntime(
            ProtosObjectValue encodingPrototype, HostCodec codec) {
        return hostProvidedForRuntime(
                encodingPrototype,
                codec,
                DecodingErrorPolicy.STRICT,
                InitialBomPolicy.CONSUME);
    }

    public static ProtosEncodingValue hostProvidedForRuntime(
            ProtosObjectValue encodingPrototype,
            HostCodec codec,
            DecodingErrorPolicy errorPolicy,
            InitialBomPolicy bomPolicy) {
        return new ProtosEncodingValue(
                Objects.requireNonNull(encodingPrototype, "encodingPrototype"),
                null,
                Objects.requireNonNull(errorPolicy, "errorPolicy"),
                Objects.requireNonNull(bomPolicy, "bomPolicy"),
                Objects.requireNonNull(codec, "codec"));
    }

    public boolean isPortableForRuntime() { return portableKind != null; }

    public PortableKind portableKindForRuntime() {
        if (portableKind == null) {
            throw new IllegalStateException("host-provided Encoding has no portable kind");
        }
        return portableKind;
    }

    public DecodingErrorPolicy decodingErrorPolicyForRuntime() { return decodingErrorPolicy; }
    public InitialBomPolicy initialBomPolicyForRuntime() { return initialBomPolicy; }

    public StreamingDecoder newStreamingDecoderForRuntime() {
        return Objects.requireNonNull(
                codec.newDecoder(decodingErrorPolicy, initialBomPolicy),
                "codec returned null streaming decoder");
    }

    public StreamingEncoder newStreamingEncoderForRuntime() {
        return Objects.requireNonNull(codec.newEncoder(), "codec returned null streaming encoder");
    }

    public byte[] encodeForRuntime(String text) throws ConversionFailure {
        Objects.requireNonNull(text, "text");
        StreamingEncoder encoder = newStreamingEncoderForRuntime();
        EncodePreview body = encoder.encode(text);
        EncodePreview finish = body.nextEncoder().finish();
        byte[] first = body.bytes();
        byte[] last = finish.bytes();
        byte[] result = Arrays.copyOf(first, first.length + last.length);
        System.arraycopy(last, 0, result, first.length, last.length);
        return result;
    }

    public String decodeForRuntime(byte[] bytes) throws ConversionFailure {
        byte[] source = Objects.requireNonNull(bytes, "bytes").clone();
        DecodePreview preview = newStreamingDecoderForRuntime().preview(source, true);
        if (preview.status() == DecodeStatus.MALFORMED) {
            throw new ConversionFailure("bytes are malformed for selected Encoding");
        }
        if (preview.status() != DecodeStatus.EOF) {
            throw new ConversionFailure("decoder did not establish one-shot EOF");
        }

        int consumed = 0;
        StringBuilder result = new StringBuilder();
        for (DecodedUnit unit : preview.units()) {
            consumed += unit.sourceBytes();
            result.append(unit.text());
        }
        if (consumed != source.length) {
            throw new ConversionFailure("decoder did not consume complete one-shot input");
        }
        return result.toString();
    }

    /** Immutable authority-free descriptors may be shared by Actor isolation transfer. */
    public ProtosEncodingValue transferForActorRuntime() { return this; }

    /** Immutable authority-free descriptors may be shared by isolated P transfer. */
    public ProtosEncodingValue transferForParallelRuntime() { return this; }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) { return prototype; }

    private static HostCodec portableCodec(PortableKind kind) {
        return new HostCodec() {
            @Override
            public StreamingDecoder newDecoder(
                    DecodingErrorPolicy errorPolicy, InitialBomPolicy bomPolicy) {
                return new PortableDecoder(kind, errorPolicy, bomPolicy, false);
            }

            @Override
            public StreamingEncoder newEncoder() {
                return new PortableEncoder(kind);
            }
        };
    }

    private static final class PortableEncoder implements StreamingEncoder {
        private final PortableKind kind;

        private PortableEncoder(PortableKind kind) { this.kind = kind; }

        @Override
        public EncodePreview encode(String text) throws ConversionFailure {
            CharsetEncoder encoder = charset(kind).newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                ByteBuffer encoded = encoder.encode(CharBuffer.wrap(text));
                byte[] result = new byte[encoded.remaining()];
                encoded.get(result);
                return new EncodePreview(result, this);
            } catch (CharacterCodingException failure) {
                throw new ConversionFailure(
                        "text is not representable in selected Encoding", failure);
            }
        }

        @Override
        public EncodePreview finish() {
            return new EncodePreview(new byte[0], this);
        }
    }

    private static final class PortableDecoder implements StreamingDecoder {
        private final PortableKind kind;
        private final DecodingErrorPolicy errorPolicy;
        private final InitialBomPolicy bomPolicy;
        private final boolean setupDone;

        private PortableDecoder(
                PortableKind kind,
                DecodingErrorPolicy errorPolicy,
                InitialBomPolicy bomPolicy,
                boolean setupDone) {
            this.kind = kind;
            this.errorPolicy = errorPolicy;
            this.bomPolicy = bomPolicy;
            this.setupDone = setupDone;
        }

        @Override
        public DecodePreview preview(byte[] bytes, boolean endOfInput) {
            Objects.requireNonNull(bytes, "bytes");
            ArrayList<DecodedUnit> units = new ArrayList<>();
            int offset = 0;
            PortableDecoder current = this;

            if (!current.setupDone) {
                byte[] bom = matchingBom(kind);
                if (bom.length > 0) {
                    int common = commonPrefix(bytes, bom);
                    if (common == bytes.length
                            && bytes.length < bom.length
                            && !endOfInput) {
                        return new DecodePreview(units, DecodeStatus.NEED_INPUT);
                    }
                    if (bytes.length >= bom.length && common == bom.length) {
                        current = current.withSetupDone();
                        if (bomPolicy == InitialBomPolicy.PRESERVE) {
                            units.add(new DecodedUnit("", 0, true, current));
                            units.add(new DecodedUnit("\ufeff", bom.length, false, current));
                        } else {
                            units.add(new DecodedUnit("", bom.length, true, current));
                        }
                        offset = bom.length;
                    } else {
                        current = current.withSetupDone();
                        units.add(new DecodedUnit("", 0, true, current));
                    }
                } else {
                    current = current.withSetupDone();
                    units.add(new DecodedUnit("", 0, true, current));
                }
            }

            while (offset < bytes.length) {
                ScalarStep step = decodeScalar(kind, bytes, offset, endOfInput);
                if (step.needInput) {
                    return new DecodePreview(units, DecodeStatus.NEED_INPUT);
                }
                if (step.malformed) {
                    if (errorPolicy == DecodingErrorPolicy.STRICT) {
                        return new DecodePreview(units, DecodeStatus.MALFORMED);
                    }
                    int replacementExtent = replacementExtent(kind, bytes, offset, endOfInput);
                    if (replacementExtent == 0) {
                        return new DecodePreview(units, DecodeStatus.NEED_INPUT);
                    }
                    units.add(new DecodedUnit("\ufffd", replacementExtent, false, current));
                    offset += replacementExtent;
                    continue;
                }
                units.add(new DecodedUnit(step.text, step.consumed, false, current));
                offset += step.consumed;
            }

            return new DecodePreview(
                    units, endOfInput ? DecodeStatus.EOF : DecodeStatus.NEED_INPUT);
        }

        private PortableDecoder withSetupDone() {
            return setupDone ? this : new PortableDecoder(kind, errorPolicy, bomPolicy, true);
        }
    }

    private static final class ScalarStep {
        final String text;
        final int consumed;
        final boolean malformed;
        final boolean needInput;

        private ScalarStep(String text, int consumed, boolean malformed, boolean needInput) {
            this.text = text;
            this.consumed = consumed;
            this.malformed = malformed;
            this.needInput = needInput;
        }

        static ScalarStep scalar(String text, int consumed) {
            return new ScalarStep(text, consumed, false, false);
        }

        static ScalarStep malformed() { return new ScalarStep(null, 0, true, false); }
        static ScalarStep needInput() { return new ScalarStep(null, 0, false, true); }
    }

    private static ScalarStep decodeScalar(
            PortableKind kind, byte[] bytes, int offset, boolean eof) {
        return switch (kind) {
            case LATIN1 -> ScalarStep.scalar(Character.toString((char) unsigned(bytes[offset])), 1);
            case UTF16LE -> decodeUtf16(bytes, offset, eof, true);
            case UTF16BE -> decodeUtf16(bytes, offset, eof, false);
            case UTF8 -> decodeUtf8(bytes, offset, eof);
        };
    }

    private static ScalarStep decodeUtf16(
            byte[] bytes, int offset, boolean eof, boolean littleEndian) {
        int remaining = bytes.length - offset;
        if (remaining < 2) return eof ? ScalarStep.malformed() : ScalarStep.needInput();

        int first = littleEndian
                ? unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8)
                : (unsigned(bytes[offset]) << 8) | unsigned(bytes[offset + 1]);
        if (first >= 0xd800 && first <= 0xdbff) {
            if (remaining < 4) return eof ? ScalarStep.malformed() : ScalarStep.needInput();
            int second = littleEndian
                    ? unsigned(bytes[offset + 2]) | (unsigned(bytes[offset + 3]) << 8)
                    : (unsigned(bytes[offset + 2]) << 8) | unsigned(bytes[offset + 3]);
            if (second < 0xdc00 || second > 0xdfff) return ScalarStep.malformed();
            int codePoint = 0x10000 + ((first - 0xd800) << 10) + (second - 0xdc00);
            return ScalarStep.scalar(new String(Character.toChars(codePoint)), 4);
        }
        if (first >= 0xdc00 && first <= 0xdfff) return ScalarStep.malformed();
        return ScalarStep.scalar(Character.toString((char) first), 2);
    }

    private static ScalarStep decodeUtf8(byte[] bytes, int offset, boolean eof) {
        int remaining = bytes.length - offset;
        int b0 = unsigned(bytes[offset]);
        if (b0 <= 0x7f) return ScalarStep.scalar(Character.toString((char) b0), 1);

        if (b0 >= 0xc2 && b0 <= 0xdf) {
            if (remaining < 2) return eof ? ScalarStep.malformed() : ScalarStep.needInput();
            int b1 = unsigned(bytes[offset + 1]);
            if (!continuation(b1)) return ScalarStep.malformed();
            int cp = ((b0 & 0x1f) << 6) | (b1 & 0x3f);
            return ScalarStep.scalar(new String(Character.toChars(cp)), 2);
        }

        if (b0 >= 0xe0 && b0 <= 0xef) {
            if (remaining < 2) return eof ? ScalarStep.malformed() : ScalarStep.needInput();
            int b1 = unsigned(bytes[offset + 1]);
            boolean secondValid = b0 == 0xe0
                    ? b1 >= 0xa0 && b1 <= 0xbf
                    : b0 == 0xed
                            ? b1 >= 0x80 && b1 <= 0x9f
                            : continuation(b1);
            if (!secondValid) return ScalarStep.malformed();
            if (remaining < 3) return eof ? ScalarStep.malformed() : ScalarStep.needInput();
            int b2 = unsigned(bytes[offset + 2]);
            if (!continuation(b2)) return ScalarStep.malformed();
            int cp = ((b0 & 0x0f) << 12) | ((b1 & 0x3f) << 6) | (b2 & 0x3f);
            return ScalarStep.scalar(new String(Character.toChars(cp)), 3);
        }

        if (b0 >= 0xf0 && b0 <= 0xf4) {
            if (remaining < 2) return eof ? ScalarStep.malformed() : ScalarStep.needInput();
            int b1 = unsigned(bytes[offset + 1]);
            boolean secondValid = b0 == 0xf0
                    ? b1 >= 0x90 && b1 <= 0xbf
                    : b0 == 0xf4
                            ? b1 >= 0x80 && b1 <= 0x8f
                            : continuation(b1);
            if (!secondValid) return ScalarStep.malformed();
            if (remaining < 3) return eof ? ScalarStep.malformed() : ScalarStep.needInput();
            int b2 = unsigned(bytes[offset + 2]);
            if (!continuation(b2)) return ScalarStep.malformed();
            if (remaining < 4) return eof ? ScalarStep.malformed() : ScalarStep.needInput();
            int b3 = unsigned(bytes[offset + 3]);
            if (!continuation(b3)) return ScalarStep.malformed();
            int cp = ((b0 & 0x07) << 18)
                    | ((b1 & 0x3f) << 12)
                    | ((b2 & 0x3f) << 6)
                    | (b3 & 0x3f);
            return ScalarStep.scalar(new String(Character.toChars(cp)), 4);
        }
        return ScalarStep.malformed();
    }

    /** Unicode maximal-subpart byte extent for the malformed sequence beginning at offset. */
    private static int replacementExtent(
            PortableKind kind, byte[] bytes, int offset, boolean eof) {
        if (kind == PortableKind.LATIN1) return 1;
        if (kind == PortableKind.UTF16LE || kind == PortableKind.UTF16BE) {
            int remaining = bytes.length - offset;
            if (remaining < 2) return eof ? remaining : 0;
            boolean little = kind == PortableKind.UTF16LE;
            int first = little
                    ? unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8)
                    : (unsigned(bytes[offset]) << 8) | unsigned(bytes[offset + 1]);
            if (first >= 0xd800 && first <= 0xdbff) {
                if (remaining < 4) return eof ? 2 : 0;
                int second = little
                        ? unsigned(bytes[offset + 2]) | (unsigned(bytes[offset + 3]) << 8)
                        : (unsigned(bytes[offset + 2]) << 8) | unsigned(bytes[offset + 3]);
                return second >= 0xdc00 && second <= 0xdfff ? 4 : 2;
            }
            return 2;
        }

        int remaining = bytes.length - offset;
        int b0 = unsigned(bytes[offset]);
        int expected;
        int secondMin = 0x80;
        int secondMax = 0xbf;
        if (b0 >= 0xc2 && b0 <= 0xdf) expected = 2;
        else if (b0 >= 0xe0 && b0 <= 0xef) {
            expected = 3;
            if (b0 == 0xe0) secondMin = 0xa0;
            if (b0 == 0xed) secondMax = 0x9f;
        } else if (b0 >= 0xf0 && b0 <= 0xf4) {
            expected = 4;
            if (b0 == 0xf0) secondMin = 0x90;
            if (b0 == 0xf4) secondMax = 0x8f;
        } else {
            return 1;
        }

        int consumed = 1;
        for (int i = 1; i < expected; i++) {
            if (i >= remaining) return eof ? consumed : 0;
            int value = unsigned(bytes[offset + i]);
            boolean valid = i == 1
                    ? value >= secondMin && value <= secondMax
                    : continuation(value);
            if (!valid) return consumed;
            consumed++;
        }
        return consumed;
    }

    private static Charset charset(PortableKind kind) {
        return switch (kind) {
            case UTF8 -> StandardCharsets.UTF_8;
            case UTF16LE -> StandardCharsets.UTF_16LE;
            case UTF16BE -> StandardCharsets.UTF_16BE;
            case LATIN1 -> StandardCharsets.ISO_8859_1;
        };
    }

    private static byte[] matchingBom(PortableKind kind) {
        return switch (kind) {
            case UTF8 -> new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};
            case UTF16LE -> new byte[] {(byte) 0xff, (byte) 0xfe};
            case UTF16BE -> new byte[] {(byte) 0xfe, (byte) 0xff};
            case LATIN1 -> new byte[0];
        };
    }

    private static int commonPrefix(byte[] bytes, byte[] prefix) {
        int limit = Math.min(bytes.length, prefix.length);
        int index = 0;
        while (index < limit && bytes[index] == prefix[index]) index++;
        return index;
    }

    private static int unsigned(byte value) { return value & 0xff; }
    private static boolean continuation(int value) { return value >= 0x80 && value <= 0xbf; }
}
