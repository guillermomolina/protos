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

import com.guillermomolina.protos.runtime.ProtosDiagnosticTrace;
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
 *   <li>Only {@link ProtosSemanticBytecodeRootNode} roots are guest frames. A generated {@link
 *       ContinuationRootNode} normalizes to its semantic source root at the continuation's current
 *       bytecode index (PERF006-B5 location stability), so a resumed activation is projected as
 *       the same semantic frame, never as an artificial continuation frame. Structured-dispatch /
 *       C-prime ({@link ProtosBytecodeRootNode}), host and foreign roots are skipped.
 *   <li>A frame without a location (the innermost frame: Protos exceptions carry no location node)
 *       uses the position {@link #recordOrigin} stored on the occurrence.
 *   <li>PLAT044 B′ inline callbacks have no physical frame. When an inline callback carrier is
 *       live at the frame's bytecode index, the root is reparsed with source information and
 *       {@link StandardTags.RootTag} only, and every nested semantic RootTag active at that index
 *       contributes one callback frame (innermost first) before the enclosing physical frame. The
 *       automatic root tag is the physical frame itself and is never duplicated. No inline
 *       activation, scope or binding is materialized.
 * </ol>
 *
 * <p>Source information is materialized lazily, only on this failure path, through the public
 * {@link com.oracle.truffle.api.bytecode.BytecodeRootNodes} reparse that the lowerer explicitly
 * supports. Truffle frames, locations and nodes are used transiently and never escape.
 */
final class ProtosDiagnosticTraceCapture {
    /*
     * Lazy source sections plus only the RootTag: the smallest reparse that exposes PLAT044
     * nested semantic roots. It is requested only for a root whose failing position lies in a
     * live inline callback region.
     */
    private static final BytecodeConfig INLINE_CALLBACK_DIAGNOSTIC_CONFIG =
            ProtosSemanticBytecodeRootNodeGen.newConfigBuilder()
                    .addSource()
                    .addTag(StandardTags.RootTag.class)
                    .build();

    private ProtosDiagnosticTraceCapture() {}

    /**
     * Records the innermost semantic bytecode position of an Error transfer. Called from semantic
     * root exception interception for every crossing; only the first crossing is kept. A bridged
     * control transfer records its position when the semantic root creates the bridge instead.
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
        for (int index = 0; index < occurrenceFrames && !collector.full(); index++) {
            TruffleStackTraceElement element = elements.get(index);
            ProtosSemanticBytecodeRootNode semantic = semanticRoot(element.getTarget().getRootNode());
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
        return collector.finish();
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

    private static ProtosSemanticBytecodeRootNode semanticRoot(RootNode root) {
        Object source = root instanceof ContinuationRootNode continuation
                ? continuation.getSourceRootNode()
                : root;
        return source instanceof ProtosSemanticBytecodeRootNode semantic ? semantic : null;
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
            ProtosSemanticBytecodeRootNode semantic,
            BytecodeLocation location,
            Collector collector) {
        String label = semantic.getName();
        if (location == null) {
            semantic.getRootNodes().ensureSourceInformation();
            collector.add(frame(label, semantic.getSourceSection(), null));
            return;
        }

        boolean inlineRegionLive =
                hasLiveInlineCallbackCarrier(location.getBytecodeNode(), location.getBytecodeIndex());
        if (inlineRegionLive) {
            semantic.getRootNodes().update(INLINE_CALLBACK_DIAGNOSTIC_CONFIG);
        } else {
            semantic.getRootNodes().ensureSourceInformation();
        }
        BytecodeLocation current = location.update();
        BytecodeNode bytecode = current.getBytecodeNode();
        int bytecodeIndex = current.getBytecodeIndex();
        SourceSection position = current.getSourceLocation();

        if (inlineRegionLive) {
            List<TagTree> regions = activeNestedRootTags(bytecode.getTagTree(), bytecodeIndex);
            for (int index = regions.size() - 1; index >= 0 && !collector.full(); index--) {
                TagTree region = regions.get(index);
                // PLAT044 inline callbacks are anonymous Closure literals: no label exists.
                collector.add(frame(null, region.getSourceSection(), position));
                position = enclosingSection(bytecode, region);
            }
        }
        collector.add(frame(label, semantic.getSourceSection(), position));
    }

    /**
     * Whether {@code bytecodeIndex} lies in a PLAT044 inline callback region. The region's
     * carrier local is block-scoped to the region, so this is a cheap, reparse-free gate on the
     * local table; the TagTree remains the authority for the regions themselves.
     */
    private static boolean hasLiveInlineCallbackCarrier(BytecodeNode bytecode, int bytecodeIndex) {
        for (Object name : bytecode.getLocalNames(bytecodeIndex)) {
            if (CanonicalToBytecodeLowerer.INLINE_CALLBACK_CALL_LOCAL.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Nested semantic RootTag regions active at {@code bytecodeIndex}, outermost first. */
    private static List<TagTree> activeNestedRootTags(TagTree automaticRoot, int bytecodeIndex) {
        List<TagTree> regions = new ArrayList<>();
        if (automaticRoot == null) {
            return regions;
        }
        TagTree current = automaticRoot;
        boolean descended = true;
        while (descended) {
            descended = false;
            for (TagTree child : current.getTreeChildren()) {
                if (child.getEnterBytecodeIndex() <= bytecodeIndex
                        && bytecodeIndex <= child.getReturnBytecodeIndex()) {
                    if (child.hasTag(StandardTags.RootTag.class)) {
                        regions.add(child);
                    }
                    current = child;
                    descended = true;
                    break;
                }
            }
        }
        return regions;
    }

    /**
     * The position of the enclosing activation while an inline callback region runs: the
     * smallest source section around the region's entry that strictly contains the region's own
     * section (the send that invoked the callback), mirroring the call-site position a physical
     * callback's caller frame reports.
     */
    private static SourceSection enclosingSection(BytecodeNode bytecode, TagTree region) {
        SourceSection inner = region.getSourceSection();
        SourceSection[] candidates = bytecode.getSourceLocations(region.getEnterBytecodeIndex());
        if (inner == null || candidates == null) {
            return null;
        }
        SourceSection best = null;
        for (SourceSection candidate : candidates) {
            if (candidate == null
                    || candidate.getSource() != inner.getSource()
                    || candidate.getCharLength() <= inner.getCharLength()
                    || candidate.getCharIndex() > inner.getCharIndex()
                    || candidate.getCharEndIndex() < inner.getCharEndIndex()) {
                continue;
            }
            if (best == null || candidate.getCharLength() < best.getCharLength()) {
                best = candidate;
            }
        }
        return best;
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
            if (occurrence instanceof ProtosBytecodeControlTransferException bridged
                    && bridged.originBytecode() != null) {
                return new Origin(bridged.originBytecode(), bridged.originBytecodeIndex());
            }
            return null;
        }

        boolean belongsTo(ProtosSemanticBytecodeRootNode semantic) {
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
