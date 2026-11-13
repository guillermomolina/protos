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

import com.guillermomolina.protos.runtime.diagnostics.ProtosDiagnosticTrace;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI008-C1 sole projection authority from a live terminal occurrence to an inert
 * {@link ProtosDiagnosticTrace} (PLAT049 Candidate C).
 *
 * <p>Nothing here runs on a successful path. The only always-present hook is {@link
 * #recordOrigin}, reached from semantic-root exception interception, i.e. only while an Error
 * transfer is already unwinding. Projection itself runs once, at the terminal boundary, while
 * the occurrence throwable is still available:
 *
 * <ol>
 *   <li>The Truffle guest stack of the throwable lists every physical frame it unwound, innermost
 *       first. {@link TruffleStackTrace} also appends the frames that are physically live at the
 *       capture point; those belong to whoever hosts the terminal boundary (possibly another
 *       Task's guest code) and are cut off by count, so no foreign frame is ever attributed to
 *       this occurrence.
 *   <li>Only {@link ProtosBytecodeRootNode} roots are guest frames. A generated {@link
 *       ContinuationRootNode} normalizes to its semantic source root at the continuation's current
 *       bytecode index (PERF006-B5 location stability), so a resumed activation is projected as
 *       the same semantic frame, never as an artificial continuation frame. Structured-dispatch /
 *       C-prime auxiliary roots (not semantic activation roots), host and foreign roots are
 *       skipped.
 *   <li>A frame without a location (the innermost frame: Protos exceptions carry no location node)
 *       uses the position {@link #recordOrigin} stored on the occurrence.
 *   <li>Inline callbacks have no physical frame. On the error path, the root is reparsed
 *       with source and tag information. Every nested semantic RootTag active at the failing
 *       index contributes one callback frame before the physical root, innermost first.
 *       The automatic physical RootTag and RootBodyTag describe one activation. No inline
 *       activation, scope or binding is materialized.
 *   <li>I112 / PLAT062-T: a caller whose terminal invocation suspended returned its transport,
 *       so after resumption it is no longer a physical frame. When the occurrence crossed such a
 *       resumed transport, {@link #recordResumedTerminalCaller} stored the caller's logical
 *       location and the physical depth at that crossing; the caller is projected as one semantic
 *       frame at exactly that position among the unwound frames, innermost first. It is a
 *       diagnostic frame only: no Truffle frame, activation or continuation is fabricated.
 * </ol>
 *
 * <p>Source information is materialized lazily, only on this failure path, through the public
 * {@link com.oracle.truffle.api.bytecode.BytecodeRootNodes} reparse that the lowerer explicitly
 * supports. Truffle frames, locations and nodes are used transiently and never escape.
 */
final class ProtosDiagnosticTraceCapture {
    /*
     * Error-only reparse exposing semantic roots and their actual invocation sites.
     * Automatic RootBodyTags describe physical bodies, never additional activations.
     */
    private static final BytecodeConfig INLINE_CALLBACK_DIAGNOSTIC_CONFIG =
            ProtosBytecodeRootNodeGen.newConfigBuilder()
                    .addSource()
                    .addTag(StandardTags.RootTag.class)
                    .addTag(StandardTags.RootBodyTag.class)
                    .addTag(StandardTags.CallTag.class)
                    .build();

    private ProtosDiagnosticTraceCapture() {}

    /**
     * Records the innermost semantic bytecode position of an Error transfer. Called from semantic
     * root exception interception for every crossing; only the first crossing is kept.
     * Deliberately a plain inlinable field update, not a boundary call.
     */
    static void recordOrigin(
            AbstractTruffleException exception,
            Frame frame,
            BytecodeNode bytecodeNode,
            int bytecodeIndex) {
        if (exception instanceof ProtosSignalException signal) {
            signal.recordOriginIfAbsentForRuntime(bytecodeNode, bytecodeIndex);
            /*
             * An uncached frame's stack-trace bytecode index is read lazily from a frame slot
             * that ensure/finally cleanup in the same frame can overwrite during unwinding. The
             * tier check folds in compiled code, which never runs uncached bytecode.
             */
            if (bytecodeNode.getTier() == BytecodeTier.UNCACHED) {
                signal.recordInterpreterPositionIfAbsentForRuntime(
                        frame, bytecodeNode, bytecodeIndex);
            }
        }
    }

    /**
     * I112: records, while {@code exception} crosses a resumed terminal transport, the logical
     * location of the caller that already returned the transport. Failure path only.
     */
    @TruffleBoundary
    static void recordResumedTerminalCaller(
            AbstractTruffleException exception, BytecodeLocation caller) {
        if (caller != null && exception instanceof ProtosSignalException signal) {
            signal.recordResumedTerminalCallerForRuntime(caller, livePhysicalFrameCount());
        }
    }

    /**
     * Projects the semantic guest frames of {@code occurrence} into an inert trace.
     *
     * <p>Diagnostics must never change the semantic outcome they describe: a projection that
     * fails for an implementation reason yields {@code null} (no trace) instead of replacing the
     * terminal Error with a host failure.
     */
    @TruffleBoundary
    static ProtosDiagnosticTrace capture(AbstractTruffleException occurrence) {
        if (occurrence == null) {
            return null;
        }
        try {
            return project(occurrence);
        } catch (RuntimeException projectionFailure) {
            return null;
        }
    }

    private static ProtosDiagnosticTrace project(AbstractTruffleException occurrence) {
        int hostingFrames = livePhysicalFrameCount();
        List<TruffleStackTraceElement> elements = TruffleStackTrace.getStackTrace(occurrence);
        int occurrenceFrames = elements == null ? 0 : Math.max(0, elements.size() - hostingFrames);

        Collector collector = new Collector();
        Origin origin = Origin.of(occurrence);
        ProtosSignalException signal =
                occurrence instanceof ProtosSignalException candidate ? candidate : null;
        int logicalCallers = signal == null ? 0 : signal.resumedTerminalCallerCountForRuntime();
        int nextLogical = 0;
        for (int index = 0; index < occurrenceFrames && !collector.full(); index++) {
            while (nextLogical < logicalCallers
                    && logicalPosition(signal, nextLogical, occurrenceFrames, hostingFrames)
                            <= index) {
                projectLogicalCaller(signal.resumedTerminalCallerForRuntime(nextLogical++), collector);
            }
            TruffleStackTraceElement element = elements.get(index);
            ProtosBytecodeRootNode semantic = semanticRoot(element.getTarget().getRootNode());
            if (semantic == null) {
                continue;
            }
            BytecodeLocation location = interpreterPosition(occurrence, element);
            if (location == null) {
                location = elementLocation(element);
            }
            if (location == null && origin != null && origin.belongsTo(semantic)) {
                location = origin.location();
            }
            origin = null;
            projectSemanticFrame(semantic, location, collector);
        }
        while (nextLogical < logicalCallers && !collector.full()) {
            projectLogicalCaller(signal.resumedTerminalCallerForRuntime(nextLogical++), collector);
        }
        return collector.finish();
    }

    /*
     * The physical frames unwound after the crossing are those live then and no longer live at
     * this capture; the logical caller sits just before them.
     */
    private static int logicalPosition(
            ProtosSignalException signal, int index, int occurrenceFrames, int hostingFrames) {
        int unwoundAfter =
                Math.max(0, signal.resumedTerminalCallerLiveFramesForRuntime(index) - hostingFrames);
        return Math.max(0, occurrenceFrames - unwoundAfter);
    }

    private static void projectLogicalCaller(BytecodeLocation caller, Collector collector) {
        if (caller.getBytecodeNode().getBytecodeRootNode()
                        instanceof ProtosBytecodeRootNode semantic
                && semantic.isSemanticActivationRoot()) {
            projectSemanticFrame(semantic, caller, collector);
        }
    }

    private static int livePhysicalFrameCount() {
        int[] count = {0};
        Truffle.getRuntime()
                .iterateFrames(
                        frame -> {
                            count[0]++;
                            return null;
                        });
        return count[0];
    }

    /*
     * I111-B: auxiliary structured/C-prime roots of the same generated interpreter are
     * infrastructure, never guest frames; only a marked semantic activation root projects.
     */
    private static ProtosBytecodeRootNode semanticRoot(RootNode root) {
        Object source = root instanceof ContinuationRootNode continuation
                ? continuation.getSourceRootNode()
                : root;
        return source instanceof ProtosBytecodeRootNode semantic
                        && semantic.isSemanticActivationRoot()
                ? semantic
                : null;
    }

    /**
     * The exact position recorded for an uncached-interpreter frame. A continuation root executes
     * on its parent frame, passed as its first argument, which is the frame the semantic root
     * intercepted.
     */
    private static BytecodeLocation interpreterPosition(
            AbstractTruffleException occurrence,
            TruffleStackTraceElement element) {
        if (!(occurrence instanceof ProtosSignalException signal)) {
            return null;
        }
        Frame frame = element.getFrame();
        if (frame == null) {
            return null;
        }
        BytecodeLocation recorded = signal.interpreterPositionForRuntime(frame);
        if (recorded == null
                && element.getTarget().getRootNode() instanceof ContinuationRootNode
                && frame.getArguments().length > 0
                && frame.getArguments()[0] instanceof Frame parent) {
            recorded = signal.interpreterPositionForRuntime(parent);
        }
        return recorded;
    }

    private static BytecodeLocation elementLocation(TruffleStackTraceElement element) {
        if (element.getLocation() == null || !element.hasBytecodeIndex()) {
            return null;
        }
        return BytecodeLocation.get(element);
    }

    private static void projectSemanticFrame(
            ProtosBytecodeRootNode semantic,
            BytecodeLocation location,
            Collector collector) {
        String label = semantic.getName();
        if (location == null) {
            semantic.getRootNodes().ensureSourceInformation();
            collector.add(frame(label, semantic.getSourceSection(), null));
            return;
        }

        // Root-scoped locals do not describe semantic callback lifetimes. On the
        // error path the instrumented tag tree supplies the active regions instead.
        semantic.getRootNodes().update(INLINE_CALLBACK_DIAGNOSTIC_CONFIG);
        BytecodeLocation current = location.update();
        BytecodeNode bytecode = current.getBytecodeNode();
        int bytecodeIndex = current.getBytecodeIndex();
        SourceSection position = current.getSourceLocation();

        List<TagTree> regions = activeNestedRootTags(bytecode.getTagTree(), bytecodeIndex);
        for (int index = regions.size() - 1; index >= 0 && !collector.full(); index--) {
            TagTree region = regions.get(index);
            collector.add(frame(null, region.getSourceSection(), position));
            position = enclosingCallSection(bytecode.getTagTree(), region, null);
        }
        collector.add(frame(label, semantic.getSourceSection(), position));
    }

    /**
     * Nested semantic RootTag regions active at {@code bytecodeIndex},
     * outermost first. The first active RootTag identifies the physical
     * semantic root and is projected separately by projectSemanticFrame.
     */
    private static List<TagTree> activeNestedRootTags(
            TagTree tagTree, int bytecodeIndex) {
        List<TagTree> regions = new ArrayList<>();
        if (tagTree == null) {
            return regions;
        }

        TagTree current = tagTree;
        boolean physicalRootSeen =
                current.hasTag(StandardTags.RootTag.class);

        boolean descended = true;
        while (descended) {
            descended = false;
            for (TagTree child : current.getTreeChildren()) {
                if (child.getEnterBytecodeIndex() < child.getReturnBytecodeIndex()
                        && child.getEnterBytecodeIndex() <= bytecodeIndex
                        && bytecodeIndex <= child.getReturnBytecodeIndex()) {
                    if (child.hasTag(StandardTags.RootTag.class)) {
                        if (physicalRootSeen) {
                            regions.add(child);
                        } else {
                            physicalRootSeen = true;
                        }
                    }
                    current = child;
                    descended = true;
                    break;
                }
            }
        }
        return regions;
    }

    /** The real tagged invocation enclosing this callback, rather than another statement. */
    private static SourceSection enclosingCallSection(
            TagTree tree, TagTree region, SourceSection caller) {
        if (tree == region) {
            return caller;
        }
        SourceSection current = tree.hasTag(StandardTags.CallTag.class)
                ? tree.getSourceSection() : caller;
        for (TagTree child : tree.getTreeChildren()) {
            if (child.getEnterBytecodeIndex() <= region.getEnterBytecodeIndex()
                    && region.getReturnBytecodeIndex() <= child.getReturnBytecodeIndex()) {
                SourceSection found = enclosingCallSection(child, region, current);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Builds one inert frame. {@code authority} supplies the Source identity of the activation;
     * {@code position}, when available, its current line/column.
     */
    private static ProtosDiagnosticTrace.Frame frame(
            String label,
            SourceSection authority,
            SourceSection position) {
        SourceSection located =
                position != null && position.isAvailable() && position.hasLines()
                        ? position
                        : authority;
        if (located == null || !located.isAvailable() || !located.hasLines()) {
            return null;
        }
        Source source = (authority != null ? authority : located).getSource();
        int column = located.hasColumns() ? located.getStartColumn() : 1;
        return new ProtosDiagnosticTrace.Frame(
                label,
                source.getName(),
                source.getPath(),
                located.getStartLine(),
                column);
    }

    /** The innermost semantic position recorded on the occurrence by {@link #recordOrigin}. */
    private record Origin(BytecodeNode bytecode, int bytecodeIndex) {
        static Origin of(AbstractTruffleException occurrence) {
            if (occurrence instanceof ProtosSignalException signal
                    && signal.originBytecodeForRuntime() != null) {
                return new Origin(
                        signal.originBytecodeForRuntime(),
                        signal.originBytecodeIndexForRuntime());
            }
            return null;
        }

        boolean belongsTo(ProtosBytecodeRootNode semantic) {
            BytecodeRootNode owner = bytecode.getBytecodeRootNode();
            return owner == semantic;
        }

        BytecodeLocation location() {
            return bytecode.getBytecodeLocation(bytecodeIndex);
        }
    }

    /** Bounded innermost-first accumulator; keeps the innermost {@code MAX_FRAMES} frames. */
    private static final class Collector {
        private final List<ProtosDiagnosticTrace.Frame> frames = new ArrayList<>();
        private boolean truncated;

        boolean full() {
            return truncated;
        }

        void add(ProtosDiagnosticTrace.Frame frame) {
            if (frame == null || truncated) {
                return;
            }
            if (frames.size() == ProtosDiagnosticTrace.MAX_FRAMES) {
                truncated = true;
                return;
            }
            frames.add(frame);
        }

        ProtosDiagnosticTrace finish() {
            return new ProtosDiagnosticTrace(frames, truncated);
        }
    }
}
