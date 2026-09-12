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

package com.guillermomolina.protos.lsp;

import com.guillermomolina.protos.source.SourceSpan;
import java.util.Objects;
import java.util.OptionalInt;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/** Maps Protos UTF-16 source offsets to the LSP default UTF-16 position model. */
final class ProtosLspSourcePositions {
    private ProtosLspSourcePositions() {}

    static Range range(String source, SourceSpan span) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(span, "span");
        if (span.endOffset() > source.length()) {
            throw new IllegalArgumentException("source span exceeds document length");
        }
        return new Range(
                position(source, span.startOffset()),
                position(source, span.endOffset()));
    }


    static OptionalInt offset(String source, Position position) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(position, "position");
        if (position.getLine() < 0 || position.getCharacter() < 0) {
            return OptionalInt.empty();
        }

        int currentLine = 0;
        int lineStart = 0;
        int index = 0;
        while (true) {
            if (currentLine == position.getLine()) {
                int lineEnd = lineStart;
                while (lineEnd < source.length()) {
                    char current = source.charAt(lineEnd);
                    if (current == '\r' || current == '\n') {
                        break;
                    }
                    lineEnd++;
                }
                int offset = lineStart + position.getCharacter();
                if (offset > lineEnd) {
                    return OptionalInt.empty();
                }
                return OptionalInt.of(offset);
            }

            if (index >= source.length()) {
                return OptionalInt.empty();
            }

            char current = source.charAt(index++);
            if (current == '\r') {
                if (index < source.length() && source.charAt(index) == '\n') {
                    index++;
                }
                currentLine++;
                lineStart = index;
            } else if (current == '\n') {
                currentLine++;
                lineStart = index;
            }
        }
    }

    static Position position(String source, int offset) {
        Objects.requireNonNull(source, "source");
        if (offset < 0 || offset > source.length()) {
            throw new IllegalArgumentException("offset outside document");
        }

        int line = 0;
        int lineStart = 0;
        for (int index = 0; index < offset; index++) {
            char current = source.charAt(index);
            if (current == '\r') {
                if (index + 1 < offset && source.charAt(index + 1) == '\n') {
                    index++;
                }
                line++;
                lineStart = index + 1;
            } else if (current == '\n') {
                line++;
                lineStart = index + 1;
            }
        }
        return new Position(line, offset - lineStart);
    }
}
