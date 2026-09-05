/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.*;
import java.util.List;
import java.util.Objects;

/** Installs standard byte-I/O message surfaces on one receiver. */
public final class ProtosStandardByteIoProtocol {
    private ProtosStandardByteIoProtocol() {}

    /** I014-B sequential ByteReadable/ByteWritable surface. */
    public static ProtosByteIoFlow install(ProtosObjectValue receiver,ProtosObjectValue bytesPrototype,
            ProtosActivation activation,ProtosByteIoFlow.Backend backend){
        Objects.requireNonNull(receiver);Objects.requireNonNull(bytesPrototype);Objects.requireNonNull(activation);Objects.requireNonNull(backend);
        ensureAbsent(receiver,"read","write");
        ProtosByteIoFlow flow=new ProtosByteIoFlow(receiver,bytesPrototype,activation,backend);
        installTransfer(receiver,flow);
        return flow;
    }

    /**
     * I014-C positioned byte-I/O surface. Capabilities are explicit: callers
     * choose this installer only for a backend that can honestly provide all
     * Flushable/ByteSeekable/ByteSized/Truncatable contracts.
     */
    public static ProtosByteIoFlow installExtended(ProtosObjectValue receiver,ProtosObjectValue bytesPrototype,
            ProtosActivation activation,ProtosByteIoFlow.ExtendedBackend backend){
        Objects.requireNonNull(receiver);Objects.requireNonNull(bytesPrototype);Objects.requireNonNull(activation);Objects.requireNonNull(backend);
        ensureAbsent(receiver,"read","write","flush","position","seek","seekBy","seekToEnd","size","truncate");
        if(backend instanceof ProtosByteIoFlow.SyncBackend)ensureAbsent(receiver,"sync");
        ProtosByteIoFlow flow=new ProtosByteIoFlow(receiver,bytesPrototype,activation,backend);
        installTransfer(receiver,flow);
        receiver.createLocalSlot("flush",ProtosClosureValue.nativeClosure((a,args)->args.isEmpty()&&a.receiver()==receiver?flow.flush(a):invalid(a)));
        receiver.createLocalSlot("position",ProtosClosureValue.nativeClosure((a,args)->args.isEmpty()&&a.receiver()==receiver?flow.position(a):invalid(a)));
        receiver.createLocalSlot("seek",ProtosClosureValue.nativeClosure((a,args)->args.size()==1&&a.receiver()==receiver?flow.seek(a,args.get(0)):invalid(a)));
        receiver.createLocalSlot("seekBy",ProtosClosureValue.nativeClosure((a,args)->args.size()==1&&a.receiver()==receiver?flow.seekBy(a,args.get(0)):invalid(a)));
        receiver.createLocalSlot("seekToEnd",ProtosClosureValue.nativeClosure((a,args)->args.isEmpty()&&a.receiver()==receiver?flow.seekToEnd(a):invalid(a)));
        receiver.createLocalSlot("size",ProtosClosureValue.nativeClosure((a,args)->args.isEmpty()&&a.receiver()==receiver?flow.size(a):invalid(a)));
        receiver.createLocalSlot("truncate",ProtosClosureValue.nativeClosure((a,args)->args.size()==1&&a.receiver()==receiver?flow.truncate(a,args.get(0)):invalid(a)));
        if(backend instanceof ProtosByteIoFlow.SyncBackend)
            receiver.createLocalSlot("sync",ProtosClosureValue.nativeClosure((a,args)->args.isEmpty()&&a.receiver()==receiver?flow.sync(a):invalid(a)));
        return flow;
    }

    private static void installTransfer(ProtosObjectValue receiver,ProtosByteIoFlow flow){
        receiver.createLocalSlot("read",ProtosClosureValue.nativeClosure((a,args)->{
            if(a.receiver()!=receiver||args.size()!=1)return invalid(a);
            return flow.read(a,args.get(0));
        }));
        receiver.createLocalSlot("write",ProtosClosureValue.nativeClosure((a,args)->{
            if(a.receiver()!=receiver||args.size()!=1)return invalid(a);
            return flow.write(a,args.get(0));
        }));
    }
    private static void ensureAbsent(ProtosObjectValue receiver,String...names){
        for(String name:names)if(receiver.hasLocalSlot(name))
            throw new IllegalStateException("byte I/O receiver already defines "+name);
    }
    private static Object invalid(ProtosActivation a){
        ProtosFutureValue f=new ProtosFutureValue(a.prelude().orElseThrow().futurePrototype(),a.executionDomain());
        f.fail(ProtosCoreErrors.newOccurrence(a,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT));return f;
    }
}
