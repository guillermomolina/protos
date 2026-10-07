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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.util.List;

/**
 * Loop state of one projected D188 {@code foreign.each(block)}: a Protos-side pull over a
 * provider iterator. The provider only acquires the iterator and answers has-next/next; Protos
 * owns the loop and invokes {@code block} through ordinary invocation, so the block is never
 * handed to the provider.
 *
 * <p>Construction validates the receiver, its provider-declared iteration capability, the exact
 * arity, and the ordinary callability of {@code block} (by selection, never by execution), all
 * before any foreign operation. Each provider call is one entered foreign operation of the exact
 * session that admitted the receiver. Each pulled element is admitted by {@link
 * ProtosForeignValueAdmission}. There is no snapshot: elements are pulled one at a time in
 * provider order, and the cursor advances only after the callback completed normally, so an
 * Error or non-local exit of the callback leaves no later pull. Normal completion returns the
 * receiver.
 *
 * <p>Both the structured C-prime dispatcher and the non-Task native body drive this one cursor,
 * so suspension inside the callback resumes the same visit without a new Task or replay.
 */
final class ProtosForeignEachCall {
    private final Object receiver;
    private final ProtosForeignHandle handle;
    private final Object block;
    private final ProtosActivation activation;
    private final ProtosPrelude prelude;
    private Object iterator;
    /** The has-next answer of the current position, or null until asked. */
    private Boolean pending;
    /** The admitted element of the current position, or null until pulled. */
    private Object current;

    ProtosForeignEachCall(Object receiver, List<?> supplied, ProtosActivation activation) {
        this.activation = activation;
        this.prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "foreign each requires an owning Core prelude"));
        this.receiver = receiver;
        this.handle = ProtosForeignHandle.of(receiver);
        if (handle == null || !handle.projectsEach() || supplied.size() != 1) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        this.block = supplied.get(0);
        requireInvokable(block, prelude);
    }

    /** Ordinary polymorphic callability: {@code call} selects a Closure; nothing executes. */
    private static void requireInvokable(Object candidate, ProtosPrelude prelude) {
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(candidate, "call", prelude)
                            .orElseThrow(() -> ProtosForeignOperation.ordinaryError(prelude));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
        if (!(selected.value() instanceof ProtosClosureValue)) {
            throw ProtosForeignOperation.ordinaryError(prelude);
        }
    }

    /** Acquires the iterator on first use, then asks the provider once per position. */
    boolean hasNext() {
        if (pending == null) {
            if (iterator == null) {
                iterator =
                        ProtosForeignOperation.enter(
                                handle,
                                "iterator",
                                prelude,
                                live -> handle.adapter().openIterator(live, handle.target()));
            }
            pending =
                    ProtosForeignOperation.enter(
                            handle,
                            "iteratorHasNext",
                            prelude,
                            live -> handle.adapter().iteratorHasNext(live, iterator));
        }
        return pending;
    }

    /** Pulls and admits the element of the current position exactly once. */
    Object current() {
        if (!hasNext()) {
            throw new IllegalStateException("foreign each element requested after exhaustion");
        }
        if (current == null) {
            current =
                    ProtosForeignOperation.enter(
                            handle,
                            "iteratorNext",
                            prelude,
                            live ->
                                    ProtosForeignValueAdmission.admit(
                                            handle.session(),
                                            handle.adapter(),
                                            live,
                                            handle.adapter().iteratorNext(live, iterator)));
        }
        return current;
    }

    /** Called only after the callback of the current position completed normally. */
    void advance() {
        if (current == null) {
            throw new IllegalStateException("foreign each advanced before its element was pulled");
        }
        pending = null;
        current = null;
    }

    Object finish() {
        if (pending == null || pending) {
            throw new IllegalStateException("foreign each finished before exhaustion");
        }
        return receiver;
    }

    Object callback() {
        return block;
    }

    ProtosActivation activation() {
        return activation;
    }
}
