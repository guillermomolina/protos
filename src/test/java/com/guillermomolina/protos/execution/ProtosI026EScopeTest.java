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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtosI026EScopeTest {
    private static final SourceSpan SPAN = new SourceSpan(0, 1);

    private final NodeLibrary nodes = NodeLibrary.getUncached();
    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void nodeScopeUsesExactActivationFromFrameArgumentZero() throws Exception {
        ProtosStringValue value = new ProtosStringValue("current");
        ProtosObjectValue context = objectWith("current", value);
        ProtosActivation activation =
                new ProtosActivation(context, List.of(), ProtosObjectValue.rootObject());
        VirtualFrame frame = frame(activation);
        DummyNode node = adopted(new DummyNode());

        assertTrue(nodes.hasScope(node, frame));
        Object scope = nodes.getScope(node, frame, true);

        assertTrue(interop.isScope(scope));
        assertTrue(interop.hasMembers(scope));
        assertTrue(interop.hasLanguageId(scope));
        assertEquals(ProtosLanguage.ID, interop.getLanguageId(scope));
        assertSame(value, interop.readMember(scope, "current"));
        assertEquals("scope", interop.toDisplayString(scope, false));
    }

    @Test
    void flattenedNamesPreserveLookupPrecedenceAndShadowingDuplicates()
            throws Exception {
        ProtosStringValue currentShadow = new ProtosStringValue("current-shadow");
        ProtosStringValue lexicalOneShadow = new ProtosStringValue("lexical-one-shadow");
        ProtosStringValue lexicalTwoShadow = new ProtosStringValue("lexical-two-shadow");
        ProtosStringValue receiverShadow = new ProtosStringValue("receiver-shadow");
        ProtosStringValue parentShadow = new ProtosStringValue("parent-shadow");

        ProtosObjectValue receiverParent = new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiverParent.createLocalSlot("parentOnly", new ProtosStringValue("parent"));
        receiverParent.createLocalSlot("shadow", parentShadow);

        ProtosObjectValue receiver = new ProtosObjectValue(receiverParent);
        receiver.createLocalSlot("receiverOnly", new ProtosStringValue("receiver"));
        receiver.createLocalSlot("shadow", receiverShadow);

        ProtosObjectValue lexicalOne = new ProtosObjectValue(ProtosObjectValue.rootObject());
        lexicalOne.createLocalSlot("lexicalOneOnly", new ProtosStringValue("lexical-one"));
        lexicalOne.createLocalSlot("shadow", lexicalOneShadow);

        ProtosObjectValue lexicalTwo = new ProtosObjectValue(ProtosObjectValue.rootObject());
        lexicalTwo.createLocalSlot("lexicalTwoOnly", new ProtosStringValue("lexical-two"));
        lexicalTwo.createLocalSlot("shadow", lexicalTwoShadow);

        ProtosObjectValue context = new ProtosObjectValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("currentOnly", new ProtosStringValue("current"));
        context.createLocalSlot("shadow", currentShadow);

        ProtosActivation activation =
                new ProtosActivation(context, List.of(lexicalOne, lexicalTwo), receiver);
        Object scope = nodes.getScope(adopted(new DummyNode()), frame(activation), true);

        List<String> names = memberNames(scope);
        assertTrue(names.size() >= 10);
        assertEquals(
                List.of(
                        "currentOnly", "shadow",
                        "lexicalOneOnly", "shadow",
                        "lexicalTwoOnly", "shadow",
                        "receiverOnly", "shadow",
                        "parentOnly", "shadow"),
                names.subList(0, 10));

        assertSame(currentShadow, activation.lookup("shadow").orElseThrow());
        assertSame(currentShadow, interop.readMember(scope, "shadow"));
        assertEquals(
                "parent",
                interop.asString(interop.readMember(scope, "parentOnly")));
    }

    @Test
    void scopeReadUsesOrdinaryReceiverDelegationWithoutLexicalParentFiction()
            throws Exception {
        ProtosStringValue inherited = new ProtosStringValue("inherited");
        ProtosObjectValue receiverParent = new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiverParent.createLocalSlot("inherited", inherited);
        ProtosObjectValue receiver = new ProtosObjectValue(receiverParent);
        ProtosActivation activation =
                new ProtosActivation(
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        List.of(),
                        receiver);

        Object scope = nodes.getScope(adopted(new DummyNode()), frame(activation), false);

        assertSame(
                activation.lookup("inherited").orElseThrow(),
                interop.readMember(scope, "inherited"));
        assertFalse(interop.hasScopeParent(scope));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.getScopeParent(scope));
    }

    @Test
    void scopeIsReadOnlyAndInventsNoNamedReceiver() throws Exception {
        ProtosObjectValue context =
                objectWith("name", new ProtosStringValue("value"));
        ProtosActivation activation =
                new ProtosActivation(context, List.of(), ProtosObjectValue.rootObject());
        VirtualFrame frame = frame(activation);
        DummyNode node = adopted(new DummyNode());
        Object scope = nodes.getScope(node, frame, true);

        assertFalse(interop.isMemberModifiable(scope, "name"));
        assertFalse(interop.isMemberInsertable(scope, "newName"));
        assertFalse(interop.isMemberRemovable(scope, "name"));
        assertThrows(
                UnsupportedMessageException.class,
                () -> interop.writeMember(
                        scope, "name", new ProtosStringValue("replacement")));
        assertSame(
                activation.lookup("name").orElseThrow(),
                interop.readMember(scope, "name"));

        assertFalse(nodes.hasReceiverMember(node, frame));
        assertThrows(
                UnsupportedMessageException.class,
                () -> nodes.getReceiverMember(node, frame));
    }

    @Test
    void lexicalAccessWithoutARealActivationFrameFailsClosed() {
        DummyNode node = adopted(new DummyNode());

        assertFalse(nodes.hasScope(node, null));
        assertThrows(
                UnsupportedMessageException.class,
                () -> nodes.getScope(node, null, true));

        VirtualFrame hostOnly =
                Truffle.getRuntime()
                        .createVirtualFrame(
                                new Object[] {new Object()},
                                FrameDescriptor.newBuilder().build());
        assertFalse(nodes.hasScope(node, hostOnly));
        assertThrows(
                UnsupportedMessageException.class,
                () -> nodes.getScope(node, hostOnly, true));
    }

    @Test
    void generatedInstrumentationWrapperPreservesDelegateScopeBridge() throws Exception {
        DummyNode guest = new DummyNode();
        guest.markStatementTagForLowering();
        ProtosExpressionNode wrapper =
                (ProtosExpressionNode) guest.createWrapper(null);
        adoptRoot(wrapper);

        assertTrue(guest.getRootNode() instanceof ProtosRootNode);
        assertTrue(guest.isInstrumentable());

        ProtosStringValue value = new ProtosStringValue("wrapped");
        ProtosActivation activation =
                new ProtosActivation(
                        objectWith("wrapped", value),
                        List.of(),
                        ProtosObjectValue.rootObject());
        VirtualFrame frame = frame(activation);

        assertTrue(nodes.hasScope(guest, frame));
        Object scope = nodes.getScope(guest, frame, true);
        assertSame(value, interop.readMember(scope, "wrapped"));
    }

    @Test
    void accidentalHostOnlyValueNeverLeavesTheScope() throws Exception {
        ProtosObjectValue context = new ProtosObjectValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("hostOnly", new Object());
        ProtosActivation activation =
                new ProtosActivation(context, List.of(), ProtosObjectValue.rootObject());
        Object scope = nodes.getScope(adopted(new DummyNode()), frame(activation), true);

        assertTrue(memberNames(scope).contains("hostOnly"));
        assertFalse(interop.isMemberReadable(scope, "hostOnly"));
        assertThrows(
                UnknownIdentifierException.class,
                () -> interop.readMember(scope, "hostOnly"));
    }

    @Test
    void noArtificialLanguageTopScopeOrGlobalScopeRegistryIsIntroduced() {
        assertFalse(
                Arrays.stream(ProtosLanguage.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals("getScope")));

        assertEquals(
                1,
                Arrays.stream(ProtosDebuggerScope.class.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .count());
        assertTrue(
                Arrays.stream(ProtosDebuggerScope.class.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .allMatch(field -> field.getType() == ProtosActivation.class));
    }

    private static <T extends ProtosExpressionNode> T adopted(T node) {
        node.markStatementTagForLowering();
        adoptRoot(node);
        assertTrue(node.isInstrumentable());
        return node;
    }

    private static void adoptRoot(ProtosExpressionNode node) {
        Source source =
                Source.newBuilder(
                                ProtosLanguage.ID,
                                "x",
                                "i026-e1-scope-test.protos")
                        .build();
        new ProtosRootNode(null, source, node).getCallTarget();
        assertTrue(node.getRootNode() instanceof ProtosRootNode);
    }

    private VirtualFrame frame(ProtosActivation activation) {
        return Truffle.getRuntime()
                .createVirtualFrame(
                        new Object[] {activation},
                        FrameDescriptor.newBuilder().build());
    }

    private List<String> memberNames(Object scope) throws Exception {
        Object members = interop.getMembers(scope);
        long size = interop.getArraySize(members);
        ArrayList<String> result = new ArrayList<>((int) size);
        for (long i = 0; i < size; i++) {
            result.add(interop.asString(interop.readArrayElement(members, i)));
        }
        return List.copyOf(result);
    }

    private static ProtosObjectValue objectWith(String name, Object value) {
        ProtosObjectValue object = new ProtosObjectValue(ProtosObjectValue.rootObject());
        object.createLocalSlot(name, value);
        return object;
    }

    private static final class DummyNode extends ProtosExpressionNode {
        DummyNode() {
            super(SPAN);
        }

        @Override
        protected Object executeDirect(VirtualFrame frame) {
            return null;
        }
    }
}
