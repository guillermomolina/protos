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

import java.util.Objects;

/**
 * Provider-neutral outbound value of a generic D188 foreign operation.
 *
 * <p>Only Protos Boolean, null, String, Integer, and Float values and raw foreign references of
 * the same session cross into a generic foreign operation. A {@link
 * ProtosForeignAdmissionDescriptor.Kind#RAW} argument carries the exact underlying target; every
 * other kind carries its lossless host scalar ({@link Boolean}, {@code null}, {@link String}, an
 * Integer as {@link Long} within the signed-long range and as {@link java.math.BigInteger} only
 * beyond it, {@link Double}). The provider decides through {@link
 * ProtosForeignValueAdapter#acceptsArgument} whether it can represent the value losslessly.
 *
 * <p>A D189 callback argument has no admission kind: it is not a foreign value but an ephemeral
 * {@link ProtosForeignCallback} capability, live only inside the operation that receives it
 * ({@link #isCallback()}).
 */
record ProtosForeignArgument(ProtosForeignAdmissionDescriptor.Kind kind, Object value) {
    ProtosForeignArgument {
        if (kind == null) {
            if (!(value instanceof ProtosForeignCallback)) {
                throw new IllegalArgumentException("only a callback argument has no kind");
            }
        } else if ((kind == ProtosForeignAdmissionDescriptor.Kind.NULL) != (value == null)) {
            throw new IllegalArgumentException("only a null argument has no value");
        }
    }

    static ProtosForeignArgument callback(ProtosForeignCallback callback) {
        return new ProtosForeignArgument(null, Objects.requireNonNull(callback, "callback"));
    }

    boolean isCallback() {
        return kind == null;
    }

    /** The callback capability of a callback argument. */
    ProtosForeignCallback callback() {
        if (kind != null) {
            throw new IllegalStateException("not a callback argument");
        }
        return (ProtosForeignCallback) value;
    }
}
