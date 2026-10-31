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
package com.guillermomolina.protos.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure candidate selection for existing REPL meta-commands.
 *
 * <p>This component does not install a terminal completer, inspect guest
 * objects, evaluate source or define session-binding visibility. It accepts
 * the complete edited line and a UTF-16 cursor offset, preserving the text
 * on both sides of the cursor when selecting matching commands.</p>
 */
final class ProtosReplCompletion {
    private ProtosReplCompletion() {}

    static List<String> commandCandidates(String line, int cursor) {
        Objects.requireNonNull(line, "line");

        if (cursor < 1 || cursor > line.length() || line.charAt(0) != ':') {
            return List.of();
        }

        String prefix = line.substring(0, cursor);
        String suffix = line.substring(cursor);
        List<String> candidates = new ArrayList<>();

        for (String command : ProtosCli.REPL_COMMANDS) {
            if (command.length() >= line.length()
                    && command.startsWith(prefix)
                    && command.endsWith(suffix)) {
                candidates.add(command);
            }
        }

        return List.copyOf(candidates);
    }
}
