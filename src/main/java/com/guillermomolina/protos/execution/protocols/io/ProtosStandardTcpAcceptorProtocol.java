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

import com.guillermomolina.protos.runtime.*;
import com.guillermomolina.protos.runtime.io.ProtosTcpAcceptorValue;
import java.util.List;
import java.util.Objects;

/** D217 shared protocol bridge for TcpAcceptor accept (I125-A). */
public final class ProtosStandardTcpAcceptorProtocol {
    private ProtosStandardTcpAcceptorProtocol() {}
    public static void install(ProtosObjectValue prototype) {
        Objects.requireNonNull(prototype,"prototype");
        if(prototype.parent().orElse(null)!=ProtosObjectValue.rootObject()) throw new IllegalArgumentException("standard TcpAcceptor protocol prototype must delegate directly to Object");
        if(!prototype.isOpen()||!prototype.localSlotsSnapshot().isEmpty()) throw new IllegalStateException("standard TcpAcceptor protocol prototype must be fresh and open");
        prototype.createLocalSlot("accept",ProtosClosureValue.nativeClosure((activation,supplied)->requireAcceptor(activation,supplied,prototype).acceptForRuntime(activation)));
    }

    private static ProtosTcpAcceptorValue requireAcceptor(ProtosActivation activation,List<?> supplied,ProtosObjectValue prototype) {
        if(!supplied.isEmpty() || !(activation.receiver() instanceof ProtosTcpAcceptorValue acceptor) || acceptor.parent().orElse(null)!=prototype || !acceptor.ownedByForRuntime(activation)) throw invalid(activation);
        return acceptor;
    }
    private static ProtosSignalException invalid(ProtosActivation activation){ return new ProtosSignalException(ProtosCoreErrors.newError(activation)); }
}
