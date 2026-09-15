/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT for the complete terms.
 */
package com.guillermomolina.protos.cli;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosFilesystemValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.Objects;

/** D126 invocation-scoped host registry for Test Tool corpus/source authority. */
final class ProtosTestCorpusRegistry {
    static final String REGISTRY_SLOT = "testCorpusBindings";

    private ProtosTestCorpusRegistry() {}

    static void install(ProtosActivation activation) {
        Objects.requireNonNull(activation, "activation");
        if (activation.context().hasLocalSlot(REGISTRY_SLOT)) {
            throw new IllegalStateException("Test Tool corpus registry already installed");
        }

        ProtosObjectValue registry = new ProtosObjectValue(ProtosObjectValue.rootObject());

        addBinding(registry, activation, "protos/corpus/conformance", "manifest", "filesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/process-snapshot",
                "manifest",
                "processSnapshotFilesystem");
        addBinding(registry, activation, "protos/corpus/actor", "manifest", "actorFilesystem");
        addBinding(registry, activation, "protos/corpus/group", "manifest", "groupFilesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/package-toml",
                "package-toml",
                "packageTomlFilesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/library/uri",
                "repository-explicit",
                "libraryFilesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/library/csv",
                "repository-explicit",
                "libraryFilesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/library/cli",
                "repository-explicit",
                "libraryFilesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/library/math/integer",
                "repository-explicit",
                "libraryFilesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/library/crypto/sha256",
                "repository-explicit",
                "libraryFilesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/library/network/ip-addresses",
                "repository-explicit",
                "libraryFilesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/library/network/ip-endpoints",
                "repository-explicit",
                "libraryFilesystem");
        addBinding(
                registry,
                activation,
                "protos/corpus/package-tool/version",
                "case-outcomes",
                "packageToolVersionFilesystem",
                "protos/package-tool/version");
        addBinding(
                registry,
                activation,
                "protos/corpus/package-tool/lock",
                "case-outcomes",
                "packageToolLockFilesystem",
                "protos/package-tool/lock");
        addBinding(
                registry,
                activation,
                "protos/corpus/package-tool/resolution-input",
                "case-outcomes",
                "packageToolResolutionInputFilesystem",
                "protos/package-tool/resolution-input");
                addProjectTreeBinding(
                registry,
                activation,
                "protos/corpus/package-tool/resolution-root",
                "packageToolResolutionRootFilesystem",
                "protos/package-tool/resolution-root",
                ProtosTestCaseAuthorityExecutionScope.RESOLUTION_ROOT_SLOT);
                addProjectTreeBinding(
                registry,
                activation,
                "protos/corpus/package-tool/execution-plan",
                "packageToolExecutionPlanFilesystem",
                "protos/package-tool/execution-plan",
                ProtosTestCaseAuthorityExecutionScope.EXECUTION_PLAN_SLOT);
                addProjectTreeBinding(
                registry,
                activation,
                "protos/corpus/package-tool/project-projection",
                "packageToolProjectProjectionFilesystem",
                "protos/package-tool/project-projection",
                ProtosTestCaseAuthorityExecutionScope.PROJECT_PROJECTION_SLOT);

        registry.freeze();
        activation.context().createLocalSlot(REGISTRY_SLOT, registry);
    }

    private static void addBinding(
            ProtosObjectValue registry,
            ProtosActivation activation,
            String corpusId,
            String planLoader,
            String filesystemSlot) {
        addBinding(registry, activation, corpusId, planLoader, filesystemSlot, null);
    }

    /**
     * D132/D133: namespaced corpus bindings additionally carry the explicit
     * logical case namespace supplied by the corpus/TestPlan authority.
     * Non-namespaced loaders pass {@code null}.
     */
    private static void addBinding(
            ProtosObjectValue registry,
            ProtosActivation activation,
            String corpusId,
            String planLoader,
            String filesystemSlot,
            String caseNamespace) {
        if (registry.hasLocalSlot(corpusId)) {
            throw new IllegalStateException("duplicate Test Tool corpus binding: " + corpusId);
        }

        Object filesystem = requiredSlot(activation, filesystemSlot);
        if (!(filesystem instanceof ProtosFilesystemValue)) {
            throw new IllegalStateException(
                    "Test Tool corpus binding "
                            + corpusId
                            + " requires Filesystem capability in slot "
                            + filesystemSlot);
        }

        ProtosObjectValue binding = new ProtosObjectValue(ProtosObjectValue.rootObject());
        binding.createLocalSlot("filesystem", filesystem);
        binding.createLocalSlot("planLoader", new ProtosStringValue(planLoader));
        if (caseNamespace != null) {
            binding.createLocalSlot("caseNamespace", new ProtosStringValue(caseNamespace));
        }
        binding.freeze();
        registry.createLocalSlot(corpusId, binding);
    }

    private static void addProjectTreeBinding(
            ProtosObjectValue registry,
            ProtosActivation activation,
            String corpusId,
            String filesystemSlot,
            String caseNamespace,
            String caseAuthorityExecutionSlot) {
        if (registry.hasLocalSlot(corpusId)) {
            throw new IllegalStateException(
                    "duplicate Test Tool corpus binding: " + corpusId);
        }

        Object filesystem = requiredSlot(activation, filesystemSlot);
        if (!(filesystem instanceof ProtosFilesystemValue)) {
            throw new IllegalStateException(
                    "Test Tool corpus binding "
                            + corpusId
                            + " requires Filesystem capability in slot "
                            + filesystemSlot);
        }

        Object caseAuthorityExecution =
                requiredSlot(
                        activation,
                        caseAuthorityExecutionSlot);

        ProtosObjectValue binding =
                new ProtosObjectValue(
                        ProtosObjectValue.rootObject());

        binding.createLocalSlot(
                "filesystem",
                filesystem);
        binding.createLocalSlot(
                "planLoader",
                new ProtosStringValue("project-tree"));
        binding.createLocalSlot(
                "caseNamespace",
                new ProtosStringValue(caseNamespace));
        binding.createLocalSlot(
                "caseAuthorityExecutionAsync",
                caseAuthorityExecution);
        binding.freeze();

        registry.createLocalSlot(
                corpusId,
                binding);
    }

    private static Object requiredSlot(ProtosActivation activation, String slotName) {
        return activation
                .context()
                .readLocalSlot(slotName)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "missing Test Tool corpus binding facility: " + slotName));
    }
}
