/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;
import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProtosStandardByteIoPositioningProtocolTest {
    private static ProtosPrelude core() throws Exception{return new ProtosCoreBootstrap().bootstrap(Path.of("protos","lib","core"));}
    private static ProtosIntegerValue i(long n){return new ProtosIntegerValue(BigInteger.valueOf(n));}
    private static ProtosBytesValue bytes(ProtosObjectValue p,int...v){var b=new ProtosBytesValue(p);for(int x:v)b.indexedAdd(i(x));return b;}

    @Test void extendedSurfaceReturnsFuturesAndValidatesArguments()throws Exception{
        var p=core();var a=p.newModuleActivation();var bp=new ProtosObjectValue(ProtosObjectValue.rootObject());ProtosStandardBytesProtocol.install(bp);
        var r=new ProtosObjectValue(ProtosObjectValue.rootObject());var b=new MemoryBackend();
        ProtosStandardByteIoProtocol.installExtended(r,bp,a,b);
        for(String m:List.of("flush","position","seekToEnd","size")){
            var f=(ProtosFutureValue)ProtosInvocation.invokeMessage(r,m,List.of(),a);assertNotNull(f);
        }
        var bad=(ProtosFutureValue)ProtosInvocation.invokeMessage(r,"seek",List.of(i(-1)),a);
        assertEquals(ProtosFutureValue.State.FAILED,bad.state());
        var badTruncate=(ProtosFutureValue)ProtosInvocation.invokeMessage(r,"truncate",List.of(i(-1)),a);
        assertEquals(ProtosFutureValue.State.FAILED,badTruncate.state());
    }

    @Test void writeFlushAndSizeShareOneOrderingDomain()throws Exception{
        var p=core();var a=p.newModuleActivation();var bp=new ProtosObjectValue(ProtosObjectValue.rootObject());ProtosStandardBytesProtocol.install(bp);
        var r=new ProtosObjectValue(ProtosObjectValue.rootObject());var b=new MemoryBackend();b.holdWrite=true;
        ProtosStandardByteIoProtocol.installExtended(r,bp,a,b);
        var write=(ProtosFutureValue)ProtosInvocation.invokeMessage(r,"write",List.of(bytes(bp,1,2,3)),a);
        var flush=(ProtosFutureValue)ProtosInvocation.invokeMessage(r,"flush",List.of(),a);
        var size=(ProtosFutureValue)ProtosInvocation.invokeMessage(r,"size",List.of(),a);
        assertEquals(ProtosFutureValue.State.PENDING,write.state());
        assertEquals(0,b.flushCalls);assertEquals(0,b.sizeCalls);
        b.completeHeldWrite();
        assertSame(r,write.resolvedValue().orElseThrow());
        assertEquals(1,b.flushCalls);assertSame(r,flush.resolvedValue().orElseThrow());
        assertEquals(BigInteger.valueOf(3),((ProtosIntegerValue)size.resolvedValue().orElseThrow()).value());
    }

    @Test void seekReadWriteAndPositionObserveLogicalOrder()throws Exception{
        var p=core();var a=p.newModuleActivation();var bp=new ProtosObjectValue(ProtosObjectValue.rootObject());ProtosStandardBytesProtocol.install(bp);
        var r=new ProtosObjectValue(ProtosObjectValue.rootObject());var b=new MemoryBackend(new byte[]{10,11,12,13});
        ProtosStandardByteIoProtocol.installExtended(r,bp,a,b);
        assertEquals(BigInteger.valueOf(2),value(ProtosInvocation.invokeMessage(r,"seek",List.of(i(2)),a)));
        var read=(ProtosFutureValue)ProtosInvocation.invokeMessage(r,"read",List.of(i(1)),a);
        assertEquals(12,((ProtosIntegerValue)((ProtosBytesValue)read.resolvedValue().orElseThrow()).indexedAt(BigInteger.ZERO)).value().intValue());
        assertEquals(BigInteger.valueOf(3),value(ProtosInvocation.invokeMessage(r,"position",List.of(),a)));
        ProtosInvocation.invokeMessage(r,"write",List.of(bytes(bp,99)),a);
        assertEquals(BigInteger.valueOf(4),value(ProtosInvocation.invokeMessage(r,"position",List.of(),a)));
    }

    @Test void truncateShrinksDoesNotExtendAndPreservesPosition()throws Exception{
        var p=core();var a=p.newModuleActivation();var bp=new ProtosObjectValue(ProtosObjectValue.rootObject());ProtosStandardBytesProtocol.install(bp);
        var r=new ProtosObjectValue(ProtosObjectValue.rootObject());var b=new MemoryBackend(new byte[]{1,2,3,4});
        ProtosStandardByteIoProtocol.installExtended(r,bp,a,b);
        ProtosInvocation.invokeMessage(r,"seek",List.of(i(4)),a);
        var t=(ProtosFutureValue)ProtosInvocation.invokeMessage(r,"truncate",List.of(i(2)),a);assertSame(r,t.resolvedValue().orElseThrow());
        assertEquals(BigInteger.valueOf(2),value(ProtosInvocation.invokeMessage(r,"size",List.of(),a)));
        assertEquals(BigInteger.valueOf(4),value(ProtosInvocation.invokeMessage(r,"position",List.of(),a)));
        ProtosInvocation.invokeMessage(r,"truncate",List.of(i(9)),a);
        assertEquals(BigInteger.valueOf(2),value(ProtosInvocation.invokeMessage(r,"size",List.of(),a)));
    }

    @Test void cancelledQueuedSeekHasNoEffect()throws Exception{
        var p=core();var a=p.newModuleActivation();var bp=new ProtosObjectValue(ProtosObjectValue.rootObject());ProtosStandardBytesProtocol.install(bp);
        var r=new ProtosObjectValue(ProtosObjectValue.rootObject());var b=new MemoryBackend();b.holdWrite=true;
        ProtosStandardByteIoProtocol.installExtended(r,bp,a,b);
        ProtosInvocation.invokeMessage(r,"write",List.of(bytes(bp,1)),a);
        var seek=(ProtosFutureValue)ProtosInvocation.invokeMessage(r,"seek",List.of(i(20)),a);
        assertTrue(seek.cancelRequest());b.completeHeldWrite();
        assertEquals(ProtosFutureValue.State.CANCELLED,seek.state());
        assertEquals(BigInteger.ONE,value(ProtosInvocation.invokeMessage(r,"position",List.of(),a)));
    }

    private static BigInteger value(Object x){
        var f=(ProtosFutureValue)x;return ((ProtosIntegerValue)f.resolvedValue().orElseThrow()).value();
    }

    private static final class MemoryBackend implements ProtosByteIoFlow.ExtendedBackend{
        byte[]data;int position;boolean holdWrite;byte[]held;ProtosByteIoFlow.WriteCompletion heldCompletion;
        int flushCalls,sizeCalls;
        MemoryBackend(){this(new byte[0]);}
        MemoryBackend(byte[]d){data=d.clone();}
        public ProtosByteIoFlow.Cancellation read(int max,ProtosByteIoFlow.ReadCompletion c){
            if(position>=data.length){c.eof();return()->{};}
            int n=Math.min(max,data.length-position);byte[]out=Arrays.copyOfRange(data,position,position+n);position+=n;c.data(out);return()->{};
        }
        public ProtosByteIoFlow.Cancellation write(byte[]b,ProtosByteIoFlow.WriteCompletion c){
            if(holdWrite){held=b.clone();heldCompletion=c;return()->{};}writeNow(b);c.succeeded();return()->{};
        }
        void completeHeldWrite(){var b=held;var c=heldCompletion;held=null;heldCompletion=null;holdWrite=false;writeNow(b);c.succeeded();}
        private void writeNow(byte[]b){
            int end=position+b.length;if(end>data.length)data=Arrays.copyOf(data,end);
            System.arraycopy(b,0,data,position,b.length);position=end;
        }
        public ProtosByteIoFlow.Cancellation flush(ProtosByteIoFlow.ReceiverCompletion c){flushCalls++;c.succeeded();return()->{};}
        public ProtosByteIoFlow.Cancellation position(ProtosByteIoFlow.IntegerCompletion c){c.succeeded(BigInteger.valueOf(position));return()->{};}
        public ProtosByteIoFlow.Cancellation seek(BigInteger p,ProtosByteIoFlow.IntegerCompletion c){position=p.intValueExact();c.succeeded(p);return()->{};}
        public ProtosByteIoFlow.Cancellation seekBy(BigInteger o,ProtosByteIoFlow.IntegerCompletion c){
            BigInteger p=BigInteger.valueOf(position).add(o);if(p.signum()<0){c.failed();return()->{};}position=p.intValueExact();c.succeeded(p);return()->{};
        }
        public ProtosByteIoFlow.Cancellation seekToEnd(ProtosByteIoFlow.IntegerCompletion c){position=data.length;c.succeeded(BigInteger.valueOf(position));return()->{};}
        public ProtosByteIoFlow.Cancellation size(ProtosByteIoFlow.IntegerCompletion c){sizeCalls++;c.succeeded(BigInteger.valueOf(data.length));return()->{};}
        public ProtosByteIoFlow.Cancellation truncate(BigInteger n,ProtosByteIoFlow.ReceiverCompletion c){
            int x=n.intValueExact();if(x<data.length)data=Arrays.copyOf(data,x);c.succeeded();return()->{};
        }
    }
}
