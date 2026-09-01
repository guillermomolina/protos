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

package com.guillermomolina.protos.lexer;

public enum TokenType {
    // Identifiers and literals
    IDENTIFIER,
    NUMBER,
    STRING,

    // Reserved intrinsics
    THIS,
    CONTEXT,
    ARGS,
    NULL,
    TRUE,
    FALSE,
    SUPER,

    // Structural punctuation
    LPAREN,         // (
    RPAREN,         // )
    LBRACE,         // {
    RBRACE,         // }
    LBRACKET,       // [
    RBRACKET,       // ]
    DOT,            // .
    COMMA,          // ,
    COLON,          // :
    SEMICOLON,      // ;

    // Operators (standard)
    EQUALS,         // =
    FAT_ARROW,      // =>
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    PERCENT,        // %
    BANG,           // !
    LESS,           // <
    LESS_EQUAL,     // <=
    GREATER,        // >
    GREATER_EQUAL,  // >=
    DOUBLE_EQUALS,  // ==
    TRIPLE_EQUALS,  // ===
    NOT_EQUALS,     // !=
    NOT_EQUALS_2,   // !==
    AND,            // &&
    OR,             // ||
    ELLIPSIS,       // ...
    CARET,          // ^ (non-local return)

    // Custom operators
    CUSTOM_OPERATOR,

    // Control
    NEWLINE,
    EOF
}
