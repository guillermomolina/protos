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
import java.util.*;

/** Standard Core BufferedReader/BufferedWriter factories and wrapper protocol surfaces. */
public final class ProtosStandardBufferedByteIoProtocol {
    private ProtosStandardBufferedByteIoProtocol() {}

    public static ProtosObjectValue installReaderFactory(
            ProtosObjectValue factory,
            ProtosObjectValue bytesPrototype,
            ProtosActivation bootstrap) {
        return installFactory(factory, bytesPrototype, bootstrap, true);
    }

    public static ProtosObjectValue installWriterFactory(
            ProtosObjectValue factory,
            ProtosObjectValue bytesPrototype,
            ProtosActivation bootstrap) {
        return installFactory(factory, bytesPrototype, bootstrap, false);
    }

    private static ProtosObjectValue installFactory(
            ProtosObjectValue factory,
            ProtosObjectValue bytesPrototype,
            ProtosActivation bootstrap,
            boolean reader) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(bytesPrototype, "bytesPrototype");
        Objects.requireNonNull(bootstrap, "bootstrap");
        if (factory.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "Core buffered byte factory must delegate directly to Object");
        }
        if (!factory.isOpen() || !factory.localSlotsSnapshot().isEmpty()) {
            throw new IllegalArgumentException(
                    "source-created Core buffered byte factory must begin open and without local slots");
        }

        factory.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (activation, args) ->
                                construct(
                                        activation,
                                        args,
                                        bytesPrototype,
                                        reader,
                                        false)));
        factory.createLocalSlot(
                "owning",
                ProtosClosureValue.nativeClosure(
                        (activation, args) ->
                                construct(
                                        activation,
                                        args,
                                        bytesPrototype,
                                        reader,
                                        true)));
        return factory.freeze();
    }
    private static Object construct(ProtosActivation a,List<?> args,ProtosObjectValue bp,boolean reader,boolean owning) {
        if(args.size()!=1 || !(args.get(0) instanceof ProtosObjectValue target) ||
                target.lookupSlot(reader?"read":"write").isEmpty() ||
                (owning && target.lookupSlot("close").isEmpty()))
            throw new ProtosSignalException(ProtosCoreErrors.newOccurrence(a,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT));
        ProtosObjectValue wrapper=new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosBufferedByteIo io=reader?ProtosBufferedByteIo.reader(wrapper,target,bp,a,owning):ProtosBufferedByteIo.writer(wrapper,target,bp,a,owning);
        if(reader) wrapper.createLocalSlot("read",ProtosClosureValue.nativeClosure((x,xs)->xs.size()==1&&x.receiver()==wrapper?io.read(x,xs.get(0)):invalid(x)));
        else {
            wrapper.createLocalSlot("write",ProtosClosureValue.nativeClosure((x,xs)->xs.size()==1&&x.receiver()==wrapper?io.write(x,xs.get(0)):invalid(x)));
            wrapper.createLocalSlot("flush",ProtosClosureValue.nativeClosure((x,xs)->xs.isEmpty()&&x.receiver()==wrapper?io.flush(x):invalid(x)));
        }
        wrapper.createLocalSlot("close",ProtosClosureValue.nativeClosure((x,xs)->xs.isEmpty()&&x.receiver()==wrapper?io.close(x):invalid(x)));
        return wrapper;
    }
    private static Object invalid(ProtosActivation a){
        ProtosFutureValue f=new ProtosFutureValue(a.prelude().orElseThrow().futurePrototype(),a.executionDomain());
        f.fail(ProtosCoreErrors.newOccurrence(a,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT)); return f;
    }
}
