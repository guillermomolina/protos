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

import com.guillermomolina.protos.semantic.ast.CanonicalAssign;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalMultipleCreate;
import com.guillermomolina.protos.semantic.ast.CanonicalLookup;
import com.guillermomolina.protos.semantic.ast.CanonicalObject;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * PLAT036 Candidate D, Slice 1 immutable result of one {@link
 * CanonicalBindingAnalyzer} run: the discovered {@link CanonicalLexicalScope}
 * tree plus every reference/declaration site's binding metadata for the
 * analyzed canonical subtree.
 *
 * <p>All node-keyed lookups use identity, never the canonical AST records'
 * generated structural {@code equals}/{@code hashCode}, because two
 * structurally identical nodes at different source positions (or two
 * genuinely distinct occurrences) must never be conflated. Callers must key
 * by the exact canonical AST node instance obtained from the same
 * compilation.
 */
final class CanonicalBindingAnalysis {
    private final CanonicalLexicalScope topScope;
    private final Map<CanonicalClosure, CanonicalLexicalScope> closureScopes;
    private final Map<CanonicalObject, CanonicalLexicalScope> objectScopes;
    private final Map<CanonicalLookup, CanonicalBindingResolution> lookupResolutions;
    private final Map<CanonicalAssign, CanonicalBindingResolution> assignResolutions;
    private final Map<CanonicalCreate, CanonicalBindingIdentity> createIdentities;
    private final Map<CanonicalMultipleCreate, List<CanonicalBindingIdentity>> multipleCreateIdentities;
    private final Map<CanonicalParameter, CanonicalBindingIdentity> parameterIdentities;

    CanonicalBindingAnalysis(
            CanonicalLexicalScope topScope,
            Map<CanonicalClosure, CanonicalLexicalScope> closureScopes,
            Map<CanonicalObject, CanonicalLexicalScope> objectScopes,
            Map<CanonicalLookup, CanonicalBindingResolution> lookupResolutions,
            Map<CanonicalAssign, CanonicalBindingResolution> assignResolutions,
            Map<CanonicalCreate, CanonicalBindingIdentity> createIdentities,
            Map<CanonicalMultipleCreate, List<CanonicalBindingIdentity>> multipleCreateIdentities,
            Map<CanonicalParameter, CanonicalBindingIdentity> parameterIdentities) {
        this.topScope = Objects.requireNonNull(topScope, "topScope");
        this.closureScopes = copyOfIdentity(closureScopes);
        this.objectScopes = copyOfIdentity(objectScopes);
        this.lookupResolutions = copyOfIdentity(lookupResolutions);
        this.assignResolutions = copyOfIdentity(assignResolutions);
        this.createIdentities = copyOfIdentity(createIdentities);
        this.multipleCreateIdentities = copyOfIdentity(multipleCreateIdentities);
        this.parameterIdentities = copyOfIdentity(parameterIdentities);
    }

    private static <K, V> Map<K, V> copyOfIdentity(Map<K, V> source) {
        Objects.requireNonNull(source, "source");
        return Collections.unmodifiableMap(new IdentityHashMap<>(source));
    }

    CanonicalLexicalScope topScope() {
        return topScope;
    }

    Optional<CanonicalLexicalScope> scopeOf(CanonicalClosure closure) {
        return Optional.ofNullable(closureScopes.get(closure));
    }

    Optional<CanonicalLexicalScope> scopeOf(CanonicalObject object) {
        return Optional.ofNullable(objectScopes.get(object));
    }

    Optional<CanonicalBindingResolution> resolutionOf(CanonicalLookup lookup) {
        return Optional.ofNullable(lookupResolutions.get(lookup));
    }

    Optional<CanonicalBindingResolution> resolutionOf(CanonicalAssign assign) {
        return Optional.ofNullable(assignResolutions.get(assign));
    }

    Optional<CanonicalBindingIdentity> identityOf(CanonicalCreate create) {
        return Optional.ofNullable(createIdentities.get(create));
    }

    Optional<List<CanonicalBindingIdentity>> identitiesOf(CanonicalMultipleCreate create) {
        return Optional.ofNullable(multipleCreateIdentities.get(create));
    }

    Optional<CanonicalBindingIdentity> identityOf(CanonicalParameter parameter) {
        return Optional.ofNullable(parameterIdentities.get(parameter));
    }
}
