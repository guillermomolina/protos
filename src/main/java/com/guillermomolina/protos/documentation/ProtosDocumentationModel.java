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

package com.guillermomolina.protos.documentation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation-neutral data model for D064 documentation artifacts.
 *
 * <p>The model deliberately keeps semantic lineage, exact artifact occurrence,
 * and source provenance as distinct values. It contains no runtime Protos object
 * identity and does not define API publication or stability policy.</p>
 */
public final class ProtosDocumentationModel {
    public static final String FORMAT_NAME = "protos-documentation";
    public static final int FORMAT_MAJOR = 1;
    public static final int FORMAT_MINOR = 0;

    private ProtosDocumentationModel() {
    }

    public sealed interface ModuleLineageIdentity
            permits StandardModuleIdentity, PackageModuleIdentity {
        String sortKey();
    }

    /**
     * Canonical Standard Library module lineage, for example
     * {@code std:collections/Set}.
     */
    public record StandardModuleIdentity(String name) implements ModuleLineageIdentity {
        public StandardModuleIdentity {
            name = requireText(name, "standard module identity");
            if (!name.startsWith("std:")) {
                throw new IllegalArgumentException(
                        "standard module identity must use canonical std: spelling");
            }
            String logicalName = name.substring("std:".length());
            validateLogicalModuleName(logicalName);
            if (logicalName.equals("core") || logicalName.startsWith("core/")) {
                throw new IllegalArgumentException(
                        "physical Core source must not acquire std:core identity");
            }
        }

        @Override
        public String sortKey() {
            return "0\u0000" + name;
        }
    }

    /**
     * Package-backed module lineage. Release/revision/content identity does not
     * participate here.
     */
    public record PackageModuleIdentity(String packageId, String logicalModuleName)
            implements ModuleLineageIdentity {
        public PackageModuleIdentity {
            packageId = requireText(packageId, "packageId");
            logicalModuleName = requireText(logicalModuleName, "logical module name");
            validateLogicalModuleName(logicalModuleName);
            if (logicalModuleName.startsWith("std:")) {
                throw new IllegalArgumentException(
                        "package logical module name must not use std: identity spelling");
            }
        }

        @Override
        public String sortKey() {
            return "1\u0000" + packageId + "\u0000" + logicalModuleName;
        }
    }

    public sealed interface ExactArtifactScope
            permits RepositoryRevisionScope, PackageReleaseScope, PackageRevisionScope {
        String sortKey();
    }

    /**
     * Exact repository revision scope, used by the canonical Standard Library
     * exact-SHA artifact.
     */
    public record RepositoryRevisionScope(String repository, String revision)
            implements ExactArtifactScope {
        public RepositoryRevisionScope {
            repository = validateRepositoryCoordinate(repository);
            revision = requireText(revision, "revision");
        }

        @Override
        public String sortKey() {
            return "0\u0000" + repository + "\u0000" + revision;
        }
    }

    /**
     * Exact published package release occurrence.
     */
    public record PackageReleaseScope(
            String packageId,
            String releaseVersion,
            String contentIdentity) implements ExactArtifactScope {
        public PackageReleaseScope {
            packageId = requireText(packageId, "packageId");
            releaseVersion = requireText(releaseVersion, "releaseVersion");
            contentIdentity = requireText(contentIdentity, "contentIdentity");
        }

        @Override
        public String sortKey() {
            return "1\u0000" + packageId + "\u0000" + releaseVersion
                    + "\u0000" + contentIdentity;
        }
    }

    /**
     * Exact package VCS/revision occurrence when a release label is not the
     * source authority.
     */
    public record PackageRevisionScope(
            String packageId,
            String revision,
            String contentIdentity) implements ExactArtifactScope {
        public PackageRevisionScope {
            packageId = requireText(packageId, "packageId");
            revision = requireText(revision, "revision");
            contentIdentity = requireText(contentIdentity, "contentIdentity");
        }

        @Override
        public String sortKey() {
            return "2\u0000" + packageId + "\u0000" + revision
                    + "\u0000" + contentIdentity;
        }
    }

