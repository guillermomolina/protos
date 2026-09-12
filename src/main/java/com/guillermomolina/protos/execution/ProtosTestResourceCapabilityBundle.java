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
import com.guillermomolina.protos.runtime.ProtosMapValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Private TOOL002-I8D3 construction of the D107 guest resource capability bundle.
 *
 * <p>The bundle is the ordinary Core Map representation. Entries are inserted through the standard
 * Map {@code atPut} protocol so String hashing/equality remain language-owned; the completed Map is
 * frozen before it can enter Process bootstrap. This class receives only the successful
 * transaction's candidate capability snapshot and therefore has no ProviderAdapter, ProviderLease,
 * cleanup or registry authority to project into the guest.
 */
final class ProtosTestResourceCapabilityBundle {
    private ProtosTestResourceCapabilityBundle() {}

    static ProtosMapValue createResourceful(
            ProtosPrelude prelude, Map<String, ?> candidateCapabilities) {
        Objects.requireNonNull(prelude, "prelude");
        Objects.requireNonNull(candidateCapabilities, "candidateCapabilities");
        if (candidateCapabilities.isEmpty()) {
            throw new IllegalArgumentException(
                    "resourceful capability bundle requires at least one resource");
        }

        ProtosMapValue resources = prelude.newMap();
        ProtosActivation constructionActivation = prelude.newModuleActivation();

        for (Map.Entry<String, ?> entry : candidateCapabilities.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "resource key");
            Object capability = Objects.requireNonNull(entry.getValue(), "resource capability");
            ProtosInvocation.invokeMessage(
                    resources,
                    "atPut",
                    List.of(new ProtosStringValue(key), capability),
                    constructionActivation);
        }

        resources.freeze();
        return resources;
    }
}
