/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordered ByteReadable/ByteWritable flow built on the I014-A operation/lifecycle substrate. */
public final class ProtosByteIoFlow {
    public interface Cancellation { void cancel(); }
    public interface ReadCompletion {
        void data(byte[] bytes);
        void eof();
        void failed();
    }
    public interface WriteCompletion {
        void succeeded();
        /** Number of captured bytes already irreversibly contributed before failure. */
        void failed(int contributedPrefix);
    }
    public interface Backend {
        Cancellation read(int maxBytes, ReadCompletion completion);
        Cancellation write(byte[] bytes, WriteCompletion completion);
    }

    private static final int DEFAULT_MAX_RETAINED_WRITE_BYTES = 1024 * 1024;
    private final ProtosObjectValue receiver;
    private final ProtosObjectValue bytesPrototype;
    private final ProtosActorExecutionDomain domain;
    private final Backend backend;
    private final ProtosIoLifecycle lifecycle;
    private final int maxRetainedWriteBytes;
    private final ArrayDeque<ReadRequest> reads = new ArrayDeque<>();
    private final ArrayDeque<WriteRequest> writes = new ArrayDeque<>();
    private final ArrayDeque<Byte> unread = new ArrayDeque<>();
    private int retainedWriteBytes;

    public ProtosByteIoFlow(ProtosObjectValue receiver, ProtosObjectValue bytesPrototype,
            ProtosActivation activation, Backend backend) {
        this(receiver, bytesPrototype, activation, backend, DEFAULT_MAX_RETAINED_WRITE_BYTES);
    }

    public ProtosByteIoFlow(ProtosObjectValue receiver, ProtosObjectValue bytesPrototype,
            ProtosActivation activation, Backend backend, int maxRetainedWriteBytes) {
        this.receiver=Objects.requireNonNull(receiver,"receiver");
        this.bytesPrototype=Objects.requireNonNull(bytesPrototype,"bytesPrototype");
        Objects.requireNonNull(activation,"activation");
        this.domain=activation.executionDomain();
        this.backend=Objects.requireNonNull(backend,"backend");
        if(maxRetainedWriteBytes<0)throw new IllegalArgumentException("negative retained-write bound");
        this.maxRetainedWriteBytes=maxRetainedWriteBytes;
        this.lifecycle=new ProtosIoLifecycle(receiver,activation.prelude().orElseThrow().futurePrototype(),domain,c->c.succeeded());
    }

