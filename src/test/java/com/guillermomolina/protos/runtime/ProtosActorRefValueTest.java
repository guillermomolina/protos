/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class ProtosActorRefValueTest {
    @Test
    void actorBoundaryRematerializationProducesFreshWrapperForSameIncarnation() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActor actor = new ProtosActor(prototype);
        ProtosActorRefValue original = actor.reference();
        ProtosActorRefValue transferred = original.rematerializeForActorTransfer();

        assertNotSame(original, transferred);
        assertTrue(ProtosIdentity.identical(original, transferred));
        assertEquals(
                ProtosIdentity.identityHash(original),
                ProtosIdentity.identityHash(transferred));
        assertEquals(
                original.incarnationIdentityForRuntime(),
                transferred.incarnationIdentityForRuntime());
        assertSame(actor, transferred.localActorForRuntime());
    }

    @Test
    void repeatedRematerializationNeverCreatesANewSemanticReference() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActor actor = new ProtosActor(prototype);
        ProtosActorRefValue original = actor.reference();
        ProtosActorRefValue first = original.rematerializeForActorTransfer();
        ProtosActorRefValue second = first.rematerializeForActorTransfer();

        assertNotSame(original, first);
        assertNotSame(first, second);
        assertNotSame(original, second);
        assertTrue(ProtosIdentity.identical(original, first));
        assertTrue(ProtosIdentity.identical(first, second));
        assertTrue(ProtosIdentity.identical(original, second));
        assertEquals(
                ProtosIdentity.identityHash(original),
                ProtosIdentity.identityHash(second));
    }

    @Test
    void rematerializedReferenceKeepsItsOriginalDelegationParent() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActor actor = new ProtosActor(prototype);
        ProtosActorRefValue transferred = actor.reference().rematerializeForActorTransfer();

        assertSame(prototype, transferred.representedDelegationParent(null));
    }

    @Test
    void actorTerminationDoesNotRetargetOrInvalidateTransferredReferenceIdentity() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActor actor = new ProtosActor(prototype);
        ProtosActorRefValue original = actor.reference();
        ProtosActorRefValue transferred = original.rematerializeForActorTransfer();
        long identity = transferred.incarnationIdentityForRuntime();

        actor.beginTermination();
        actor.markTerminated();

        assertEquals(identity, transferred.incarnationIdentityForRuntime());
        assertSame(actor, transferred.localActorForRuntime());
        assertTrue(ProtosIdentity.identical(original, transferred));
        assertEquals(ProtosActor.LifecycleState.TERMINATED, actor.lifecycleState());
    }

    @Test
    void replacementActorCannotAcquireOldTransferredActorRefIdentity() {
        ProtosObjectValue prototype = actorRefPrototype();
        ProtosActor originalActor = new ProtosActor(prototype);
        ProtosActorRefValue oldTransferred =
                originalActor.reference().rematerializeForActorTransfer();
        originalActor.beginTermination();
        originalActor.markTerminated();

        ProtosActor replacement = new ProtosActor(prototype);
        ProtosActorRefValue replacementTransferred =
                replacement.reference().rematerializeForActorTransfer();

        assertFalse(ProtosIdentity.identical(oldTransferred, replacementTransferred));
        assertNotEquals(
                oldTransferred.incarnationIdentityForRuntime(),
                replacementTransferred.incarnationIdentityForRuntime());
        assertSame(originalActor, oldTransferred.localActorForRuntime());
        assertSame(replacement, replacementTransferred.localActorForRuntime());
    }

    @Test
    void actorRefDoesNotExposeTargetOrFutureMessagingSurfaceAsPublicJavaApi() {
        assertNoPublicMethodNamed("localActorForRuntime");
        assertNoPublicMethodNamed("incarnationIdentityForRuntime");
        assertNoPublicMethodNamed("rematerializeForActorTransfer");
        assertNoPublicMethodNamed("send");
        assertNoPublicMethodNamed("request");
        assertNoPublicMethodNamed("stop");
        assertNoPublicMethodNamed("termination");
    }

    private static void assertNoPublicMethodNamed(String name) {
        boolean found =
                Arrays.stream(ProtosActorRefValue.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals(name))
                        .anyMatch(method -> Modifier.isPublic(method.getModifiers()));
        assertFalse(found, "unexpected public ActorRef method: " + name);
    }

    private static ProtosObjectValue actorRefPrototype() {
        return new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze();
    }
}
