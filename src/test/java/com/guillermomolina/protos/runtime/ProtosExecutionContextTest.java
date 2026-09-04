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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosActivationTest {
    @Test
    void lookupPrefersCurrentContextThenCapturedLexicalContextsThenReceiver() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiverParent = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(receiverParent);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        ProtosObjectValue inner = new ProtosObjectValue(root);
        ProtosObjectValue current = new ProtosObjectValue(root);

        Object receiverValue = new ProtosStringValue("receiver");
        Object outerValue = new ProtosStringValue("outer");
        Object innerValue = new ProtosStringValue("inner");
        Object currentValue = new ProtosStringValue("current");

        receiver.createLocalSlot("name", receiverValue);
        outer.createLocalSlot("name", outerValue);
        inner.createLocalSlot("name", innerValue);
        current.createLocalSlot("name", currentValue);

        ProtosActivation activation =
                new ProtosActivation(current, List.of(inner, outer), receiver);

        assertSame(currentValue, activation.lookup("name").orElseThrow());

        current.assignLocalSlot("name", ProtosNullValue.INSTANCE);
        assertSame(ProtosNullValue.INSTANCE, activation.lookup("name").orElseThrow());
    }

    @Test
    void capturedLexicalContextsShadowReceiverAndPreserveNearestFirstOrder() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        ProtosObjectValue inner = new ProtosObjectValue(root);
        ProtosObjectValue current = new ProtosObjectValue(root);

        Object receiverValue = new ProtosStringValue("receiver");
        Object outerValue = new ProtosStringValue("outer");
        Object innerValue = new ProtosStringValue("inner");

        receiver.createLocalSlot("state", receiverValue);
        outer.createLocalSlot("state", outerValue);
        inner.createLocalSlot("state", innerValue);

        ProtosActivation activation =
                new ProtosActivation(current, List.of(inner, outer), receiver);

        assertSame(innerValue, activation.lookup("state").orElseThrow());
    }

    @Test
    void receiverLookupDelegatesAfterLexicalContextsAreExhausted() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        ProtosObjectValue current = new ProtosObjectValue(root);

        Object inherited = new ProtosStringValue("inherited");
        prototype.createLocalSlot("name", inherited);

        ProtosActivation activation =
                new ProtosActivation(current, List.of(), receiver);

        assertSame(inherited, activation.lookup("name").orElseThrow());
    }

    @Test
    void objectConstructionUsesObjectAsContextWithoutCapturingItLexically() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue enclosingContext = new ProtosObjectValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        ProtosObjectValue enclosingReceiver = new ProtosObjectValue(root);
        ProtosActivation enclosing =
                new ProtosActivation(
                        enclosingContext,
                        List.of(outer),
                        enclosingReceiver);
        ProtosObjectValue object = new ProtosObjectValue(root);

        ProtosActivation construction =
                ProtosActivation.forObjectConstruction(object, enclosing);

        assertSame(object, construction.context());
        assertSame(object, construction.receiver());
        assertSame(
                enclosingContext,
                construction.lexicalContextsForClosureCapture().get(0));
        assertSame(
                outer,
                construction.lexicalContextsForClosureCapture().get(1));
        assertTrue(
                construction.lexicalContextsForClosureCapture().stream()
                        .noneMatch(candidate -> candidate == object));
    }

    @Test
    void nestedConstructionSkipsEveryConstructionObjectForClosureCapture() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue lexical = new ProtosObjectValue(root);
        ProtosActivation enclosing =
                new ProtosActivation(
                        lexical,
                        List.of(),
                        new ProtosObjectValue(root));
        ProtosObjectValue outerObject = new ProtosObjectValue(root);
        ProtosObjectValue innerObject = new ProtosObjectValue(root);

        ProtosActivation outerConstruction =
                ProtosActivation.forObjectConstruction(
                        outerObject,
                        enclosing);
        ProtosActivation innerConstruction =
                ProtosActivation.forObjectConstruction(
                        innerObject,
                        outerConstruction);

        assertSame(
                lexical,
                innerConstruction.lexicalContextsForClosureCapture().get(0));
        assertTrue(
                innerConstruction.lexicalContextsForClosureCapture().stream()
                        .noneMatch(candidate ->
                                candidate == outerObject || candidate == innerObject));
    }

    @Test
    void writableLookupNeverTraversesReceiverDelegationParents() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        ProtosObjectValue lexical = new ProtosObjectValue(root);
        ProtosObjectValue current = new ProtosObjectValue(root);

        prototype.createLocalSlot("inherited", ProtosBooleanValue.TRUE);
        receiver.createLocalSlot("receiverLocal", ProtosBooleanValue.TRUE);
        lexical.createLocalSlot("captured", ProtosBooleanValue.TRUE);
        current.createLocalSlot("local", ProtosBooleanValue.TRUE);

        ProtosActivation activation =
                new ProtosActivation(current, List.of(lexical), receiver);

        assertSame(current, activation.writableLexicalContext("local").orElseThrow());
        assertSame(lexical, activation.writableLexicalContext("captured").orElseThrow());
        assertSame(receiver, activation.writableLexicalContext("receiverLocal").orElseThrow());
        assertTrue(activation.writableLexicalContext("inherited").isEmpty());
        assertTrue(activation.writableLexicalContext("missing").isEmpty());
    }
}
