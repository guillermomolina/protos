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
package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.util.Objects;

/**
 * D125 host registry for Test Tool execution requirements.
 *
 * <p>D126 moves source authority and plan materialization to the independent corpus registry, so
 * these bindings contain execution and inspection mechanics only.
 */
final class ProtosTestExecutionRequirementRegistry {
    static final String REGISTRY_SLOT = "testExecutionRequirementBindings";

    private ProtosTestExecutionRequirementRegistry() {}

    static void install(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (activation.context().hasLocalSlot(REGISTRY_SLOT)) {
            throw new IllegalStateException(
                    "Test Tool execution requirement registry already installed");
        }

        ProtosObjectValue registry = new ProtosObjectValue(ProtosObjectValue.rootObject());

        addBinding(
                registry,
                activation,
                "protos/test/ordinary",
                "executionAsync",
                "executionInspectAsync",
                "resourceExecutionAsync",
                "resourceExecutionInspectAsync");
        addBinding(
                registry,
                activation,
                "protos/test/actor",
                "actorExecutionAsync",
                "actorExecutionInspectAsync",
                "actorResourceExecutionAsync",
                "actorResourceExecutionInspectAsync");
        addBinding(
                registry,
                activation,
                "protos/test/group",
                "groupExecutionAsync",
                "groupExecutionInspectAsync",
                "groupResourceExecutionAsync",
                "groupResourceExecutionInspectAsync");
        addBinding(
                registry,
                activation,
                "protos/test/package",
                "packageExecutionAsync",
                "packageExecutionInspectAsync",
                "packageResourceExecutionAsync",
                "packageResourceExecutionInspectAsync");

        registry.freeze();
        activation.context().createLocalSlot(REGISTRY_SLOT, registry);
    }

    private static void addBinding(
            ProtosObjectValue registry,
            ProtosActivation activation,
            String requirementId,
            String executionSlot,
            String inspectionSlot,
            String resourceExecutionSlot,
            String resourceInspectionSlot) {
        if (registry.hasLocalSlot(requirementId)) {
            throw new IllegalStateException(
                    "duplicate Test Tool execution requirement binding: " + requirementId);
        }

        ProtosObjectValue binding = new ProtosObjectValue(ProtosObjectValue.rootObject());
        binding.createLocalSlot("executionAsync", requiredSlot(activation, executionSlot));
        binding.createLocalSlot("executionInspectAsync", requiredSlot(activation, inspectionSlot));
        binding.createLocalSlot(
                "resourceExecutionAsync", requiredSlot(activation, resourceExecutionSlot));
        binding.createLocalSlot(
                "resourceExecutionInspectAsync",
                requiredSlot(activation, resourceInspectionSlot));
        binding.freeze();
        registry.createLocalSlot(requirementId, binding);
    }

    private static Object requiredSlot(ProtosActivation activation, String slotName) {
        return activation
                .context()
                .readLocalSlot(slotName)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "missing Test Tool execution binding facility: "
                                                + slotName));
    }
}