    public record Generator(String name, String version) {
        public Generator {
            name = requireText(name, "generator name");
            version = requireText(version, "generator version");
        }
    }

    /**
     * One-based line and Unicode-scalar column range with an exclusive end.
     *
     * <p>This is documentation provenance only. It does not participate in
     * module or symbol identity.</p>
     */
    public record SourceRange(
            int startLine,
            int startColumn,
            int endLine,
            int endColumn) {
        public SourceRange {
            if (startLine < 1 || startColumn < 1 || endLine < 1 || endColumn < 1) {
                throw new IllegalArgumentException(
                        "source range lines and columns must be one-based");
            }
            if (endLine < startLine
                    || (endLine == startLine && endColumn < startColumn)) {
                throw new IllegalArgumentException(
                        "source range end must not precede start");
            }
        }
    }

    /**
     * Repository/package-relative source provenance.
     */
    public record SourceProvenance(String path, SourceRange range) {
        public SourceProvenance {
            path = validateRelativeSourcePath(path);
            range = Objects.requireNonNull(range, "range");
        }
    }

    public record Module(
            ModuleLineageIdentity identity,
            String documentation,
            SourceProvenance source) {
        public Module {
            identity = Objects.requireNonNull(identity, "identity");
            documentation = normalizeMarkdown(documentation);
            source = Objects.requireNonNull(source, "source");
        }
    }

    public record SymbolIdentity(
            ModuleLineageIdentity module,
            String slotName) {
        public SymbolIdentity {
            module = Objects.requireNonNull(module, "module");
            slotName = requireText(slotName, "slot name");
        }

        String sortKey() {
            return module.sortKey() + "\u0000" + slotName;
        }
    }

    /**
     * Mechanically observable callable shape. It is intentionally not part of
     * {@link SymbolIdentity}.
     */
    public record Callable(
            List<String> parameters,
            String restParameter) {
        public Callable {
            Objects.requireNonNull(parameters, "parameters");
            List<String> copy = new ArrayList<>(parameters.size());
            Set<String> seen = new HashSet<>();
            for (String parameter : parameters) {
                String validated = requireText(parameter, "parameter name");
                if (!seen.add(validated)) {
                    throw new IllegalArgumentException(
                            "duplicate callable parameter: " + validated);
                }
                copy.add(validated);
            }
            parameters = List.copyOf(copy);
            if (restParameter != null) {
                restParameter = requireText(restParameter, "rest parameter");
                if (!seen.add(restParameter)) {
                    throw new IllegalArgumentException(
                            "rest parameter duplicates ordinary parameter: " + restParameter);
                }
            }
        }
    }

    public record Symbol(
            SymbolIdentity identity,
            String documentation,
            SourceProvenance source,
            Callable callable) {
        public Symbol {
            identity = Objects.requireNonNull(identity, "identity");
            documentation = normalizeMarkdown(documentation);
            source = Objects.requireNonNull(source, "source");
        }
    }

    /**
     * Exact symbol occurrence: artifact scope plus stable semantic lineage.
     */
    public record SymbolOccurrenceKey(
            ExactArtifactScope scope,
            SymbolIdentity identity) {
        public SymbolOccurrenceKey {
            scope = Objects.requireNonNull(scope, "scope");
            identity = Objects.requireNonNull(identity, "identity");
        }
    }

