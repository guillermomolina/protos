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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSemanticTransferDestination;
import com.guillermomolina.protos.runtime.ProtosSemanticTransferFamily;
import com.guillermomolina.protos.runtime.ProtosSemanticTransferPayload;
import com.guillermomolina.protos.runtime.ProtosSemanticTransferValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single PLAT051 semantic transfer family of {@code std:regex/Regex} Pattern and Match values
 * (PLAT051-B, D187).
 *
 * <p>Pattern and Match semantics stay in {@code Regex.protos}. This family only mints the values
 * that module builds, extracts their portable semantic data, and asks the destination's own
 * module instance to build fresh values:
 *
 * <ul>
 *   <li>Pattern payload: {@code ["Pattern", source, canonicalFlags]}. The destination recompiles
 *       with its own implementation; no compiled program or Closure of the source crosses.
 *   <li>Match payload: {@code ["Match", captureCount, groups, names]} where {@code groups} holds
 *       one entry per group 0..captureCount, {@code [false]} for a nonparticipating group or
 *       {@code [true, text, start, end]} for a participating one, and {@code names} holds
 *       {@code [name, group]} pairs. It is a snapshot of the computed result: the subject is never
 *       retained or transferred and the destination never matches again.
 * </ul>
 *
 * <p>The kind discriminator is validated data, never authority: only values minted here carry this
 * exact family, and only a Prelude whose bootstrap registered this descriptor transfers them.
 *
 * <p>Minting authority is a frozen bootstrap facility installed only as an initial member of the
 * exact Regex module, which captures it lexically and removes its slot during initialization. The
 * same facility lets that module install, in its own Actor-local module record, the guest factory
 * that the destination stage invokes, so destination values get their methods from destination
 * guest code. Nothing here is module surface.
 */
public final class ProtosRegexSemanticTransferFamily extends ProtosSemanticTransferFamily {
    public static final String BOOTSTRAP_SLOT = "_regexTransferFacility";

    private static final String PATTERN = "Pattern";
    private static final String MATCH = "Match";
    private static final Object PATTERN_STATE = new Object();
    private static final List<String> CANONICAL_FLAGS = List.of("i", "m", "s", "x");

    /**
     * Runtime-private Match state: the frozen Arrays the guest Match itself retains, referenced
     * rather than copied.
     */
    private record MatchState(
            ProtosArrayValue bounds, ProtosArrayValue texts, ProtosArrayValue groupNames) {}

    public ProtosRegexSemanticTransferFamily() {
        super(ProtosRegexUnicodeFacility.MODULE_KEY);
    }

