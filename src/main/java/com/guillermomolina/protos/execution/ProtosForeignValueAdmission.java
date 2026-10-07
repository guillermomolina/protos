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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosRawForeignValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.util.Objects;

/**
 * The single D188 admission boundary between provider values and Protos values, in both
 * directions.
 *
 * <p>Inbound, {@link #admit} converts exactly the provider-classified lossless scalars and wraps
 * everything else as a raw foreign reference bound to the exact session that produced it. The same
 * admission applies to D189 callback arguments. Outbound, {@link #exportOrNull} admits only
 * lossless scalars and raw references returning to their own session, which is also the whole D189
 * callback result domain; ordinary objects, collections, Closures, Futures, ActorRefs, and
 * capabilities never cross as values, so a generic operation creates no new authority. The one
 * additional outbound form is an operation-scoped D189 callback capability for a Closure argument
 * ({@link #exportCallbackOrNull}), which is not a foreign value and exports no retained handle.
 */
final class ProtosForeignValueAdmission {
    private ProtosForeignValueAdmission() {}

    /** Admits one provider value; runs inside the entered operation that produced it. */
    static Object admit(
            ProtosForeignProviderSessionBinding session,
            ProtosForeignValueAdapter adapter,
            ProtosForeignProviderSession live,
            Object foreign)
            throws Exception {
        Objects.requireNonNull(foreign, "foreign value");
        ProtosForeignAdmissionDescriptor descriptor =
                Objects.requireNonNull(adapter.classify(live, foreign), "foreign classification");
        return switch (descriptor.kind()) {
            case BOOLEAN -> ProtosBooleanValue.of((Boolean) descriptor.scalar());
            case NULL -> ProtosNullValue.INSTANCE;
            case STRING -> unicodeOrRaw(session, adapter, foreign, (String) descriptor.scalar());
            case INTEGER -> new ProtosIntegerValue((BigInteger) descriptor.scalar());
            case BINARY64 -> new ProtosFloatValue((Double) descriptor.scalar());
            case RAW ->
                    new ProtosRawForeignValue(
                            new ProtosForeignHandle(session, adapter, foreign, descriptor));
        };
    }

    /** Text that is not a valid Unicode scalar sequence stays an opaque raw reference. */
    private static Object unicodeOrRaw(
            ProtosForeignProviderSessionBinding session,
            ProtosForeignValueAdapter adapter,
            Object foreign,
            String text) {
        try {
            return new ProtosStringValue(text);
        } catch (IllegalArgumentException notUnicodeScalars) {
            return new ProtosRawForeignValue(
                    new ProtosForeignHandle(
                            session, adapter, foreign, ProtosForeignAdmissionDescriptor.opaque()));
        }
    }

    /**
     * Outbound form of {@code value} for an operation on {@code origin}, or null when it cannot
     * cross losslessly without creating authority; the caller then fails before foreign entry.
     */
    static ProtosForeignArgument exportOrNull(ProtosForeignHandle origin, Object value) {
        ProtosForeignArgument argument;
        if (value == ProtosBooleanValue.TRUE || value == ProtosBooleanValue.FALSE) {
            argument =
                    new ProtosForeignArgument(
                            ProtosForeignAdmissionDescriptor.Kind.BOOLEAN,
                            value == ProtosBooleanValue.TRUE);
        } else if (value == ProtosNullValue.INSTANCE) {
            argument = new ProtosForeignArgument(ProtosForeignAdmissionDescriptor.Kind.NULL, null);
        } else if (value instanceof ProtosStringValue string) {
            argument =
                    new ProtosForeignArgument(
                            ProtosForeignAdmissionDescriptor.Kind.STRING, string.value());
        } else if (value instanceof ProtosIntegerValue integer) {
            argument =
                    new ProtosForeignArgument(
                            ProtosForeignAdmissionDescriptor.Kind.INTEGER, integer.value());
        } else if (value instanceof ProtosFloatValue floating) {
            argument =
                    new ProtosForeignArgument(
                            ProtosForeignAdmissionDescriptor.Kind.BINARY64, floating.value());
        } else if (value instanceof ProtosRawForeignValue raw
                && raw.handleForRuntime() instanceof ProtosForeignHandle handle
                && handle.session() == origin.session()
                && handle.adapter() == origin.adapter()) {
            argument =
                    new ProtosForeignArgument(
                            ProtosForeignAdmissionDescriptor.Kind.RAW, handle.target());
        } else {
            return null;
        }
        return origin.adapter().acceptsArgument(argument) ? argument : null;
    }

    /** Outbound form of a prepared D189 callback, or null when the provider cannot accept one. */
    static ProtosForeignArgument exportCallbackOrNull(
            ProtosForeignHandle origin, ProtosForeignCallback callback) {
        ProtosForeignArgument argument = ProtosForeignArgument.callback(callback);
        return origin.adapter().acceptsArgument(argument) ? argument : null;
    }
}
