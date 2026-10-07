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

import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Entered/not-entered boundary of one foreign operation and the {@code ForeignError} projection
 * (D188 "Foreign failures").
 *
 * <p>A failure detected before the provider is called, including a closed session, is the
 * ordinary Protos failure. Every exception the provider throws after entry, including one raised
 * while admitting its result, becomes a fresh {@code ForeignError} whose six visible slots hold
 * only provider-sanitized Strings, null, or a projected Error cause. The host exception itself is
 * never reachable from guest code.
 */
final class ProtosForeignOperation {
    /** Bounds cause projection even for an adapter that keeps producing fresh causes. */
    private static final int MAX_CAUSE_DEPTH = 32;

    private static final String UNKNOWN_LANGUAGE = "unknown";

    @FunctionalInterface
    interface Body<T> {
        T run(ProtosForeignProviderSession live) throws Exception;
    }

    private ProtosForeignOperation() {}

    /** Runs {@code body} as the foreign operation {@code operation} of one exact session. */
    @TruffleBoundary
    static <T> T enter(
            ProtosForeignProviderSessionBinding session,
            ProtosForeignValueAdapter adapter,
            String operation,
            ProtosPrelude prelude,
            Body<T> body) {
        Objects.requireNonNull(operation, "operation");
        ProtosForeignProviderSession live = requireLiveSession(session, prelude);
        try {
            return body.run(live);
        } catch (Exception failure) {
            // D189 will distinguish Protos failures propagating through a callback; D188 admits
            // no callback, so everything raised after entry is a foreign failure.
            throw new ProtosSignalException(foreignError(prelude, adapter, operation, failure));
        }
    }

    static <T> T enter(
            ProtosForeignHandle handle, String operation, ProtosPrelude prelude, Body<T> body) {
        return enter(handle.session(), handle.adapter(), operation, prelude, body);
    }

    /** The live session, or the ordinary pre-entry Error once the generation closed. */
    private static ProtosForeignProviderSession requireLiveSession(
            ProtosForeignProviderSessionBinding session, ProtosPrelude prelude) {
        if (session.isOpenForRuntime()) {
            try {
                return session.sessionForRuntime();
            } catch (IllegalStateException closedConcurrently) {
                // Falls through to the ordinary failure; never acquires a replacement session.
            }
        }
        throw ordinaryError(prelude);
    }

    static ProtosSignalException ordinaryError(ProtosPrelude prelude) {
        return new ProtosSignalException(
                ProtosCoreErrors.newOccurrence(
                        requirePrelude(prelude), ProtosCoreErrors.StandardError.ERROR));
    }

    static ProtosObjectValue foreignError(
            ProtosPrelude prelude,
            ProtosForeignValueAdapter adapter,
            String operation,
            Throwable failure) {
        Set<Throwable> projected = Collections.newSetFromMap(new IdentityHashMap<>());
        return project(
                requirePrelude(prelude), adapter, safeLanguage(adapter), operation, failure,
                projected, 0);
    }

    private static ProtosObjectValue project(
            ProtosPrelude prelude,
            ProtosForeignValueAdapter adapter,
            String language,
            String operation,
            Throwable failure,
            Set<Throwable> projected,
            int depth) {
        projected.add(failure);
        ProtosForeignFailureDescription description = describe(adapter, failure);
        ProtosObjectValue error =
                ProtosCoreErrors.newOccurrence(
                        prelude, ProtosCoreErrors.StandardError.FOREIGN_ERROR);
        error.createLocalSlot("language", new ProtosStringValue(language));
        error.createLocalSlot("operation", new ProtosStringValue(operation));
        error.createLocalSlot("category", safeString(description.category()));
        error.createLocalSlot("foreignCategory", safeString(description.foreignCategory()));
        error.createLocalSlot("message", safeString(description.message()));
        Throwable cause = description.cause();
        error.createLocalSlot(
                "cause",
                cause == null || projected.contains(cause) || depth >= MAX_CAUSE_DEPTH
                        ? ProtosNullValue.INSTANCE
                        : project(
                                prelude, adapter, language, operation, cause, projected,
                                depth + 1));
        return error;
    }

    private static ProtosForeignFailureDescription describe(
            ProtosForeignValueAdapter adapter, Throwable failure) {
        try {
            ProtosForeignFailureDescription description = adapter.describeFailure(failure);
            return description == null ? ProtosForeignFailureDescription.EMPTY : description;
        } catch (RuntimeException unsanitizable) {
            return ProtosForeignFailureDescription.EMPTY;
        }
    }

    private static String safeLanguage(ProtosForeignValueAdapter adapter) {
        try {
            String language = adapter.language();
            if (language != null) {
                new ProtosStringValue(language);
                return language;
            }
        } catch (RuntimeException unsafe) {
            // Falls back to the neutral name.
        }
        return UNKNOWN_LANGUAGE;
    }

    private static Object safeString(String text) {
        if (text == null) {
            return ProtosNullValue.INSTANCE;
        }
        try {
            return new ProtosStringValue(text);
        } catch (IllegalArgumentException notUnicodeScalars) {
            return ProtosNullValue.INSTANCE;
        }
    }

    private static ProtosPrelude requirePrelude(ProtosPrelude prelude) {
        if (prelude == null) {
            throw new IllegalStateException("foreign operations require an owning Core prelude");
        }
        return prelude;
    }
}
