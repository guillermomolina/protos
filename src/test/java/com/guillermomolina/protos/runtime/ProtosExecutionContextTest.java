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

class ProtosExecutionContextTest {
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

        ProtosExecutionContext activation =
                new ProtosExecutionContext(current, List.of(inner, outer), receiver);

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

        ProtosExecutionContext activation =
                new ProtosExecutionContext(current, List.of(inner, outer), receiver);

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

        ProtosExecutionContext activation =
                new ProtosExecutionContext(current, List.of(), receiver);

        assertSame(inherited, activation.lookup("name").orElseThrow());
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

        ProtosExecutionContext activation =
                new ProtosExecutionContext(current, List.of(lexical), receiver);

        assertSame(current, activation.writableLexicalContext("local").orElseThrow());
        assertSame(lexical, activation.writableLexicalContext("captured").orElseThrow());
        assertSame(receiver, activation.writableLexicalContext("receiverLocal").orElseThrow());
        assertTrue(activation.writableLexicalContext("inherited").isEmpty());
        assertTrue(activation.writableLexicalContext("missing").isEmpty());
    }
}
