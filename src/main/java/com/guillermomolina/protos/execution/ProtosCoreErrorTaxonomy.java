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

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bootstrap validation/export for the closed Core v0.1 Error taxonomy. */
final class ProtosCoreErrorTaxonomy {
    private static final Map<String, String> PARENTS = Map.ofEntries(
            Map.entry("SlotNotFound", "Error"),
            Map.entry("Cancelled", "Error"),
            Map.entry("FutureResolutionCycle", "Error"),
            Map.entry("RequestOutcomeUncertain", "Error"),
            Map.entry("NonTransferableValue", "Error"),
            Map.entry("NonParallelValue", "Error"),
            Map.entry("InvalidPredicateResult", "Error"),
            Map.entry("InvalidComparatorResult", "Error"),
            Map.entry("InvalidComparatorOrder", "Error"),
            Map.entry("ParallelRegionOverlap", "Error"),
            Map.entry("ParallelRegionInUse", "Error"),
            Map.entry("ParallelRegionOutsideP", "Error"),
            Map.entry("IOError", "Error"),
            Map.entry("InvalidIOArgument", "IOError"),
            Map.entry("IOLifecycleError", "IOError"),
            Map.entry("IOCapacityExhausted", "IOError"),
            Map.entry("EncodingError", "IOError"),
            Map.entry("LineTooLong", "IOError")
    );

    private ProtosCoreErrorTaxonomy() {}

    static void validate(ProtosObjectValue context, ProtosObjectValue errorPrototype) {
        Map<String, ProtosObjectValue> resolved = new LinkedHashMap<>();
        resolved.put("Error", errorPrototype);
        for (Map.Entry<String,String> relation : PARENTS.entrySet()) {
            String name=relation.getKey(), parentName=relation.getValue();
            Object raw=context.readLocalSlot(name).orElseThrow(
                    () -> new IllegalStateException("Core bootstrap did not define "+name));
            if (!(raw instanceof ProtosObjectValue prototype))
                throw new IllegalStateException("Core "+name+" binding is not an ordinary object");

            ProtosObjectValue expected=resolved.get(parentName);
            if (expected==null) {
                Object parentRaw=context.readLocalSlot(parentName).orElseThrow(
                        () -> new IllegalStateException("Core bootstrap did not define "+parentName));
                if (!(parentRaw instanceof ProtosObjectValue parentPrototype))
                    throw new IllegalStateException("Core "+parentName+" binding is not an ordinary object");
                expected=parentPrototype;
                resolved.put(parentName,parentPrototype);
            }
            if (prototype.parent().orElse(null)!=expected)
                throw new IllegalStateException(
                        "Core "+name+" prototype must delegate directly to "+parentName);
            resolved.put(name,prototype);
        }
    }

    static void exportBindings(ProtosObjectValue context, ProtosObjectValue prelude) {
        for (String name : PARENTS.keySet()) {
            prelude.createLocalSlot(name, context.readLocalSlot(name).orElseThrow());
        }
    }
}
