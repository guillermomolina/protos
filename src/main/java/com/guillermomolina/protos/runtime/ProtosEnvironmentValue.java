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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable standardized Environment snapshot belonging to one logical Protos Process.
 *
 * <p>The snapshot retains the host/native spelling and value representation captured at Process
 * bootstrap. Acquisition validity requires only that the captured native mapping be single-valued
 * under its own native-name identity rules. Portable String conversion is deliberately deferred:
 * {@code contains} need not decode a value, {@code get} decodes only the selected value, and
 * {@code each} validates every name/value pair before its first user callback.
 *
 * <p>The NativeNameDomain is trusted host representation state. Its methods must be deterministic,
 * side-effect-free with respect to Protos semantics, thread-safe, and stable for the lifetime of
 * the snapshot. They invoke no Protos code.
 */
public final class ProtosEnvironmentValue implements ProtosRepresentedValue {
    public record NativeEntry(String name, String value) {
        public NativeEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    public record PortableEntry(ProtosStringValue name, ProtosStringValue value) {
        public PortableEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    public interface NativeNameDomain {
        boolean sameCapturedName(String leftNativeName, String rightNativeName);
        boolean isQueryRepresentable(String portableName);
        boolean matchesQuery(String capturedNativeName, String portableName);
    }

    private final ProtosObjectValue prototype;
    private final NativeNameDomain nameDomain;
    private final List<NativeEntry> entries;
    private volatile List<PortableEntry> portableEntries;

    private ProtosEnvironmentValue(
            ProtosObjectValue prototype,
            NativeNameDomain nameDomain,
            List<NativeEntry> entries) {
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.nameDomain = Objects.requireNonNull(nameDomain, "nameDomain");
        this.entries = List.copyOf(entries);
    }

    static ProtosEnvironmentValue captureForRuntime(
            ProtosObjectValue prototype,
            NativeNameDomain nameDomain,
            List<NativeEntry> hostEntries) {
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(nameDomain, "nameDomain");
        Objects.requireNonNull(hostEntries, "hostEntries");

        List<NativeEntry> captured = List.copyOf(hostEntries);
        try {
            for (int left = 0; left < captured.size(); left++) {
                for (int right = left + 1; right < captured.size(); right++) {
                    if (nameDomain.sameCapturedName(
                            captured.get(left).name(),
                            captured.get(right).name())) {
                        throw new IllegalArgumentException(
                                "duplicate-equivalent native environment names");
                    }
                }
            }
        } catch (RuntimeException invalidDomainOrMapping) {
            if (invalidDomainOrMapping instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "native environment name domain failed duplicate validation",
                    invalidDomainOrMapping);
        }

        return new ProtosEnvironmentValue(prototype, nameDomain, captured);
    }

    public Optional<ProtosStringValue> getForRuntime(String portableName) {
        NativeEntry entry = findForRuntime(portableName);
        if (entry == null) {
            return Optional.empty();
        }
        if (!isUnicodeScalarString(entry.value())) {
            throw new IllegalArgumentException(
                    "environment value is not representable as Protos Unicode text");
        }
        return Optional.of(new ProtosStringValue(entry.value()));
    }

    public boolean containsForRuntime(String portableName) {
        return findForRuntime(portableName) != null;
    }

    public List<PortableEntry> portableEntriesForRuntime() {
        List<PortableEntry> cached = portableEntries;
        if (cached != null) {
            return cached;
        }

        ArrayList<PortableEntry> converted = new ArrayList<>(entries.size());
        for (NativeEntry entry : entries) {
            if (!isUnicodeScalarString(entry.name())
                    || !isUnicodeScalarString(entry.value())) {
                throw new IllegalArgumentException(
                        "environment entry is not representable as Protos Unicode text");
            }
            converted.add(
                    new PortableEntry(
                            new ProtosStringValue(entry.name()),
                            new ProtosStringValue(entry.value())));
        }
        converted.sort(
                (left, right) ->
                        compareUnicodeScalars(
                                left.name().value(),
                                right.name().value()));

        List<PortableEntry> result = List.copyOf(converted);
        portableEntries = result;
        return result;
    }

    ProtosEnvironmentValue rematerializeForActorTransfer() {
        return new ProtosEnvironmentValue(prototype, nameDomain, entries);
    }

    public ProtosEnvironmentValue rematerializeForParallelTransfer() {
        return new ProtosEnvironmentValue(prototype, nameDomain, entries);
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }

    private NativeEntry findForRuntime(String portableName) {
        requireRepresentableQuery(portableName);

        NativeEntry selected = null;
        try {
            for (NativeEntry entry : entries) {
                if (nameDomain.matchesQuery(entry.name(), portableName)) {
                    if (selected != null) {
                        throw new IllegalArgumentException(
                                "native name domain produced an ambiguous query result");
                    }
                    selected = entry;
                }
            }
        } catch (RuntimeException invalidDomain) {
            if (invalidDomain instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "native environment name-domain query failed",
                    invalidDomain);
        }
        return selected;
    }

    private void requireRepresentableQuery(String portableName) {
        if (!isUnicodeScalarString(portableName)) {
            throw new IllegalArgumentException(
                    "environment query is not valid Protos Unicode text");
        }
        final boolean representable;
        try {
            representable = nameDomain.isQueryRepresentable(portableName);
        } catch (RuntimeException invalidDomain) {
            throw new IllegalArgumentException(
                    "native environment name-domain query validation failed",
                    invalidDomain);
        }
        if (!representable) {
            throw new IllegalArgumentException(
                    "environment query cannot be represented as one native name");
        }
    }

    static int compareUnicodeScalarsForTesting(String left, String right) {
        return compareUnicodeScalars(left, right);
    }

    private static int compareUnicodeScalars(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftScalar = left.codePointAt(leftIndex);
            int rightScalar = right.codePointAt(rightIndex);
            if (leftScalar != rightScalar) {
                return Integer.compare(leftScalar, rightScalar);
            }
            leftIndex += Character.charCount(leftScalar);
            rightIndex += Character.charCount(rightScalar);
        }
        if (leftIndex == left.length() && rightIndex == right.length()) {
            return 0;
        }
        return leftIndex == left.length() ? -1 : 1;
    }

    private static boolean isUnicodeScalarString(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }
}
