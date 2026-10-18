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
package com.guillermomolina.protos.execution;

/**
 * Implementation-version identity of the running Protos distribution (LIB022-A).
 *
 * <p>The single precedence policy shared by the CLI {@code --version} output and
 * {@code std:runtime}: the packaged implementation version, then the distribution-supplied
 * {@value #IMPLEMENTATION_VERSION_ENV}, then {@code "development"}. Supplied versions are answered
 * verbatim, never trimmed, parsed or normalized.
 */
public final class ProtosVersionIdentity {
    public static final String IMPLEMENTATION_VERSION_ENV = "PROTOS_IMPLEMENTATION_VERSION";

    private ProtosVersionIdentity() {}

    public static String select(String packageVersion, String distributionVersion) {
        if (packageVersion != null && !packageVersion.isEmpty()) return packageVersion;
        if (distributionVersion != null && !distributionVersion.isEmpty()) {
            return distributionVersion;
        }
        return "development";
    }

    public static String current() {
        return select(
                ProtosVersionIdentity.class.getPackage().getImplementationVersion(),
                System.getenv(IMPLEMENTATION_VERSION_ENV));
    }
}
