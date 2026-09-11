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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * JVM entry point for the dedicated stdio language-server host.
 *
 * <p>D070 makes {@code protos language-server} the public tool-facing launcher.
 * This class remains the internal JVM host behind that stable launcher contract.</p>
 */
public final class ProtosLanguageServerMain {
    private ProtosLanguageServerMain() {}

    public static void main(String[] args) throws Exception {
        run(System.in, System.out);
    }

    /** Runs one client-owned standard-LSP session over the supplied byte streams. */
    public static void run(InputStream input, OutputStream output) throws Exception {
        ProtosLanguageServer server = new ProtosLanguageServer(System::exit);
        ProtosLanguageServerStdio.start(
                        server,
                        Objects.requireNonNull(input, "input"),
                        Objects.requireNonNull(output, "output"))
                .get();
    }
}
