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

package com.guillermomolina.protos.parser;

import com.guillermomolina.protos.lexer.TokenOccurrence;
import com.guillermomolina.protos.lexer.TokenType;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.Objects;

public final class ParseError extends RuntimeException {
    private final SourceSpan span;
    private final boolean unexpectedEndOfSource;

    public ParseError(String message, SourceSpan span) {
        this(message, span, false);
    }

    private ParseError(String message, SourceSpan span, boolean unexpectedEndOfSource) {
        super(Objects.requireNonNull(message, "message"));
        this.span = Objects.requireNonNull(span, "span");
        this.unexpectedEndOfSource = unexpectedEndOfSource;
    }

    public SourceSpan span() {
        return span;
    }

    public boolean isUnexpectedEndOfSource() {
        return unexpectedEndOfSource;
    }

    static ParseError expected(String expectation, TokenOccurrence actual) {
        return new ParseError(
                "Expected " + expectation + " but found " + actual.token().type(),
                actual.span(),
                actual.token().type() == TokenType.EOF);
    }
}
