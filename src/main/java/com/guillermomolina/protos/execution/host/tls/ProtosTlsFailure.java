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

/**
 * A failure fact conclusively established by the private JSSE/PKIX backend of {@code
 * std:tls/Client} (D220–S12).
 *
 * <p>A {@link Fact} is backend evidence, not a public {@code TlsError.kind}: the mapping to the
 * portable kinds, the preservation of transport Errors and every cleanup decision belong to the
 * Protos session (I130-B). Facts are never derived from exception wording or from peer alert
 * bytes; any JSSE failure without conclusive evidence is reported only as {@link
 * Fact#HANDSHAKE_FAILED} or {@link Fact#PROTOCOL_FAILED}.
 */
public final class ProtosTlsFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Fact {
        /** No JSSE context or no configuration within the fixed compliant profile. */
        BACKEND_UNAVAILABLE,
        /** Explicitly selected Java certificate material cannot be read. */
        TRUST_MATERIAL_UNAVAILABLE,
        /** The verifier rejected the peer chain (PKIX or serverAuth usage). */
        UNTRUSTED_CHAIN,
        /** The session's negotiated version is outside the configured versions. */
        NEGOTIATED_VERSION_OUTSIDE_POLICY,
        /** The session's negotiated application protocol was not offered. */
        NEGOTIATED_ALPN_OUTSIDE_POLICY,
        /** Any other failure before the session was established. */
        HANDSHAKE_FAILED,
        /** Any other failure after the session was established. */
        PROTOCOL_FAILED,
        /** Transport input ended while JSSE had not received close_notify. */
        TRUNCATED,
        /**
         * A received, syntactically plausible TLS record header advertises an encrypted fragment
         * longer than the approved bound of 18,432 octets (header excluded); conclusive from the
         * header alone, before any of the fragment arrives.
         */
        RECORD_LIMIT_EXCEEDED
    }

    private final Fact fact;

    public ProtosTlsFailure(Fact fact, String detail) {
        this(fact, detail, null);
    }

    public ProtosTlsFailure(Fact fact, String detail, Throwable cause) {
        super(fact + ": " + detail, cause);
        this.fact = fact;
    }

    public Fact fact() {
        return fact;
    }
}
