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
 * the specific language governing rights and limitations under the LICENSE.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosPerf006B5ABytecodeDebuggerScopeTest {
    private final InteropLibrary interop = InteropLibrary.getUncached();

    @Test
    void bytecodeTagTreeScopeUsesExactActivationLookupAuthority() throws Exception {
        ProtosStringValue currentShadow = new ProtosStringValue("current");
        ProtosStringValue lexicalShadow = new ProtosStringValue("lexical");
        ProtosStringValue receiverShadow = new ProtosStringValue("receiver");
        ProtosStringValue lexicalOnly = new ProtosStringValue("lexical-only");
        ProtosStringValue receiverOnly = new ProtosStringValue("receiver-only");

        ProtosObjectValue context = new ProtosObjectValue(ProtosObjectValue.rootObject());
        context.createLocalSlot("shadow", currentShadow);
        context.createLocalSlot("currentOnly", new ProtosStringValue("current-only"));

        ProtosObjectValue lexical = new ProtosObjectValue(ProtosObjectValue.rootObject());
        lexical.createLocalSlot("shadow", lexicalShadow);
        lexical.createLocalSlot("lexicalOnly", lexicalOnly);

        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiver.createLocalSlot("shadow", receiverShadow);
        receiver.createLocalSlot("receiverOnly", receiverOnly);

        ProtosActivation activation = new ProtosActivation(context, List.of(lexical), receiver);
        VirtualFrame frame = frame(activation);

        assertTrue(ProtosBytecodeTagTreeNodeExports.hasScope(null, frame));
        Object scope = ProtosBytecodeTagTreeNodeExports.getScope(null, frame, true);

        assertTrue(interop.isScope(scope));
        assertSame(currentShadow, interop.readMember(scope, "shadow"));
        assertSame(lexicalOnly, interop.readMember(scope, "lexicalOnly"));
        assertSame(receiverOnly, interop.readMember(scope, "receiverOnly"));
        assertSame(activation.lookup("shadow").orElseThrow(), interop.readMember(scope, "shadow"));

        List<String> names = memberNames(scope);
        assertTrue(names.contains("currentOnly"));
        assertTrue(names.contains("lexicalOnly"));
        assertTrue(names.contains("receiverOnly"));
        assertFalse(names.contains("sequenceResult"));
        assertFalse(names.contains("preparedClosureCall"));
        assertFalse(names.contains("childResult"));
        assertFalse(names.contains("resumeValue"));
        assertFalse(names.contains("structuredEnsureCall"));
        assertFalse(names.contains("structuredWhileCall"));

        System.out.println("PERF006_B5A_BYTECODE_SCOPE_AUTHORITY=PROTOS_ACTIVATION");
        System.out.println("PERF006_B5A_BYTECODE_INTERNAL_LOCALS_EXPOSED=NO");
        System.out.println("PERF006_B5A_SCOPE_LOOKUP_PARITY=PASS");
    }

    @Test
    void bytecodeTagTreeScopeFailsClosedWithoutActivation() {
        VirtualFrame frame =
                Truffle.getRuntime()
                        .createVirtualFrame(
                                new Object[] {new Object()},
                                FrameDescriptor.newBuilder().build());

        assertFalse(ProtosBytecodeTagTreeNodeExports.hasScope(null, null));
        assertFalse(ProtosBytecodeTagTreeNodeExports.hasScope(null, frame));
        assertThrows(
                UnsupportedMessageException.class,
                () -> ProtosBytecodeTagTreeNodeExports.getScope(null, frame, true));

        System.out.println("PERF006_B5A_SCOPE_WITHOUT_ACTIVATION=FAIL_CLOSED");
    }

    @Test
    void generatedBytecodeRootSelectsTheActivationScopeLibrary() throws Exception {
        Path rootPath =
                Path.of(
                        "src", "main", "java", "com", "guillermomolina", "protos",
                        "execution", "ProtosBytecodeRootNode.java");
        String root = Files.readString(rootPath);

        assertTrue(root.contains("enableTagInstrumentation = true"));
        assertTrue(
                root.contains(
                        "tagTreeNodeLibrary = ProtosBytecodeTagTreeNodeExports.class"));
        assertTrue(root.contains("enableRootTagging = false"));
        assertTrue(root.contains("enableRootBodyTagging = false"));

        System.out.println("PERF006_B5A_BYTECODE_TAG_TREE_SCOPE_LIBRARY=YES");
        System.out.println("PERF006_B5A_ROOT_TAG_EXPANSION=NO");
    }

    private static VirtualFrame frame(ProtosActivation activation) {
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
}
