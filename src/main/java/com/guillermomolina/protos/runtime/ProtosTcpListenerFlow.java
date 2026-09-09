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

import java.util.Objects;

/** Host-neutral Closable lifecycle state for one TcpListener. */
public final class ProtosTcpListenerFlow {
    @FunctionalInterface
    public interface Backend { void close(CloseCompletion completion); }
    public interface CloseCompletion { void succeeded(); void failed(); }

    private final ProtosTcpListenerValue receiver;
    private final ProtosActorExecutionDomain domain;
    private final Backend backend;
    private final ProtosIoLifecycle lifecycle;
    private volatile ProtosActivation closeActivation;

    public ProtosTcpListenerFlow(ProtosTcpListenerValue receiver, ProtosActivation activation, Backend backend) {
        this.receiver=Objects.requireNonNull(receiver,"receiver");
        Objects.requireNonNull(activation,"activation");
        this.domain=activation.executionDomain();
        this.backend=Objects.requireNonNull(backend,"backend");
        this.lifecycle=new ProtosIoLifecycle(receiver,activation.prelude().orElseThrow().futurePrototype(),domain,this::startCloseRelease);
    }

    public synchronized ProtosFutureValue close(ProtosActivation activation) {
        requireDomain(activation);
        if(closeActivation==null) closeActivation=activation;
        return lifecycle.close(activation);
    }

    public ProtosIoLifecycle lifecycleForRuntime() { return lifecycle; }
    ProtosIoLifecycle.State lifecycleStateForTesting() { return lifecycle.state(); }

    private void startCloseRelease(ProtosIoLifecycle.ReleaseCompletion completion) {
        ProtosActivation activation=closeActivation;
        if(activation==null) throw new IllegalStateException("TCP listener close release started without a close activation");
        try {
            backend.close(new CloseCompletion(){
                @Override public void succeeded(){ completion.succeeded(); }
                @Override public void failed(){ completion.failed(ioError(activation)); }
            });
        } catch(RuntimeException ex) { completion.failed(ioError(activation)); }
    }

    private void requireDomain(ProtosActivation activation) {
        Objects.requireNonNull(activation,"activation");
        if(activation.executionDomain()!=domain) throw new IllegalArgumentException("TcpListener operation belongs to another Actor domain");
    }
    private static ProtosObjectValue ioError(ProtosActivation activation) {
        return ProtosCoreErrors.newOccurrence(activation,ProtosCoreErrors.StandardError.I_O_ERROR);
    }
}
