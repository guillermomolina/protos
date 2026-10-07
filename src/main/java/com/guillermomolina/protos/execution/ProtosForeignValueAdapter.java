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

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral D188 value contract of one foreign provider: source classification and the
 * operations the generic projection may perform. Import-based values, a future {@code std:interop},
 * concrete providers, and D189 callback arguments all reach Protos through this one contract and
 * {@link ProtosForeignValueAdmission}.
 *
 * <p>Every operation receives the live session of the exact binding that admitted the value and
 * runs only after the runtime checked its preconditions; any exception it throws is an entered
 * foreign failure and becomes a fresh {@code ForeignError}. Defaults describe a provider with no
 * projectable capability. No method grants host authority.
 */
interface ProtosForeignValueAdapter {
    /** Safe name of the foreign language/provider, used for {@code ForeignError.language}. */
    String language();

    /** Classifies one provider value by its source semantic category. */
    ProtosForeignAdmissionDescriptor classify(ProtosForeignProviderSession session, Object value)
            throws Exception;

    /** Whether the provider represents {@code argument} losslessly; checked before entry. */
    default boolean acceptsArgument(ProtosForeignArgument argument) {
        return true;
    }

    /**
     * Whether reading member {@code name} of {@code target} is faithful to an ordinary Protos
     * member read: no material side effects incompatible with a read, and receiver binding of a
     * method-like result preserved. Readability alone is not fidelity.
     */
    default boolean canFaithfullyReadMember(
            ProtosForeignProviderSession session, Object target, String name) throws Exception {
        return false;
    }

    default Object readMember(ProtosForeignProviderSession session, Object target, String name)
            throws Exception {
        throw new UnsupportedOperationException("foreign member read");
    }

    default Object execute(
            ProtosForeignProviderSession session, Object target, List<ProtosForeignArgument> args)
            throws Exception {
        throw new UnsupportedOperationException("foreign execution");
    }

    default Object readElement(
            ProtosForeignProviderSession session, Object target, ProtosForeignArgument index)
            throws Exception {
        throw new UnsupportedOperationException("foreign element read");
    }

    default void writeElement(
            ProtosForeignProviderSession session,
            Object target,
            ProtosForeignArgument index,
            ProtosForeignArgument value)
            throws Exception {
        throw new UnsupportedOperationException("foreign element write");
    }

    /**
     * Acquires a fresh pull iterator over {@code target} for one projected {@code each}. The
     * iterator is a provider-private handle bound to {@code session}; it is never admitted or
     * guest-visible. Iteration never receives a Protos callback: Protos owns the loop.
     */
    default Object openIterator(ProtosForeignProviderSession session, Object target)
            throws Exception {
        throw new UnsupportedOperationException("foreign iterator");
    }

    default boolean iteratorHasNext(ProtosForeignProviderSession session, Object iterator)
            throws Exception {
        throw new UnsupportedOperationException("foreign iterator has-next");
    }

    /** Pulls the next element; the runtime admits it like any other provider result. */
    default Object iteratorNext(ProtosForeignProviderSession session, Object iterator)
            throws Exception {
        throw new UnsupportedOperationException("foreign iterator next");
    }

    /**
     * Sanitizes one entered failure. The default exposes nothing beyond the language and
     * operation: host messages, stacks, and objects are never copied implicitly.
     */
    default ProtosForeignFailureDescription describeFailure(Throwable failure) {
        return ProtosForeignFailureDescription.EMPTY;
    }

    /**
     * Adapter of a provider that declares no value semantics: every value is an opaque raw
     * reference without stable identity or projectable capability.
     */
    static ProtosForeignValueAdapter opaque(String language) {
        Objects.requireNonNull(language, "language");
        return new ProtosForeignValueAdapter() {
            @Override
            public String language() {
                return language;
            }

            @Override
            public ProtosForeignAdmissionDescriptor classify(
                    ProtosForeignProviderSession session, Object value) {
                return ProtosForeignAdmissionDescriptor.opaque();
            }
        };
    }
}
