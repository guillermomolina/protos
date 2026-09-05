/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.*;
import java.util.List;
import java.util.Objects;

/** Installs the standard ByteReadable.read / ByteWritable.write message surface on one receiver. */
public final class ProtosStandardByteIoProtocol {
    private ProtosStandardByteIoProtocol() {}
    public static ProtosByteIoFlow install(ProtosObjectValue receiver,ProtosObjectValue bytesPrototype,
            ProtosActivation activation,ProtosByteIoFlow.Backend backend){
        Objects.requireNonNull(receiver);Objects.requireNonNull(bytesPrototype);Objects.requireNonNull(activation);Objects.requireNonNull(backend);
        if(receiver.hasLocalSlot("read")||receiver.hasLocalSlot("write"))throw new IllegalStateException("byte I/O receiver already defines read/write");
        ProtosByteIoFlow flow=new ProtosByteIoFlow(receiver,bytesPrototype,activation,backend);
        receiver.createLocalSlot("read",ProtosClosureValue.nativeClosure((a,args)->{
            if(a.receiver()!=receiver||args.size()!=1)return invalid(a);
            return flow.read(a,args.get(0));
        }));
        receiver.createLocalSlot("write",ProtosClosureValue.nativeClosure((a,args)->{
            if(a.receiver()!=receiver||args.size()!=1)return invalid(a);
            return flow.write(a,args.get(0));
        }));
        return flow;
    }
    private static Object invalid(ProtosActivation a){
        ProtosFutureValue f=new ProtosFutureValue(a.prelude().orElseThrow().futurePrototype(),a.executionDomain());
        f.fail(ProtosCoreErrors.newOccurrence(a,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT));return f;
    }
}
