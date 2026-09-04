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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosReturnHomeTest {
    @Test
    void tracksActiveAndCompletedLifecycle() {
        ProtosReturnHome home = new ProtosReturnHome();

        assertTrue(home.isActive());
        home.complete();
        assertFalse(home.isActive());
        assertThrows(IllegalStateException.class, home::complete);
    }

    @Test
    void objectConstructionPreservesEnclosingReturnHome() {
        ProtosObjectValue context =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosReturnHome home = new ProtosReturnHome();
        ProtosActivation enclosing =
                ProtosActivation.withReturnHome(
                        context, List.of(), receiver, home);
        ProtosObjectValue object =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        ProtosActivation construction =
                ProtosActivation.forObjectConstruction(object, enclosing);

        assertSame(home, construction.returnHome().orElseThrow());
    }
}
