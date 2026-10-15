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

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable host-supplied metadata and factory of one foreign provider.
 *
 * <p>The profile and its {@code enforcement} mechanism are fixed with the host or embedder that
 * supplies the RuntimeHost registry; import specifiers only select a provider route and never its
 * profile. {@link ProtosForeignProviderAdmission} decides from these two components whether any
 * provider code may run (PLAT052).
 *
 * <p>A provider without an import route can still own a Process lifecycle but is never selected
 * by an import specifier. {@code values} is the provider's single D188 value contract; a provider
 * that declares none admits every value as an opaque raw reference.
 */
record ProtosForeignProviderDescriptor(
        ProtosForeignProviderId id,
        ProtosForeignProviderExecutionProfile profile,
        Optional<ProtosForeignProviderEnforcement> enforcement,
        ProtosForeignProviderFactory factory,
        Optional<ProtosForeignImportRoute> importRoute,
        ProtosForeignValueAdapter values) {
    ProtosForeignProviderDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(enforcement, "enforcement");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(importRoute, "importRoute");
        Objects.requireNonNull(values, "values");
    }

    ProtosForeignProviderDescriptor(
            ProtosForeignProviderId id,
            ProtosForeignProviderExecutionProfile profile,
            ProtosForeignProviderFactory factory,
            Optional<ProtosForeignImportRoute> importRoute,
            ProtosForeignValueAdapter values) {
        this(id, profile, Optional.empty(), factory, importRoute, values);
    }

    ProtosForeignProviderDescriptor(
            ProtosForeignProviderId id,
            ProtosForeignProviderExecutionProfile profile,
            ProtosForeignProviderFactory factory,
            Optional<ProtosForeignImportRoute> importRoute) {
        // The provider id is host metadata, never a guest-visible language name.
        this(id, profile, factory, importRoute, ProtosForeignValueAdapter.opaque("unknown"));
    }

    ProtosForeignProviderDescriptor(
            ProtosForeignProviderId id,
            ProtosForeignProviderExecutionProfile profile,
            ProtosForeignProviderFactory factory) {
        this(id, profile, factory, Optional.empty());
    }
}
