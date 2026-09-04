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

import java.util.Objects;

public final class ProtosPrelude {
    private final ProtosObjectValue bindings;
    private final ProtosObjectValue contextPrototype;

    public ProtosPrelude(
            ProtosObjectValue bindings,
            ProtosObjectValue contextPrototype) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.contextPrototype =
                Objects.requireNonNull(contextPrototype, "contextPrototype");

        if (!bindings.isFrozen()) {
            throw new IllegalArgumentException("prelude bindings must be frozen");
        }
        if (bindings.parent().orElse(null) != contextPrototype) {
            throw new IllegalArgumentException(
                    "prelude bindings must delegate to Context");
        }
        if (bindings.readLocalSlot("Context").orElse(null)
                != contextPrototype) {
            throw new IllegalArgumentException(
                    "prelude Context binding must be the Context prototype");
        }

        requireNumericPrototypeHierarchy();

        Object errorBinding = bindings.readLocalSlot("Error").orElse(null);
        if (!(errorBinding instanceof ProtosObjectValue errorPrototype)
                || errorPrototype.parent().orElse(null)
                        != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "prelude Error binding must be an ordinary child of Object");
        }
    }

    public ProtosObjectValue bindings() {
        return bindings;
    }

    public ProtosObjectValue contextPrototype() {
        return contextPrototype;
    }

    private void requireNumericPrototypeHierarchy() {
        ProtosObjectValue number = requiredOrdinaryBinding("Number");
        ProtosObjectValue integer = requiredOrdinaryBinding("Integer");
        ProtosObjectValue floating = requiredOrdinaryBinding("Float");
        if (number.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "standard Number must delegate directly to Object");
        }
        if (integer.parent().orElse(null) != number) {
            throw new IllegalArgumentException(
                    "standard Integer must delegate directly to Number");
        }
        if (floating.parent().orElse(null) != number) {
            throw new IllegalArgumentException(
                    "standard Float must delegate directly to Number");
        }
    }

    private ProtosObjectValue requiredOrdinaryBinding(String name) {
        Object binding = bindings.readLocalSlot(name).orElse(null);
        if (!(binding instanceof ProtosObjectValue object)) {
            throw new IllegalArgumentException(
                    "standard " + name + " binding must be an ordinary object");
        }
        return object;
    }

    public ProtosObjectValue numberPrototype() {
        return requiredOrdinaryBinding("Number");
    }

    public ProtosObjectValue integerPrototype() {
        return requiredOrdinaryBinding("Integer");
    }

    public ProtosObjectValue floatPrototype() {
        return requiredOrdinaryBinding("Float");
    }

    public ProtosObjectValue errorPrototype() {
        return (ProtosObjectValue)
                bindings.readLocalSlot("Error").orElseThrow();
    }

    public ProtosObjectValue newError() {
        return new ProtosObjectValue(errorPrototype());
    }

    public ProtosObjectValue invalidReturnPrototype() {
        Object binding =
                bindings.readLocalSlot("InvalidReturn").orElseThrow();
        if (!(binding instanceof ProtosObjectValue invalidReturnPrototype)) {
            throw new IllegalStateException(
                    "standard InvalidReturn binding is not an ordinary object");
        }
        if (invalidReturnPrototype.parent().orElse(null) != errorPrototype()) {
            throw new IllegalStateException(
                    "standard InvalidReturn must delegate directly to Error");
        }
        return invalidReturnPrototype;
    }

    public ProtosObjectValue newInvalidReturn() {
        return new ProtosObjectValue(invalidReturnPrototype());
    }

    public ProtosObjectValue arrayPrototype() {
        Object binding = bindings.readLocalSlot("Array").orElseThrow();
        if (!(binding instanceof ProtosObjectValue arrayPrototype)) {
            throw new IllegalStateException(
                    "standard Array binding is not an ordinary object");
        }
        return arrayPrototype;
    }

    public ProtosArrayValue newArray(java.util.List<?> elements) {
        return new ProtosArrayValue(arrayPrototype(), elements);
    }

    public ProtosArrayValue newFrozenArray(java.util.List<?> elements) {
        ProtosArrayValue array = newArray(elements);
        array.freeze();
        return array;
    }

    public ProtosObjectValue newExecutionContext() {
        return new ProtosObjectValue(contextPrototype);
    }

    public ProtosActivation newModuleActivation() {
        ProtosObjectValue moduleContext = newExecutionContext();
        return ProtosActivation.withPrelude(
                moduleContext,
                java.util.List.of(bindings),
                moduleContext,
                this);
    }
}