    /**
     * Creates the frozen, stateless minting facility bound to this family.
     *
     * <ul>
     *   <li>{@code mintPattern(template)} answers a FROZEN Pattern of this family with the slots and
     *       parent of the OPEN {@code template}, which must hold String {@code source} and
     *       {@code flags};
     *   <li>{@code mintMatch(template, bounds, texts, groupNames)} likewise answers a FROZEN Match
     *       that privately references the three frozen Arrays its methods already capture;
     *   <li>{@code installFactory(factory)} fixes the calling Actor's Regex module factory
     *       {@code factory(kind, a, b, c)} used by destination materialization.
     * </ul>
     */
    public ProtosObjectValue createFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        facility.createLocalSlot(
                "mintPattern",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> mintPattern(activation, supplied)));
        facility.createLocalSlot(
                "mintMatch",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> mintMatch(activation, supplied)));
        facility.createLocalSlot(
                "installFactory",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> installFactory(activation, supplied)));
        return facility.freeze();
    }

    private Object mintPattern(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1) {
            throw invalid(activation);
        }
        ProtosObjectValue template = template(activation, supplied.get(0));
        if (!(template.readLocalSlot("source").orElse(null) instanceof ProtosStringValue)
                || !(template.readLocalSlot("flags").orElse(null) instanceof ProtosStringValue flags)
                || !canonicalFlags(flags.value())) {
            throw invalid(activation);
        }
        return mint(template, PATTERN_STATE);
    }

    private Object mintMatch(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 4
                || !(supplied.get(1) instanceof ProtosArrayValue bounds && bounds.isFrozen())
                || !(supplied.get(2) instanceof ProtosArrayValue texts && texts.isFrozen())
                || !(supplied.get(3) instanceof ProtosArrayValue names && names.isFrozen())) {
            throw invalid(activation);
        }
        ProtosObjectValue template = template(activation, supplied.get(0));
        return mint(template, new MatchState(bounds, texts, names));
    }

    private Object installFactory(ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1 || !(supplied.get(0) instanceof ProtosClosureValue)) {
            throw invalid(activation);
        }
        activation
                .actorModuleState()
                .lookup(ownerModule())
                .orElseThrow(() -> new IllegalStateException("Regex module is not initializing"))
                .installSemanticTransferFactory(supplied.get(0));
        return ProtosNullValue.INSTANCE;
    }

    private static ProtosObjectValue template(ProtosActivation activation, Object candidate) {
        if (candidate == null
                || candidate.getClass() != ProtosObjectValue.class
                || !((ProtosObjectValue) candidate).isOpen()) {
            throw invalid(activation);
        }
        return (ProtosObjectValue) candidate;
    }

    private ProtosSemanticTransferValue mint(ProtosObjectValue template, Object state) {
        ProtosSemanticTransferValue value = newValue(template.parent().orElseThrow(), state);
        value.composeLocalSlotsFrom(template, List.of());
        value.freeze();
        return value;
    }

    @Override
    protected ProtosSemanticTransferPayload extract(ProtosSemanticTransferValue value) {
        Object state = familyState(value);
        if (state == PATTERN_STATE) {
            return ProtosSemanticTransferPayload.of(
                    PATTERN, string(value.readLocalSlot("source").orElse(null)),
                    string(value.readLocalSlot("flags").orElse(null)));
        }
        MatchState match = (MatchState) state;
        List<Object> bounds = match.bounds().indexedSnapshot();
        List<Object> texts = match.texts().indexedSnapshot();
        List<Object> groupNames = match.groupNames().indexedSnapshot();
        int captureCount = texts.size() - 1;
        Object[] groups = new Object[texts.size()];
        List<Object> names = new ArrayList<>();
        for (int group = 0; group <= captureCount; group++) {
            Object text = texts.get(group);
            groups[group] =
                    text == ProtosNullValue.INSTANCE
                            ? ProtosSemanticTransferPayload.of(Boolean.FALSE)
                            : ProtosSemanticTransferPayload.of(
                                    Boolean.TRUE,
                                    string(text),
                                    integer(bounds.get(2 * group)),
                                    integer(bounds.get(2 * group + 1)));
            if (groupNames.get(group) != ProtosNullValue.INSTANCE) {
                names.add(
                        ProtosSemanticTransferPayload.of(
                                string(groupNames.get(group)), BigInteger.valueOf(group)));
            }
        }
        return ProtosSemanticTransferPayload.of(
                MATCH,
                BigInteger.valueOf(captureCount),
                ProtosSemanticTransferPayload.of(groups),
                ProtosSemanticTransferPayload.of(names.toArray()));
    }

    /**
     * Mirrors every check the guest construction performs, so an accepted payload always
     * materializes. A Pattern's source is not recompiled here: only the trusted Regex module mints
     * Patterns, and it does so after compiling exactly that source and canonical flags, so the
     * same library image compiles them again.
     */
    @Override
    protected boolean acceptsPayload(ProtosSemanticTransferPayload payload) {
        if (payload.size() == 3 && PATTERN.equals(payload.get(0))) {
            return payload.get(1) instanceof String
                    && payload.get(2) instanceof String flags
                    && canonicalFlags(flags);
        }
        if (payload.size() != 4
                || !MATCH.equals(payload.get(0))
                || !(payload.get(1) instanceof BigInteger count)
                || count.signum() < 0
                || count.bitLength() > 30
                || !(payload.get(2) instanceof ProtosSemanticTransferPayload groups)
                || !(payload.get(3) instanceof ProtosSemanticTransferPayload names)
                || groups.size() != count.intValue() + 1) {
            return false;
        }
        int captureCount = count.intValue();
        for (int group = 0; group < groups.size(); group++) {
            if (!(groups.get(group) instanceof ProtosSemanticTransferPayload entry)
                    || !acceptsGroup(entry, group)) {
                return false;
            }
        }
        Set<String> seenNames = new HashSet<>();
        boolean[] namedGroups = new boolean[captureCount + 1];
        for (int index = 0; index < names.size(); index++) {
            if (!(names.get(index) instanceof ProtosSemanticTransferPayload pair)
                    || pair.size() != 2
                    || !(pair.get(0) instanceof String name)
                    || !(pair.get(1) instanceof BigInteger group)
                    || group.signum() <= 0
                    || group.compareTo(count) > 0
                    || !seenNames.add(name)
                    || namedGroups[group.intValue()]) {
                return false;
            }
            namedGroups[group.intValue()] = true;
        }
        return true;
    }

    private static boolean acceptsGroup(ProtosSemanticTransferPayload entry, int group) {
        if (entry.size() == 1 && Boolean.FALSE.equals(entry.get(0))) {
            return group > 0;
        }
        return entry.size() == 4
                && Boolean.TRUE.equals(entry.get(0))
                && entry.get(1) instanceof String text
                && entry.get(2) instanceof BigInteger start
                && entry.get(3) instanceof BigInteger end
                && start.signum() >= 0
                && start.compareTo(end) <= 0
                && spansCodePoints(start, end, text.codePointCount(0, text.length()));
    }

    /*
     * PLAT051 carries bounds as exact Integers. Non-negative bounds within the signed-long range
     * subtract exactly as longs; only larger bounds need arbitrary-precision subtraction.
     */
    private static boolean spansCodePoints(BigInteger start, BigInteger end, int length) {
        if (end.bitLength() < Long.SIZE) {
            return end.longValue() - start.longValue() == length;
        }
        return end.subtract(start).equals(BigInteger.valueOf(length));
    }

    @Override
    protected ProtosSemanticTransferValue materialize(
            ProtosSemanticTransferPayload payload, ProtosSemanticTransferDestination destination) {
        Object factory = destination.ownerModuleFactory();
        Object built;
        if (PATTERN.equals(payload.get(0))) {
            built =
                    destination.invoke(
                            factory,
                            List.of(
                                    new ProtosStringValue(PATTERN),
                                    new ProtosStringValue((String) payload.get(1)),
                                    new ProtosStringValue((String) payload.get(2)),
                                    ProtosNullValue.INSTANCE));
        } else {
            ProtosPrelude prelude = destination.prelude();
            ProtosSemanticTransferPayload groups = (ProtosSemanticTransferPayload) payload.get(2);
            ProtosSemanticTransferPayload names = (ProtosSemanticTransferPayload) payload.get(3);
            List<Object> bounds = new ArrayList<>(2 * groups.size());
            List<Object> texts = new ArrayList<>(groups.size());
            List<Object> groupNames = new ArrayList<>(groups.size());
            for (int group = 0; group < groups.size(); group++) {
                ProtosSemanticTransferPayload entry = (ProtosSemanticTransferPayload) groups.get(group);
                boolean participates = Boolean.TRUE.equals(entry.get(0));
                texts.add(
                        participates
                                ? new ProtosStringValue((String) entry.get(1))
                                : ProtosNullValue.INSTANCE);
                bounds.add(
                        participates
                                ? ProtosNumericValueSupport.integer((BigInteger) entry.get(2), prelude)
                                : ProtosNullValue.INSTANCE);
                bounds.add(
                        participates
                                ? ProtosNumericValueSupport.integer((BigInteger) entry.get(3), prelude)
                                : ProtosNullValue.INSTANCE);
                groupNames.add(ProtosNullValue.INSTANCE);
            }
            for (int index = 0; index < names.size(); index++) {
                ProtosSemanticTransferPayload pair = (ProtosSemanticTransferPayload) names.get(index);
                groupNames.set(
                        ((BigInteger) pair.get(1)).intValue(),
                        new ProtosStringValue((String) pair.get(0)));
            }
            built =
                    destination.invoke(
                            factory,
                            List.of(
                                    new ProtosStringValue(MATCH),
                                    prelude.newFrozenArray(groupNames),
                                    prelude.newFrozenArray(bounds),
                                    prelude.newFrozenArray(texts)));
        }
        if (!(built instanceof ProtosSemanticTransferValue value) || value.family() != this) {
            throw new IllegalStateException("Regex factory did not mint a value of its family");
        }
        return value;
    }

    private static boolean canonicalFlags(String flags) {
        int next = 0;
        for (int index = 0; index < flags.length(); index++) {
            int position = CANONICAL_FLAGS.indexOf(flags.substring(index, index + 1));
            if (position < next) {
                return false;
            }
            next = position + 1;
        }
        return true;
    }

    private static String string(Object value) {
        return ((ProtosStringValue) value).value();
    }

    private static BigInteger integer(Object value) {
        return ProtosNumericValueSupport.exactBigInteger(value);
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
