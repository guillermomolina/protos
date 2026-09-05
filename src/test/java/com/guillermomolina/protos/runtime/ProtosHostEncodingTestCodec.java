/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/** Stateful deterministic host Encoding fixture used only by I015/I017 conformance tests. */
public final class ProtosHostEncodingTestCodec implements ProtosEncodingValue.HostCodec {
    @Override
    public ProtosEncodingValue.StreamingDecoder newDecoder(
            ProtosEncodingValue.DecodingErrorPolicy errorPolicy,
            ProtosEncodingValue.InitialBomPolicy ignoredBomPolicy) {
        return new Decoder(errorPolicy, false, false);
    }

    @Override
    public ProtosEncodingValue.StreamingEncoder newEncoder() {
        return new Encoder(false);
    }

    private static final class Decoder implements ProtosEncodingValue.StreamingDecoder {
        private final ProtosEncodingValue.DecodingErrorPolicy policy;
        private final boolean setupDone;
        private final boolean upper;

        private Decoder(
                ProtosEncodingValue.DecodingErrorPolicy policy,
                boolean setupDone,
                boolean upper) {
            this.policy = policy;
            this.setupDone = setupDone;
            this.upper = upper;
        }

        @Override
        public ProtosEncodingValue.DecodePreview preview(byte[] bytes, boolean eof) {
            ArrayList<ProtosEncodingValue.DecodedUnit> units = new ArrayList<>();
            Decoder current = this;
            if (!current.setupDone) {
                current = new Decoder(policy, true, current.upper);
                units.add(new ProtosEncodingValue.DecodedUnit("", 0, true, current));
            }

            for (byte raw : bytes) {
                int value = raw & 0xff;
                if (value == 0x0e) {
                    current = new Decoder(policy, true, true);
                    units.add(new ProtosEncodingValue.DecodedUnit("", 1, false, current));
                    continue;
                }
                if (value == 0x0f) {
                    current = new Decoder(policy, true, false);
                    units.add(new ProtosEncodingValue.DecodedUnit("", 1, false, current));
                    continue;
                }
                if (value >= 0x80) {
                    if (policy == ProtosEncodingValue.DecodingErrorPolicy.STRICT) {
                        return new ProtosEncodingValue.DecodePreview(
                                units, ProtosEncodingValue.DecodeStatus.MALFORMED);
                    }
                    units.add(new ProtosEncodingValue.DecodedUnit("\ufffd", 1, false, current));
                    continue;
                }

                char character = (char) value;
                String text;
                if (character >= 'A' && character <= 'Z') character = Character.toLowerCase(character);
                if (character >= 'a' && character <= 'z') {
                    character = current.upper ? Character.toUpperCase(character) : character;
                }
                text = Character.toString(character);
                units.add(new ProtosEncodingValue.DecodedUnit(text, 1, false, current));
            }

            return new ProtosEncodingValue.DecodePreview(
                    units,
                    eof
                            ? ProtosEncodingValue.DecodeStatus.EOF
                            : ProtosEncodingValue.DecodeStatus.NEED_INPUT);
        }
    }

    private static final class Encoder implements ProtosEncodingValue.StreamingEncoder {
        private final boolean upper;

        private Encoder(boolean upper) { this.upper = upper; }

        @Override
        public ProtosEncodingValue.EncodePreview encode(String text)
                throws ProtosEncodingValue.ConversionFailure {
            boolean state = upper;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                offset += Character.charCount(codePoint);
                if (codePoint > 0x7f) {
                    throw new ProtosEncodingValue.ConversionFailure(
                            "host test Encoding accepts ASCII only");
                }
                char character = (char) codePoint;
                boolean letter =
                        (character >= 'a' && character <= 'z')
                                || (character >= 'A' && character <= 'Z');
                if (letter) {
                    boolean wantsUpper = character >= 'A' && character <= 'Z';
                    if (wantsUpper != state) {
                        output.write(wantsUpper ? 0x0e : 0x0f);
                        state = wantsUpper;
                    }
                    character = Character.toLowerCase(character);
                }
                output.write(character);
            }
            return new ProtosEncodingValue.EncodePreview(output.toByteArray(), new Encoder(state));
        }

        @Override
        public ProtosEncodingValue.EncodePreview finish() {
            if (!upper) return new ProtosEncodingValue.EncodePreview(new byte[0], this);
            return new ProtosEncodingValue.EncodePreview(new byte[] {0x0f}, new Encoder(false));
        }
    }
}
