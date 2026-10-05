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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosHostResourceCloseTest {
    @Test
    void successfulHostCloseReportsTrueAfterClosingExactlyOnce() {
        AtomicInteger closes = new AtomicInteger();

        assertTrue(ProtosHostResourceClose.closePhysically(closes::incrementAndGet));
        assertEquals(1, closes.get());
    }

    @Test
    void hostIoFailureReportsFalse() {
        assertFalse(
                ProtosHostResourceClose.closePhysically(
                        () -> {
                            throw new IOException("close failed");
                        }));
    }

    @Test
    void hostRuntimeFailurePropagatesUnchangedToTheFlowTranslation() {
        IllegalStateException failure = new IllegalStateException("backend defect");

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ProtosHostResourceClose.closePhysically(
                                        () -> {
                                            throw failure;
                                        })));
    }
}