    /**
     * D064 v1 baseline artifact.
     *
     * <p>Supplemental articles and relationships are emitted as empty arrays by
     * the initial serializer because their authoring/identity vocabularies are
     * deliberately deferred. They can be added compatibly when separately
     * ratified.</p>
     */
    public record Artifact(
            Generator generator,
            ExactArtifactScope scope,
            List<Module> modules,
            List<Symbol> symbols) {
        public Artifact {
            generator = Objects.requireNonNull(generator, "generator");
            scope = Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(modules, "modules");
            Objects.requireNonNull(symbols, "symbols");
            modules = List.copyOf(modules);
            symbols = List.copyOf(symbols);
            validateArtifact(modules, symbols);
        }

        public SymbolOccurrenceKey occurrenceKey(SymbolIdentity identity) {
            Objects.requireNonNull(identity, "identity");
            boolean present = symbols.stream()
                    .anyMatch(symbol -> symbol.identity().equals(identity));
            if (!present) {
                throw new IllegalArgumentException(
                        "symbol identity is absent from exact artifact: " + identity);
            }
            return new SymbolOccurrenceKey(scope, identity);
        }
    }

    static int compareModuleIdentities(
            ModuleLineageIdentity left,
            ModuleLineageIdentity right) {
        return compareUnicodeScalars(left.sortKey(), right.sortKey());
    }

    static int compareSymbolIdentities(SymbolIdentity left, SymbolIdentity right) {
        return compareUnicodeScalars(left.sortKey(), right.sortKey());
    }

    private static void validateArtifact(List<Module> modules, List<Symbol> symbols) {
        Set<ModuleLineageIdentity> moduleIds = new HashSet<>();
        for (Module module : modules) {
            Objects.requireNonNull(module, "module");
            if (!moduleIds.add(module.identity())) {
                throw new IllegalArgumentException(
                        "duplicate module identity in exact artifact: " + module.identity());
            }
        }

        Set<SymbolIdentity> symbolIds = new HashSet<>();
        for (Symbol symbol : symbols) {
            Objects.requireNonNull(symbol, "symbol");
            if (!moduleIds.contains(symbol.identity().module())) {
                throw new IllegalArgumentException(
                        "symbol references module absent from exact artifact: "
                                + symbol.identity().module());
            }
            if (!symbolIds.add(symbol.identity())) {
                throw new IllegalArgumentException(
                        "duplicate symbol identity in exact artifact: " + symbol.identity());
            }
        }
    }


    private static String validateRepositoryCoordinate(String value) {
        String repository = requireText(value, "repository");
        String[] segments = repository.split("/", -1);
        if (segments.length != 2) {
            throw new IllegalArgumentException(
                    "repository must be a canonical owner/name coordinate");
        }
        for (String segment : segments) {
            if (!segment.matches("[A-Za-z0-9._-]+")
                    || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        "repository must be a canonical owner/name coordinate");
            }
        }
        return repository;
    }

    private static String validateRelativeSourcePath(String value) {
        String path = requireText(value, "source path");
        if (path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                    "source path must use canonical forward slashes");
        }
        if (path.startsWith("/") || path.startsWith("//")
                || (path.length() >= 2
                    && Character.isLetter(path.charAt(0))
                    && path.charAt(1) == ':')) {
            throw new IllegalArgumentException(
                    "absolute source paths are forbidden");
        }

        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        "source path must be normalized and repository/package-relative");
            }
        }
        return path;
    }

    private static void validateLogicalModuleName(String value) {
        if (value.startsWith("/") || value.endsWith("/") || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("logical module name must be normalized");
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        "logical module name contains invalid segment");
            }
        }
    }

    private static String normalizeMarkdown(String value) {
        if (value == null) {
            return null;
        }
        validateUnicode(value, "documentation");
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        validateUnicode(value, label);
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            if (codePoint == 0 || Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException(
                        label + " must not contain control characters");
            }
            index += Character.charCount(codePoint);
        }
        return value;
    }

    static void validateUnicode(String value, String label) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(
                            label + " contains an unpaired high surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(
                        label + " contains an unpaired low surrogate");
            }
        }
    }

    private static int compareUnicodeScalars(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftCodePoint = left.codePointAt(leftIndex);
            int rightCodePoint = right.codePointAt(rightIndex);
            if (leftCodePoint != rightCodePoint) {
                return Integer.compare(leftCodePoint, rightCodePoint);
            }
            leftIndex += Character.charCount(leftCodePoint);
            rightIndex += Character.charCount(rightCodePoint);
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }
}
