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
package com.guillermomolina.protos.source;

import java.util.Objects;

/**
 * Zero-based line and UTF-16 character position of one source offset.
 *
 * <p>{@code \n}, {@code \r}, and {@code \r\n} each terminate one logical line;
 * {@code character} counts UTF-16 code units from the line start. This is the
 * protocol-neutral mapping shared by the language server and the lint CLI.</p>
 */
public record SourcePosition(int line, int character) {

    public static SourcePosition at(String source, int offset) {
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
        return new SourcePosition(line, offset - lineStart);
    }
}
