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

package com.guillermomolina.protos.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable semantic snapshot of the standard Filesystem.open option tuple.
 *
 * <p>Only local slots of the supplied options object participate. Capturing this value never
 * exercises filesystem authority and never invokes user code.
 */
public final class ProtosFilesystemOpenOptions {
    public enum Creation {
        EXISTING,
        CREATE,
        CREATE_NEW
    }

    public enum Placement {
        POSITIONED,
        APPEND
    }

    private static final Set<String> STANDARD_OPTION_NAMES =
            Set.of("read", "write", "create", "createNew", "truncate", "append");

    private final boolean read;
    private final boolean write;
    private final Creation creation;
    private final boolean truncate;
    private final Placement placement;

    private ProtosFilesystemOpenOptions(
            boolean read,
            boolean write,
            Creation creation,
            boolean truncate,
            Placement placement) {
        this.read = read;
        this.write = write;
        this.creation = Objects.requireNonNull(creation, "creation");
        this.truncate = truncate;
        this.placement = Objects.requireNonNull(placement, "placement");
    }

    /** Standard one-argument open defaults: read existing content, preserve, positioned. */
    public static ProtosFilesystemOpenOptions defaults() {
        return new ProtosFilesystemOpenOptions(
                true, false, Creation.EXISTING, false, Placement.POSITIONED);
    }

    /**
     * Captures and validates the complete standard option tuple exactly once.
     *
     * @throws IllegalArgumentException when the supplied semantic options value is invalid
     */
    public static ProtosFilesystemOpenOptions capture(Object optionsValue) {
        if (!(optionsValue instanceof ProtosObjectValue options)) {
            throw new IllegalArgumentException("filesystem open options must be an object");
        }

        Map<String, Object> slots = options.localSlotsSnapshot();
        for (String name : slots.keySet()) {
            if (!STANDARD_OPTION_NAMES.contains(name)) {
                throw new IllegalArgumentException("unknown filesystem open option: " + name);
            }
        }

        boolean read = booleanOption(slots, "read", true);
        boolean write = booleanOption(slots, "write", false);
        boolean create = booleanOption(slots, "create", false);
        boolean createNew = booleanOption(slots, "createNew", false);
        boolean truncate = booleanOption(slots, "truncate", false);
        boolean append = booleanOption(slots, "append", false);

        if (!read && !write) {
            throw new IllegalArgumentException("filesystem open requires read or write access");
        }
        if (create && createNew) {
            throw new IllegalArgumentException("create and createNew are mutually exclusive");
        }
        if (append && !write) {
            throw new IllegalArgumentException("append requires write access");
        }
        if (truncate && !write) {
            throw new IllegalArgumentException("truncate requires write access");
        }
        if (append && truncate) {
            throw new IllegalArgumentException("append and truncate are mutually exclusive");
        }

        Creation creation =
                createNew ? Creation.CREATE_NEW : create ? Creation.CREATE : Creation.EXISTING;
        Placement placement = append ? Placement.APPEND : Placement.POSITIONED;
        return new ProtosFilesystemOpenOptions(read, write, creation, truncate, placement);
    }

    public boolean readAccess() {
        return read;
    }

    public boolean writeAccess() {
        return write;
    }

    public Creation creation() {
        return creation;
    }

    public boolean truncateInitialContent() {
        return truncate;
    }

    public Placement placement() {
        return placement;
    }

    private static boolean booleanOption(
            Map<String, Object> slots, String name, boolean defaultValue) {
        if (!slots.containsKey(name)) {
            return defaultValue;
        }
        Object value = slots.get(name);
        if (value == ProtosBooleanValue.TRUE) {
            return true;
        }
        if (value == ProtosBooleanValue.FALSE) {
            return false;
        }
        throw new IllegalArgumentException(
                "filesystem open option " + name + " must be canonical Boolean");
    }
}
