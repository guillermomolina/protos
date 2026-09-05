/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.*;
import java.util.*;

/** Standard Core BufferedReader/BufferedWriter factories and wrapper protocol surfaces. */
public final class ProtosStandardBufferedByteIoProtocol {
    private ProtosStandardBufferedByteIoProtocol() {}

    public static ProtosObjectValue readerFactory(ProtosObjectValue bytesPrototype, ProtosActivation bootstrap) {
        return factory(bytesPrototype, bootstrap, true);
    }
    public static ProtosObjectValue writerFactory(ProtosObjectValue bytesPrototype, ProtosActivation bootstrap) {
        return factory(bytesPrototype, bootstrap, false);
    }
    private static ProtosObjectValue factory(ProtosObjectValue bytesPrototype, ProtosActivation bootstrap, boolean reader) {
        Objects.requireNonNull(bytesPrototype); Objects.requireNonNull(bootstrap);
        ProtosObjectValue f=new ProtosObjectValue(ProtosObjectValue.rootObject());
        f.createLocalSlot("call",ProtosClosureValue.nativeClosure((a,args)->construct(a,args,bytesPrototype,reader,false)));
        f.createLocalSlot("owning",ProtosClosureValue.nativeClosure((a,args)->construct(a,args,bytesPrototype,reader,true)));
        f.freeze(); return f;
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