    public ProtosFutureValue read(ProtosActivation activation, Object maxBytesValue) {
        Objects.requireNonNull(activation,"activation"); requireDomain(activation);
        BigInteger n=integer(maxBytesValue);
        if(n==null||n.signum()<=0||n.compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>0)
            return failedFuture(activation,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        ProtosIoOperation op=lifecycle.beginOperation(activation);
        if(!op.future().isPending())return op.future();
        ReadRequest request=new ReadRequest(op,n.intValueExact());
        synchronized(this){reads.addLast(request);}
        op.onCancellation(()->cancelRead(request));
        pumpRead();
        return op.future();
    }

    public ProtosFutureValue write(ProtosActivation activation, Object value) {
        Objects.requireNonNull(activation,"activation"); requireDomain(activation);
        if(!(value instanceof ProtosBytesValue bytes))
            return failedFuture(activation,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        byte[] snapshot=snapshot(bytes,activation);
        synchronized(this){
            if(snapshot.length>maxRetainedWriteBytes-retainedWriteBytes)
                return failedFuture(activation,ProtosCoreErrors.StandardError.I_O_CAPACITY_EXHAUSTED);
            retainedWriteBytes+=snapshot.length;
        }
        ProtosIoOperation op=lifecycle.beginOperation(activation);
        if(!op.future().isPending()){releaseWriteBytes(snapshot.length);return op.future();}
        WriteRequest request=new WriteRequest(op,snapshot);
        synchronized(this){writes.addLast(request);}
        op.onCancellation(()->cancelWrite(request));
        pumpWrite();
        return op.future();
    }

    private void pumpRead(){
        ReadRequest r; byte[] buffered=null;
        synchronized(this){
            r=reads.peekFirst(); if(r==null||r.started)return;
            if(!unread.isEmpty()){
                int n=Math.min(r.maxBytes,unread.size()); buffered=new byte[n];
                for(int i=0;i<n;i++)buffered[i]=unread.removeFirst();
                r.started=true;
            } else r.started=true;
        }
        if(buffered!=null){completeReadData(r,buffered);return;}
        try{
            Cancellation c=backend.read(r.maxBytes,new ReadCompletion(){
                public void data(byte[] b){completeReadData(r,b);}
                public void eof(){completeReadEof(r);}
                public void failed(){completeReadFailure(r);}
            });
            synchronized(this){r.cancellation=c;}
            if(r.op.terminal()&&c!=null)c.cancel();
        }catch(RuntimeException ex){completeReadFailure(r);}
    }

    private void completeReadData(ReadRequest r,byte[] bytes){
        Objects.requireNonNull(bytes,"bytes");
        if(bytes.length==0||bytes.length>r.maxBytes){completeReadFailure(r);return;}
        boolean committed=r.op.commit();
        if(!committed){
            synchronized(this){for(int i=bytes.length-1;i>=0;i--)unread.addFirst(bytes[i]);}
            finishRead(r); return;
        }
        ProtosBytesValue result=new ProtosBytesValue(bytesPrototype);
        for(byte b:bytes)result.indexedAdd(new ProtosIntegerValue(BigInteger.valueOf(b&0xff)));
        r.op.resolve(result); finishRead(r);
    }
    private void completeReadEof(ReadRequest r){
        if(r.op.commit())r.op.resolve(ProtosNullValue.INSTANCE);
        finishRead(r);
    }
    private void completeReadFailure(ReadRequest r){
        r.op.fail(ProtosCoreErrors.newOccurrence(r.activation(),ProtosCoreErrors.StandardError.I_O_ERROR));
        finishRead(r);
    }
    private void finishRead(ReadRequest r){synchronized(this){reads.remove(r);}pumpRead();}
    private void cancelRead(ReadRequest r){Cancellation c; synchronized(this){c=r.cancellation;if(!r.started)reads.remove(r);}if(c!=null)c.cancel();pumpRead();}

    private void pumpWrite(){
        WriteRequest w; synchronized(this){w=writes.peekFirst();if(w==null||w.started)return;w.started=true;}
        if(w.bytes.length==0){if(w.op.commit())w.op.resolve(receiver);finishWrite(w);return;}
        try{
            Cancellation c=backend.write(w.bytes.clone(),new WriteCompletion(){
                public void succeeded(){completeWriteSuccess(w);}
                public void failed(int k){completeWriteFailure(w,k);}
            });
            synchronized(this){w.cancellation=c;}
            if(w.op.terminal()&&c!=null)c.cancel();
        }catch(RuntimeException ex){completeWriteFailure(w,0);}
    }
    private void completeWriteSuccess(WriteRequest w){if(w.op.commit())w.op.resolve(receiver);finishWrite(w);}
    private void completeWriteFailure(WriteRequest w,int k){
        if(k<0||k>w.bytes.length)throw new IllegalArgumentException("invalid contributed prefix");
        if(k>0)w.op.commit();
        w.op.fail(ProtosCoreErrors.newOccurrence(w.activation(),ProtosCoreErrors.StandardError.I_O_ERROR));
        finishWrite(w);
    }
    private void finishWrite(WriteRequest w){synchronized(this){if(writes.remove(w))retainedWriteBytes-=w.bytes.length;}pumpWrite();}
    private void cancelWrite(WriteRequest w){Cancellation c; synchronized(this){c=w.cancellation;if(!w.started&&writes.remove(w))retainedWriteBytes-=w.bytes.length;}if(c!=null)c.cancel();pumpWrite();}
    private void releaseWriteBytes(int n){synchronized(this){retainedWriteBytes-=n;}}

    private ProtosFutureValue failedFuture(ProtosActivation a,ProtosCoreErrors.StandardError kind){
        ProtosFutureValue f=new ProtosFutureValue(a.prelude().orElseThrow().futurePrototype(),domain);
        f.fail(ProtosCoreErrors.newOccurrence(a,kind)); return f;
    }
    private void requireDomain(ProtosActivation a){if(a.executionDomain()!=domain)throw new IllegalArgumentException("I/O flow belongs to another Actor domain");}
    private static BigInteger integer(Object v){if(v instanceof ProtosIntegerValue i)return i.value();if(v instanceof ProtosFixedIntegerValue i)return i.value();return null;}
    private static byte[] snapshot(ProtosBytesValue b,ProtosActivation a){
        List<Object> s=b.indexedSnapshot();byte[] out=new byte[s.size()];
        for(int i=0;i<s.size();i++){BigInteger n=integer(s.get(i));if(n==null||n.signum()<0||n.compareTo(BigInteger.valueOf(255))>0)throw new IllegalStateException("Bytes invariant violated");out[i]=(byte)n.intValue();}
        return out;
    }
    private static final class ReadRequest{
        final ProtosIoOperation op;final int maxBytes;boolean started;Cancellation cancellation;
        ReadRequest(ProtosIoOperation op,int maxBytes){this.op=op;this.maxBytes=maxBytes;}
        ProtosActivation activation(){return op.origin();}
    }
    private static final class WriteRequest{
        final ProtosIoOperation op;final byte[] bytes;boolean started;Cancellation cancellation;
        WriteRequest(ProtosIoOperation op,byte[] bytes){this.op=op;this.bytes=bytes;}
        ProtosActivation activation(){return op.origin();}
    }
}
