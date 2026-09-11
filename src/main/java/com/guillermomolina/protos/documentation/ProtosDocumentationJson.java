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

import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Artifact;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Callable;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.ExactArtifactScope;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Module;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.ModuleLineageIdentity;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.PackageModuleIdentity;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.PackageReleaseScope;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.PackageRevisionScope;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.RepositoryRevisionScope;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.SourceProvenance;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.SourceRange;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.StandardModuleIdentity;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Symbol;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.SymbolIdentity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Canonical deterministic UTF-8 JSON serializer for D064 v1 artifacts.
 */
public final class ProtosDocumentationJson {
    private static final Comparator<Module> MODULE_ORDER =
            (left, right) -> ProtosDocumentationModel.compareModuleIdentities(
                    left.identity(), right.identity());
    private static final Comparator<Symbol> SYMBOL_ORDER =
            (left, right) -> ProtosDocumentationModel.compareSymbolIdentities(
                    left.identity(), right.identity());

    private ProtosDocumentationJson() {
    }

    /**
     * Serialize one validated artifact with stable field order, entity ordering,
     * LF line endings, no timestamp, and exactly one final newline.
     */
    public static byte[] serialize(Artifact artifact) {
        Objects.requireNonNull(artifact, "artifact");

        List<Module> modules = new ArrayList<>(artifact.modules());
        modules.sort(MODULE_ORDER);
        List<Symbol> symbols = new ArrayList<>(artifact.symbols());
        symbols.sort(SYMBOL_ORDER);

        StringBuilder output = new StringBuilder();
        output.append("{\n");
        output.append("  \"format\":{\"name\":");
        appendQuoted(output, ProtosDocumentationModel.FORMAT_NAME);
        output.append(",\"major\":").append(ProtosDocumentationModel.FORMAT_MAJOR);
        output.append(",\"minor\":").append(ProtosDocumentationModel.FORMAT_MINOR);
        output.append("},\n");

        output.append("  \"generator\":{\"name\":");
        appendQuoted(output, artifact.generator().name());
        output.append(",\"version\":");
        appendQuoted(output, artifact.generator().version());
        output.append("},\n");

        output.append("  \"provenance\":");
        appendScope(output, artifact.scope());
        output.append(",\n");

        output.append("  \"modules\":[");
        appendEntityArrayStart(output, modules.isEmpty());
        for (int index = 0; index < modules.size(); index++) {
            output.append("    ");
            appendModule(output, modules.get(index));
            output.append(index + 1 == modules.size() ? '\n' : ",\n");
        }
        if (!modules.isEmpty()) {
            output.append("  ");
        }
        output.append("],\n");

        output.append("  \"symbols\":[");
        appendEntityArrayStart(output, symbols.isEmpty());
        for (int index = 0; index < symbols.size(); index++) {
            output.append("    ");
            appendSymbol(output, symbols.get(index));
            output.append(index + 1 == symbols.size() ? '\n' : ",\n");
        }
        if (!symbols.isEmpty()) {
            output.append("  ");
        }
        output.append("],\n");

        // D064 deliberately defers article identity/association and relationship vocabulary.
        output.append("  \"articles\":[],\n");
        output.append("  \"relationships\":[]\n");
        output.append("}\n");
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendEntityArrayStart(StringBuilder output, boolean empty) {
        if (!empty) {
            output.append('\n');
        }
    }

    private static void appendScope(StringBuilder output, ExactArtifactScope scope) {
        output.append('{');
        if (scope instanceof RepositoryRevisionScope repository) {
            output.append("\"kind\":\"repositoryRevision\",\"repository\":");
            appendQuoted(output, repository.repository());
            output.append(",\"revision\":");
            appendQuoted(output, repository.revision());
        } else if (scope instanceof PackageReleaseScope release) {
            output.append("\"kind\":\"packageRelease\",\"packageId\":");
            appendQuoted(output, release.packageId());
            output.append(",\"releaseVersion\":");
            appendQuoted(output, release.releaseVersion());
            output.append(",\"contentIdentity\":");
            appendQuoted(output, release.contentIdentity());
        } else if (scope instanceof PackageRevisionScope revision) {
            output.append("\"kind\":\"packageRevision\",\"packageId\":");
            appendQuoted(output, revision.packageId());
            output.append(",\"revision\":");
            appendQuoted(output, revision.revision());
            output.append(",\"contentIdentity\":");
            appendQuoted(output, revision.contentIdentity());
        } else {
            throw new IllegalArgumentException(
                    "unsupported exact artifact scope: " + scope.getClass().getName());
        }
        output.append('}');
    }

    private static void appendModule(StringBuilder output, Module module) {
        output.append("{\"kind\":\"module\",\"identity\":");
        appendModuleIdentity(output, module.identity());
        output.append(",\"documentation\":");
        appendNullableQuoted(output, module.documentation());
        output.append(",\"source\":");
        appendSource(output, module.source());
        output.append('}');
    }

    private static void appendSymbol(StringBuilder output, Symbol symbol) {
        output.append("{\"kind\":\"slot\",\"identity\":");
        appendSymbolIdentity(output, symbol.identity());
        output.append(",\"documentation\":");
        appendNullableQuoted(output, symbol.documentation());
        output.append(",\"source\":");
        appendSource(output, symbol.source());
        output.append(",\"callable\":");
        appendCallable(output, symbol.callable());
        output.append('}');
    }

    private static void appendModuleIdentity(
            StringBuilder output,
            ModuleLineageIdentity identity) {
        output.append('{');
        if (identity instanceof StandardModuleIdentity standard) {
            output.append("\"kind\":\"std\",\"name\":");
            appendQuoted(output, standard.name());
        } else if (identity instanceof PackageModuleIdentity packaged) {
            output.append("\"kind\":\"package\",\"packageId\":");
            appendQuoted(output, packaged.packageId());
            output.append(",\"module\":");
            appendQuoted(output, packaged.logicalModuleName());
        } else {
            throw new IllegalArgumentException(
                    "unsupported module lineage identity: "
                            + identity.getClass().getName());
        }
        output.append('}');
    }

    private static void appendSymbolIdentity(
            StringBuilder output,
            SymbolIdentity identity) {
        output.append("{\"module\":");
        appendModuleIdentity(output, identity.module());
        output.append(",\"slot\":");
        appendQuoted(output, identity.slotName());
        output.append('}');
    }

    private static void appendSource(
            StringBuilder output,
            SourceProvenance source) {
        output.append("{\"path\":");
        appendQuoted(output, source.path());
        output.append(",\"range\":");
        appendRange(output, source.range());
        output.append('}');
    }

    private static void appendRange(StringBuilder output, SourceRange range) {
        output.append("{\"start\":{\"line\":")
                .append(range.startLine())
                .append(",\"column\":")
                .append(range.startColumn())
                .append("},\"end\":{\"line\":")
                .append(range.endLine())
                .append(",\"column\":")
                .append(range.endColumn())
                .append("}}");
    }

    private static void appendCallable(StringBuilder output, Callable callable) {
        if (callable == null) {
            output.append("null");
            return;
        }

        output.append("{\"parameters\":[");
        for (int index = 0; index < callable.parameters().size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            appendQuoted(output, callable.parameters().get(index));
        }
        output.append("],\"restParameter\":");
        appendNullableQuoted(output, callable.restParameter());
        output.append('}');
    }

    private static void appendNullableQuoted(StringBuilder output, String value) {
        if (value == null) {
            output.append("null");
        } else {
            appendQuoted(output, value);
        }
    }

    private static void appendQuoted(StringBuilder output, String value) {
        ProtosDocumentationModel.validateUnicode(value, "JSON string");
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (current < 0x20) {
                        output.append(String.format("\\u%04x", (int) current));
                    } else {
                        output.append(current);
                    }
                }
            }
        }
        output.append('"');
    }
}
