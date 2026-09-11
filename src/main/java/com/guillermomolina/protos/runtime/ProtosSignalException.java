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

import com.oracle.truffle.api.exception.AbstractTruffleException;
import java.util.Objects;
import java.util.Optional;

public final class ProtosSignalException extends AbstractTruffleException {
    private final ProtosObjectValue error;
    private ProtosDynamicControlState.Frame selectedHandlerFrame;

    public ProtosSignalException(ProtosObjectValue error) {
        super();
        this.error = Objects.requireNonNull(error, "error");
    }

    public ProtosObjectValue error() {
        return error;
    }

    public Optional<ProtosDynamicControlState.Frame> selectedHandlerFrame() {
        return Optional.ofNullable(selectedHandlerFrame);
    }

    void selectHandlerFrame(ProtosDynamicControlState.Frame frame) {
        Objects.requireNonNull(frame, "frame");
        if (selectedHandlerFrame != null && selectedHandlerFrame != frame) {
            throw new IllegalStateException("Error transfer already selected another handler");
        }
        selectedHandlerFrame = frame;
    }
}
