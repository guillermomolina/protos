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

package com.guillermomolina.protos.runtime.contexts;

import com.guillermomolina.protos.runtime.ProtosExecutionContextValue;
import com.guillermomolina.protos.runtime.ProtosLexicalBindingAuthority;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * D179 candidate C0: execution-context local-slot membership follows ordinary
 * structural Object semantics, including PRESENT -> ABSENT removal while OPEN.
 * These tests exercise {@link ProtosExecutionContextValue} directly at the
 * runtime-object level; {@code protos/tests/conformance/execution-context/}
 * exercises the same boundary from guest Protos programs.
 */
class ProtosExecutionContextValueTest {
    @Test
    void openContextStillAllowsAbsentToPresentCreation() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());

        assertTrue(context.isOpen());
        context.createLocalSlot("x", "value");
        assertTrue(context.hasLocalSlot("x"));
    }

    @Test
    void contextStillAllowsPresentToPresentValueMutation() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("x", "first");

        context.assignLocalSlot("x", "second");

        assertSame("second", context.readLocalSlot("x").orElseThrow());
    }

    @Test
    void openContextAllowsRemovalAndRecreationOfAPresentLocalSlot() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("x", "value");

        assertSame("value", context.removeLocalSlot("x"));
        assertTrue(!context.hasLocalSlot("x"));
        assertTrue(context.readLocalSlot("x").isEmpty());

        context.createLocalSlot("x", "recreated");
        assertTrue(context.hasLocalSlot("x"));
        assertSame("recreated", context.readLocalSlot("x").orElseThrow());
    }

    @Test
    void closedAndFrozenContextsAlsoRejectRemovalOfAPresentLocalSlot() {
        ProtosExecutionContextValue closed =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        closed.createLocalSlot("x", "value");
        closed.close();
        assertThrows(IllegalStateException.class, () -> closed.removeLocalSlot("x"));
        assertTrue(closed.hasLocalSlot("x"));

        ProtosExecutionContextValue frozen =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        frozen.createLocalSlot("x", "value");
        frozen.freeze();
        assertThrows(IllegalStateException.class, () -> frozen.removeLocalSlot("x"));
        assertTrue(frozen.hasLocalSlot("x"));
    }

    @Test
    void removingAMissingLocalSlotStillSignalsTheOrdinaryMissingSlotFailure() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());

        assertThrows(IllegalStateException.class, () -> context.removeLocalSlot("absent"));
    }

    @Test
    void ordinaryObjectValueRemovalIsUnaffectedByTheExecutionContextBoundary() {
        ProtosObjectValue ordinary = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ordinary.createLocalSlot("x", "value");

        assertSame("value", ordinary.removeLocalSlot("x"));
        assertTrue(!ordinary.hasLocalSlot("x"));
    }

    // ---- I115-S1c-L one active lexical authority -------------------------------

    @Test
    void frameAuthorityHandoffKeepsIdentityAndExistingBindings() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("dynamic", "value");
        ProtosLexicalBindingAuthority frame = ProtosLexicalBindingAuthority.newMapBackedForRuntime();

        context.installFrameLexicalBindingAuthority(frame);

        assertSame(frame, context.lexicalBindingAuthorityForRuntime());
        assertSame("value", context.readLocalSlot("dynamic").orElseThrow());
        assertSame("value", frame.readBinding("dynamic").orElseThrow());
    }

    @Test
    void installedFrameAuthorityIsTheAuthoritativeStoreNotASynchronizedCopy() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        ProtosLexicalBindingAuthority previous = context.lexicalBindingAuthorityForRuntime();
        ProtosLexicalBindingAuthority frame = ProtosLexicalBindingAuthority.newMapBackedForRuntime();
        context.installFrameLexicalBindingAuthority(frame);

        frame.putBinding("x", "physical");
        assertSame("physical", context.readLocalSlot("x").orElseThrow());
        context.assignLocalSlot("x", "assigned");
        assertSame("assigned", frame.readBinding("x").orElseThrow());
        assertFalse(previous.containsBinding("x"));
    }

    @Test
    void frameAuthorityInstallationIsIdempotentAndRejectsAnotherOwner() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        ProtosLexicalBindingAuthority frame = ProtosLexicalBindingAuthority.newMapBackedForRuntime();
        context.installFrameLexicalBindingAuthority(frame);

        context.installFrameLexicalBindingAuthority(frame);
        assertSame(frame, context.lexicalBindingAuthorityForRuntime());
        assertThrows(
                IllegalStateException.class,
                () -> context.installFrameLexicalBindingAuthority(
                        ProtosLexicalBindingAuthority.newMapBackedForRuntime()));
        assertSame(frame, context.lexicalBindingAuthorityForRuntime());
    }

    @Test
    void openClosedAndFrozenRulesAreUnchangedAfterHandoff() {
        ProtosExecutionContextValue context =
                new ProtosExecutionContextValue(ProtosObjectValue.rootObject());
        context.installFrameLexicalBindingAuthority(ProtosLexicalBindingAuthority.newMapBackedForRuntime());
        context.createLocalSlot("x", "value");
        context.close();
        context.assignLocalSlot("x", "second");
        assertThrows(IllegalStateException.class, () -> context.createLocalSlot("y", "late"));
        assertThrows(IllegalStateException.class, () -> context.removeLocalSlot("x"));
        context.freeze();
        assertThrows(IllegalStateException.class, () -> context.assignLocalSlot("x", "third"));
        assertSame("second", context.readLocalSlot("x").orElseThrow());
    }
}
