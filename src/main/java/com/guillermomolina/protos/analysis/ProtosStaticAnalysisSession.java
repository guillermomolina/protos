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

package com.guillermomolina.protos.analysis;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client-session-owned custody for editor-neutral static-analysis snapshots.
 *
 * <p>Workspace and document identifiers are opaque. This class does not infer
 * paths, URIs, module specifiers, package roots, or {@code ModuleKey} values
 * from them. Each workspace owns an independent document map, and all state is
 * instance-local: there is no process-global semantic registry.</p>
 *
 * <p>Each analysis request captures one immutable document snapshot and parses
 * that snapshot outside the mutable custody maps. Concurrent replacement or
 * removal therefore cannot change the source observed by an in-flight parse.
 * Callers can use {@link #isCurrent(String, ProtosStaticParseResult)} before
 * publishing a result whose freshness matters.</p>
 *
 * <p>The numeric document version remains opaque metadata. This class does not
 * impose LSP ordering rules or reject decreasing/reused version values; the
 * protocol adapter owns protocol-specific ordering.</p>
 */
public final class ProtosStaticAnalysisSession {
    private final ProtosStaticAnalysisCore core;
    private final ConcurrentMap<String, WorkspaceState> workspaces = new ConcurrentHashMap<>();

    public ProtosStaticAnalysisSession() {
        this(new ProtosStaticAnalysisCore());
    }

    ProtosStaticAnalysisSession(ProtosStaticAnalysisCore core) {
        this.core = Objects.requireNonNull(core, "core");
    }

    /**
     * Adds one independent workspace custody domain.
     *
     * @return {@code true} when the workspace was newly added; {@code false}
     *         when it already existed
     */
    public boolean openWorkspace(String workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        return workspaces.putIfAbsent(workspaceId, new WorkspaceState()) == null;
    }

    /**
     * Removes one workspace and all document snapshots owned by it.
     *
     * @return {@code true} when a workspace was removed
     */
    public boolean closeWorkspace(String workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        return workspaces.remove(workspaceId) != null;
    }

    /**
     * Replaces the current immutable snapshot for one document.
     *
     * <p>Replacement order is the order in which this method is applied to the
     * workspace map. The numeric snapshot version is not interpreted here.</p>
     *
     * @return the previously current snapshot, if any
     * @throws IllegalArgumentException when the workspace is not currently open
     */
    public Optional<ProtosDocumentSnapshot> putDocument(
            String workspaceId,
            ProtosDocumentSnapshot snapshot) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(snapshot, "snapshot");

        AtomicReference<ProtosDocumentSnapshot> previous = new AtomicReference<>();
        workspaces.compute(
                workspaceId,
                (ignored, workspace) -> {
                    if (workspace == null) {
                        throw new IllegalArgumentException(
                                "Unknown static-analysis workspace: " + workspaceId);
                    }
                    previous.set(workspace.documents.put(snapshot.documentId(), snapshot));
                    return workspace;
                });
        return Optional.ofNullable(previous.get());
    }

    /**
     * Removes the current snapshot for one document.
     *
     * @return the removed snapshot, or empty when the workspace/document is not
     *         currently present
     */
    public Optional<ProtosDocumentSnapshot> closeDocument(
            String workspaceId,
            String documentId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(documentId, "documentId");

        WorkspaceState workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(workspace.documents.remove(documentId));
    }

    /**
     * Captures the current immutable snapshot for one document.
     */
    public Optional<ProtosDocumentSnapshot> currentSnapshot(
            String workspaceId,
            String documentId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(documentId, "documentId");

        WorkspaceState workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(workspace.documents.get(documentId));
    }

    /**
     * Parses the currently captured snapshot, if present.
     *
     * <p>The parser runs on the captured immutable value, not while holding any
     * workspace-map mutation lock. A concurrent update can make the returned
     * result stale but cannot alter what source the parse observed.</p>
     */
    public Optional<ProtosStaticParseResult> parseCurrent(
            String workspaceId,
            String documentId) {
        Optional<ProtosDocumentSnapshot> snapshot = currentSnapshot(workspaceId, documentId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(core.parse(snapshot.get()));
    }

    /**
     * Resolves a D110 generation-1 definition proof from the currently captured
     * immutable document snapshot, if present.
     *
     * <p>The analysis runs outside custody-map mutation. A concurrent document
     * replacement can therefore make the returned result stale but cannot alter
     * the source snapshot it observed. Callers whose publication requires
     * freshness must use {@link #isCurrent(String, ProtosStaticDefinitionResult)}
     * before publishing it.</p>
     */
    public Optional<ProtosStaticDefinitionResult> definitionCurrent(
            String workspaceId,
            String documentId,
            int sourceOffset) {
        Optional<ProtosDocumentSnapshot> snapshot =
                currentSnapshot(workspaceId, documentId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        return core.definition(snapshot.get(), sourceOffset);
    }

    /**
     * Returns whether a parse result still corresponds exactly to the current
     * snapshot of the same document in the selected workspace.
     *
     * <p>This compares snapshot value, not host object identity. An equivalent
     * replacement does not become stale merely because it is a different Java
     * object.</p>
     */
    public boolean isCurrent(String workspaceId, ProtosStaticParseResult result) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(result, "result");

        WorkspaceState workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            return false;
        }
        ProtosDocumentSnapshot current =
                workspace.documents.get(result.snapshot().documentId());
        return result.snapshot().equals(current);
    }

    /**
     * Returns whether a definition proof still corresponds exactly to the
     * current snapshot of its reference document.
     */
    public boolean isCurrent(
            String workspaceId,
            ProtosStaticDefinitionResult result) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(result, "result");

        WorkspaceState workspace = workspaces.get(workspaceId);
        if (workspace == null) {
            return false;
        }
        ProtosDocumentSnapshot current =
                workspace.documents.get(
                        result.referenceSnapshot().documentId());
        return result.referenceSnapshot().equals(current);
    }

    private static final class WorkspaceState {
        private final ConcurrentMap<String, ProtosDocumentSnapshot> documents =
                new ConcurrentHashMap<>();
    }
}
