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

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.util.Objects;

/** Authentication and creation-scoped delegation of exact source-backed module targets. */
public final class ProtosModuleReferenceSupport {
    private static final class ReferenceFamily {
        static final Object VALUE = new Object();
    }

    /** Only immutable target and scope identities are retained; never an execution object. */
    private record ReferenceState(
            ProtosModuleKey key, Object moduleRuntime, ProtosProcessRuntime process) {}

    private record SpawnDelegation(ReferenceState state) {}

    private ProtosModuleReferenceSupport() {}

    /** Exact family recognition without guest lookup or invocation. */
    public static boolean isReference(Object candidate) {
        return candidate instanceof ProtosSealedValue sealed
                && sealed.belongsTo(ReferenceFamily.VALUE);
    }

    /** Mints a fresh, slotless frozen reference in the acquiring resolution environment. */
    public static ProtosSealedValue mint(ProtosModuleKey key, ProtosActivation owner) {
        Objects.requireNonNull(key, "key");
        if (key.isForeign()) {
            throw new IllegalArgumentException("a module reference requires a source-backed target");
        }
        return mintState(new ReferenceState(key, moduleRuntime(owner), process(owner)));
    }

    private static ProtosSealedValue mintState(ReferenceState state) {
        return ProtosSealedValue.mint(
                ReferenceFamily.VALUE, ProtosObjectValue.rootObject(), state);
    }

    private static ReferenceState state(Object candidate) {
        if (!isReference(candidate)) {
            throw new IllegalArgumentException("value is not a resolved module reference");
        }
        return (ReferenceState) ((ProtosSealedValue) candidate).stateFor(ReferenceFamily.VALUE);
    }

    private static Object moduleRuntime(ProtosActivation owner) {
        Object runtime = owner.prelude().orElseThrow(
                () -> new IllegalArgumentException("module reference requires a Core prelude"))
                .moduleRuntimeForRuntime();
        if (runtime == null) {
            throw new IllegalArgumentException("module reference requires a resolution environment");
        }
        return runtime;
    }

    private static ProtosProcessRuntime process(ProtosActivation owner) {
        return owner.executionDomain().currentActorForRuntime()
                .flatMap(ProtosActor::processForRuntime).orElse(null);
    }

    private static void requireScope(
            ReferenceState state, ProtosActivation owner, boolean spawning) {
        if (state.moduleRuntime() != moduleRuntime(owner)
                || state.process() != process(owner)
                || (spawning && state.process() == null)) {
            throw new IllegalArgumentException("module reference belongs to a different scope");
        }
    }

    /** Validates local ownership before exposing the private canonical target to its loader. */
    public static ProtosModuleKey requireKey(Object candidate, ProtosActivation owner) {
        ReferenceState state = state(candidate);
        requireScope(state, owner, false);
        return state.key();
    }

    /** Spawn additionally requires actual hosting in the same Process. */
    public static ProtosModuleKey requireSpawnKey(Object candidate, ProtosActivation owner) {
        ReferenceState state = state(candidate);
        requireScope(state, owner, true);
        return state.key();
    }

    /** The copier memo, rather than this family, owns alias preservation. */
    public static Object prepareSpawnDelegation(Object candidate, ProtosActivation source) {
        ReferenceState state = state(candidate);
        requireScope(state, source, true);
        return new SpawnDelegation(state);
    }

    public static boolean isSpawnDelegation(Object candidate) {
        return candidate instanceof SpawnDelegation;
    }

    /** Reconstructs one destination identity without resolving or touching a module cache. */
    public static ProtosSealedValue materializeSpawnDelegation(
            Object candidate, ProtosActivation destination) {
        if (!(candidate instanceof SpawnDelegation descriptor)) {
            throw new IllegalArgumentException("value is not a module-reference spawn descriptor");
        }
        requireScope(descriptor.state(), destination, true);
        return mintState(descriptor.state());
    }
}
