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
import com.guillermomolina.protos.runtime.ProtosTask;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * PERF017 Test-Tool-private JFR admission timing instrumentation.
 *
 * <p>The event carries only inert diagnostic identity and source-reference metadata. It exposes no
 * Protos capability and does not participate in scheduling, cancellation, lifecycle or result
 * semantics. Operation IDs are allocated only while this event is enabled. Lane IDs use weak Task
 * keys so diagnostic correlation never keeps completed Tasks alive.
 */
final class ProtosTestToolPerf017Admission {
    static final String EVENT_NAME = "protos.TestToolPerf017Admission";

    static final String HOST_CASE_DONE = "HOST_CASE_DONE";
    static final String COMPLETION_TASK_BEGIN = "COMPLETION_TASK_BEGIN";
    static final String FUTURE_TERMINAL = "FUTURE_TERMINAL";
    static final String LIFECYCLE_TERMINAL = "LIFECYCLE_TERMINAL";
    static final String LIFECYCLE_STARTED = "LIFECYCLE_STARTED";
    static final String SUBMIT_ENTER = "SUBMIT_ENTER";
    static final String SUBMIT_RETURN = "SUBMIT_RETURN";
    static final String CARRIER_RUN_BEGIN = "CARRIER_RUN_BEGIN";

    private static final EventType EVENT_TYPE =
            EventType.getEventType(ProtosTestToolPerf017AdmissionEvent.class);
    private static final AtomicLong NEXT_OPERATION_ID = new AtomicLong();
    private static final AtomicLong NEXT_LANE_ID = new AtomicLong();
    private static final Map<ProtosTask, Long> LANE_IDS = new WeakHashMap<>();

    private ProtosTestToolPerf017Admission() {}

    static boolean enabled() {
        return EVENT_TYPE.isEnabled();
    }

    static OperationCorrelation operationCorrelation(ProtosActivation caller) {
        Objects.requireNonNull(caller, "caller");
        if (!enabled()) {
            return null;
        }

        long laneId = caller.task().map(ProtosTestToolPerf017Admission::laneId).orElse(0L);
        return new OperationCorrelation(NEXT_OPERATION_ID.incrementAndGet(), laneId);
    }

    static void emitOperation(
            OperationCorrelation correlation,
            String phase,
            String corpusId,
            String sourcePath,
            String selector) {
        if (correlation == null || !enabled()) {
            return;
        }

        commit(
                phase,
                correlation.operationId(),
                correlation.laneId(),
                -1L,
                corpusId,
                sourcePath,
                selector,
                null);
    }

    static void emitLifecycle(
            ProtosActivation caller, String phase, long lifecycleToken, String displayReference) {
        Objects.requireNonNull(caller, "caller");
        if (!enabled()) {
            return;
        }

        long laneId = caller.task().map(ProtosTestToolPerf017Admission::laneId).orElse(0L);
        commit(
                phase,
                0L,
                laneId,
                lifecycleToken,
                null,
                null,
                null,
                displayReference);
    }

    private static long laneId(ProtosTask task) {
        synchronized (LANE_IDS) {
            return LANE_IDS.computeIfAbsent(
                    Objects.requireNonNull(task, "task"), ignored -> NEXT_LANE_ID.incrementAndGet());
        }
    }

    private static void commit(
            String phase,
            long operationId,
            long laneId,
            long lifecycleToken,
            String corpusId,
            String sourcePath,
            String selector,
            String displayReference) {
        ProtosTestToolPerf017AdmissionEvent event = new ProtosTestToolPerf017AdmissionEvent();
        event.phase = Objects.requireNonNull(phase, "phase");
        event.operationId = operationId;
        event.laneId = laneId;
        event.lifecycleToken = lifecycleToken;
        event.corpusId = corpusId;
        event.sourcePath = sourcePath;
        event.selector = selector;
        event.displayReference = displayReference;
        event.commit();
    }

    record OperationCorrelation(long operationId, long laneId) {}
}

@Name(ProtosTestToolPerf017Admission.EVENT_NAME)
@Label("Protos Test Tool PERF017 Admission")
@StackTrace(false)
final class ProtosTestToolPerf017AdmissionEvent extends Event {
    @Label("Phase")
    String phase;

    @Label("Operation ID")
    long operationId;

    @Label("Lane ID")
    long laneId;

    @Label("Lifecycle Token")
    long lifecycleToken;

    @Label("Corpus ID")
    String corpusId;

    @Label("Source Path")
    String sourcePath;

    @Label("Selector")
    String selector;

    @Label("Display Reference")
    String displayReference;
}

