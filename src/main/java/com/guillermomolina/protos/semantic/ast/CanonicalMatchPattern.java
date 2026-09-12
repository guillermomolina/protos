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

package com.guillermomolina.protos.semantic.ast;

import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Backend-neutral compiler IR for ratified match-pattern structure. */
public sealed interface CanonicalMatchPattern
        permits CanonicalMatchPattern.Binder,
                CanonicalMatchPattern.Wildcard,
                CanonicalMatchPattern.Alias,
                CanonicalMatchPattern.Or,
                CanonicalMatchPattern.Value,
                CanonicalMatchPattern.ArrayPattern,
                CanonicalMatchPattern.MapPattern {
    SourceSpan span();

    record Binder(String name, SourceSpan span) implements CanonicalMatchPattern {
        public Binder {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(span, "span");
        }
    }

    record Wildcard(SourceSpan span) implements CanonicalMatchPattern {
        public Wildcard {
            Objects.requireNonNull(span, "span");
        }
    }

    record Alias(String name, CanonicalMatchPattern pattern, SourceSpan span)
            implements CanonicalMatchPattern {
        public Alias {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(span, "span");
        }
    }

    record Or(List<CanonicalMatchPattern> alternatives, SourceSpan span)
            implements CanonicalMatchPattern {
        public Or {
            alternatives = List.copyOf(Objects.requireNonNull(alternatives, "alternatives"));
            if (alternatives.size() < 2) {
                throw new IllegalArgumentException(
                        "canonical OR pattern requires at least two alternatives");
            }
            Objects.requireNonNull(span, "span");
        }
    }

    record Value(
            CanonicalExpression matcher,
            Optional<CaptureInterface> captureInterface,
            SourceSpan span)
            implements CanonicalMatchPattern {
        public Value {
            Objects.requireNonNull(matcher, "matcher");
            captureInterface = Objects.requireNonNull(captureInterface, "captureInterface");
            Objects.requireNonNull(span, "span");
        }
    }

    record CaptureInterface(
            List<String> requiredNames,
            Optional<String> restName,
            SourceSpan span) {
        public CaptureInterface {
            requiredNames =
                    List.copyOf(Objects.requireNonNull(requiredNames, "requiredNames"));
            restName = Objects.requireNonNull(restName, "restName");
            if (requiredNames.isEmpty() && restName.isEmpty()) {
                throw new IllegalArgumentException(
                        "canonical capture interface cannot be empty");
            }
            Objects.requireNonNull(span, "span");
        }

        public boolean variableArity() {
            return restName.isPresent();
        }

        public List<String> declaredNames() {
            if (restName.isEmpty()) {
                return requiredNames;
            }
            ArrayList<String> names = new ArrayList<>(requiredNames);
            names.add(restName.orElseThrow());
            return List.copyOf(names);
        }
    }

    record Remainder(Optional<CanonicalMatchPattern> pattern, SourceSpan span) {
        public Remainder {
            pattern = Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(span, "span");
        }
    }

    record ArrayPattern(
            List<CanonicalMatchPattern> prefix,
            Optional<Remainder> remainder,
            List<CanonicalMatchPattern> suffix,
            SourceSpan span)
            implements CanonicalMatchPattern {
        public ArrayPattern {
            prefix = List.copyOf(Objects.requireNonNull(prefix, "prefix"));
            remainder = Objects.requireNonNull(remainder, "remainder");
            suffix = List.copyOf(Objects.requireNonNull(suffix, "suffix"));
            Objects.requireNonNull(span, "span");
        }
    }

    record MapEntry(
            CanonicalExpression key,
            CanonicalMatchPattern valuePattern,
            SourceSpan span) {
        public MapEntry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(valuePattern, "valuePattern");
            Objects.requireNonNull(span, "span");
        }
    }

    record MapPattern(
            boolean exact,
            List<MapEntry> entries,
            Optional<Remainder> remainder,
            SourceSpan span)
            implements CanonicalMatchPattern {
        public MapPattern {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            remainder = Objects.requireNonNull(remainder, "remainder");
            if (exact && remainder.isPresent()) {
                throw new IllegalArgumentException(
                        "canonical exact Map pattern cannot contain a remainder");
            }
            Objects.requireNonNull(span, "span");
        }
    }
}
