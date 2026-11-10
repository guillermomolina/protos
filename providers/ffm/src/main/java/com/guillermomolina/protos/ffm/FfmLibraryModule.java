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
package com.guillermomolina.protos.ffm;

import java.lang.foreign.SymbolLookup;
import java.util.Objects;

/**
 * Module handle of one authorized library in one session ({@code import("ffi:<library>")}).
 *
 * <p>Its members are the provider-private declaration operations of {@link FfmValueOperations};
 * they are external support machinery for a future Protos-source {@code std:ffi}, not that
 * normative API.
 */
final class FfmLibraryModule {
    private final FfmSession session;
    private final String library;
    private final SymbolLookup lookup;

    FfmLibraryModule(FfmSession session, String library, SymbolLookup lookup) {
        this.session = Objects.requireNonNull(session, "session");
        this.library = Objects.requireNonNull(library, "library");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    FfmSession session() {
        return session;
    }

    String library() {
        return library;
    }

    SymbolLookup lookup() {
        return lookup;
    }

    @Override
    public String toString() {
        return "ffi-library(" + library + ")";
    }
}
