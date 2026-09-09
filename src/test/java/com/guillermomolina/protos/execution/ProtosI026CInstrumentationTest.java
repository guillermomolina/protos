/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.semantic.ast.CanonicalCall;
import com.guillermomolina.protos.semantic.ast.CanonicalLiteral;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.guillermomolina.protos.source.SourceSpan;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosI026CInstrumentationTest {
    private static final SourceSpan A = new SourceSpan(0, 1);
    private static final SourceSpan B = new SourceSpan(2, 3);
    private static final SourceSpan ALL = new SourceSpan(0, 3);

    @Test
    void directSequenceChildrenAreStatementsButContainerAndSubexpressionsAreNot() {
        CanonicalLiteral first = new CanonicalLiteral(CanonicalLiteral.Kind.NUMBER, "1", A);
        CanonicalLiteral receiver = new CanonicalLiteral(CanonicalLiteral.Kind.NUMBER, "2", B);
        CanonicalCall call = new CanonicalCall(receiver, List.of(), B);
        CanonicalSequence sequence = new CanonicalSequence(List.of(first, call), ALL);

        ProtosExpressionNode lowered = new CanonicalToTruffleLowerer().lower(sequence);
        List<ProtosExpressionNode> nodes = descendants(lowered);

        assertFalse(lowered.hasTag(StandardTags.StatementTag.class));
        assertFalse(lowered.hasTag(StandardTags.CallTag.class));
        assertTrue(nodes.stream().anyMatch(n -> n.span().equals(A) && n.hasTag(StandardTags.StatementTag.class)));
        ProtosExpressionNode callNode = nodes.stream()
                .filter(ProtosCallNode.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertTrue(callNode.hasTag(StandardTags.StatementTag.class));
        assertTrue(callNode.hasTag(StandardTags.CallTag.class));
        assertTrue(nodes.stream()
                .filter(n -> n.span().equals(B) && !(n instanceof ProtosCallNode))
                .noneMatch(n -> n.hasTag(StandardTags.StatementTag.class)));
    }

    @Test
    void generatedWrapperCopiesDerivedSourceAndTagMetadata() {
        ProtosExpressionNode guest = new DummyNode(A).markStatementTagForLowering();
        InstrumentableNode.WrapperNode wrapper = guest.createWrapper(null);
        ProtosExpressionNode wrapperNode = (ProtosExpressionNode) wrapper;

        assertSame(guest, wrapper.getDelegateNode());
        assertEquals(guest.span(), wrapperNode.span());
        assertTrue(wrapperNode.hasTag(StandardTags.StatementTag.class));
        assertFalse(wrapperNode.hasTag(StandardTags.CallTag.class));
    }

    @Test
    void replayIdentityRecursivelyIgnoresDerivedWrappers() {
        ProtosExpressionNode guest = new DummyNode(A);
        ProtosExpressionNode once = new TestWrapper(A, guest);
        ProtosExpressionNode twice = new TestWrapper(A, once);

        assertSame(guest, ProtosExpressionNode.replaySiteIdentity(guest));
        assertSame(guest, ProtosExpressionNode.replaySiteIdentity(once));
        assertSame(guest, ProtosExpressionNode.replaySiteIdentity(twice));
    }

    @Test
    void replayIdentityFailsClosedOnBrokenWrapper() {
        ProtosExpressionNode broken = new BrokenWrapper(A);
        assertThrows(IllegalStateException.class, () -> ProtosExpressionNode.replaySiteIdentity(broken));
    }

    @Test
    void baseNodeAddsOnlyCompactTagStateNotSourceOrReplaySiteObjects() {
        Field[] fields = ProtosExpressionNode.class.getDeclaredFields();
        assertTrue(java.util.Arrays.stream(fields).anyMatch(f -> f.getType() == byte.class && f.getName().equals("instrumentationTags")));
        assertFalse(java.util.Arrays.stream(fields).anyMatch(f -> f.getType().getSimpleName().contains("SourceSection")));
        assertFalse(java.util.Arrays.stream(fields).anyMatch(f -> f.getName().toLowerCase().contains("replaysite")));
    }

    private static List<ProtosExpressionNode> descendants(ProtosExpressionNode root) {
        java.util.ArrayList<ProtosExpressionNode> result = new java.util.ArrayList<>();
        root.accept(node -> {
            if (node instanceof ProtosExpressionNode expression) result.add(expression);
            return true;
        });
        return result;
    }

    private static class DummyNode extends ProtosExpressionNode {
        DummyNode(SourceSpan span) { super(span); }
        @Override protected Object executeDirect(VirtualFrame frame) { return null; }
    }

    private static final class TestWrapper extends ProtosExpressionNode implements InstrumentableNode.WrapperNode {
        @Child private ProtosExpressionNode delegate;
        TestWrapper(SourceSpan span, ProtosExpressionNode delegate) { super(span); this.delegate = delegate; }
        @Override public Node getDelegateNode() { return delegate; }
        @Override public ProbeNode getProbeNode() { return null; }
        @Override protected Object executeDirect(VirtualFrame frame) { return delegate.executeDirect(frame); }
    }

    private static final class BrokenWrapper extends ProtosExpressionNode implements InstrumentableNode.WrapperNode {
        BrokenWrapper(SourceSpan span) { super(span); }
        @Override public Node getDelegateNode() { return this; }
        @Override public ProbeNode getProbeNode() { return null; }
        @Override protected Object executeDirect(VirtualFrame frame) { return null; }
    }
}
