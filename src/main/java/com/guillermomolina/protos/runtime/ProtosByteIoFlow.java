/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/**
 * Ordered standard byte-I/O flow. I014-C extends the I014-B transfer flow with
 * flush and the optional positioned/size/truncate capability set while keeping
 * one sequence-state ordering domain.
 */
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
    public interface ReceiverCompletion {
        void succeeded();
        void failed();
    }
    public interface IntegerCompletion {
        void succeeded(BigInteger value);
        void failed();
    }
    public interface Backend {
        Cancellation read(int maxBytes, ReadCompletion completion);
        Cancellation write(byte[] bytes, WriteCompletion completion);
    }
    /** Backend capability required by installExtended(). */
    public interface ExtendedBackend extends Backend {
        Cancellation flush(ReceiverCompletion completion);
        Cancellation position(IntegerCompletion completion);
        Cancellation seek(BigInteger absolutePosition, IntegerCompletion completion);
        Cancellation seekBy(BigInteger offset, IntegerCompletion completion);
        Cancellation seekToEnd(IntegerCompletion completion);
        Cancellation size(IntegerCompletion completion);
        Cancellation truncate(BigInteger size, ReceiverCompletion completion);
    }

    private enum Kind { READ, WRITE, FLUSH, POSITION, SEEK, SEEK_BY, SEEK_END, SIZE, TRUNCATE }
    private static final int DEFAULT_MAX_RETAINED_WRITE_BYTES = 1024 * 1024;

    private final ProtosObjectValue receiver;
    private final ProtosObjectValue bytesPrototype;
    private final ProtosActorExecutionDomain domain;
    private final Backend backend;
    private final ProtosIoLifecycle lifecycle;
    private final int maxRetainedWriteBytes;
    private final ArrayDeque<Request> operations = new ArrayDeque<>();
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

    public ProtosFutureValue read(ProtosActivation activation,Object maxBytesValue){
        Objects.requireNonNull(activation);requireDomain(activation);
        BigInteger n=integer(maxBytesValue);
        if(n==null||n.signum()<=0||n.compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>0)
            return failedFuture(activation,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        return enqueue(new Request(Kind.READ,begin(activation),n,null,null));
    }

    public ProtosFutureValue write(ProtosActivation activation,Object value){
        Objects.requireNonNull(activation);requireDomain(activation);
        if(!(value instanceof ProtosBytesValue bytes))
            return failedFuture(activation,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        byte[] snapshot=snapshot(bytes);
        synchronized(this){
            if(snapshot.length>maxRetainedWriteBytes-retainedWriteBytes)
                return failedFuture(activation,ProtosCoreErrors.StandardError.I_O_CAPACITY_EXHAUSTED);
            retainedWriteBytes+=snapshot.length;
        }
        ProtosIoOperation op=begin(activation);
        if(!op.future().isPending()){releaseWriteBytes(snapshot.length);return op.future();}
        return enqueue(new Request(Kind.WRITE,op,null,snapshot,null));
    }

    public ProtosFutureValue flush(ProtosActivation a){return enqueueSimple(a,Kind.FLUSH,null);}
    public ProtosFutureValue position(ProtosActivation a){return enqueueSimple(a,Kind.POSITION,null);}
    public ProtosFutureValue seek(ProtosActivation a,Object v){
        BigInteger n=validatedNonNegative(a,v);if(n==null)return invalidFuture(a);
        return enqueueSimple(a,Kind.SEEK,n);
    }
    public ProtosFutureValue seekBy(ProtosActivation a,Object v){
        Objects.requireNonNull(a);requireDomain(a);BigInteger n=integer(v);
        if(n==null)return invalidFuture(a);return enqueueSimple(a,Kind.SEEK_BY,n);
    }
    public ProtosFutureValue seekToEnd(ProtosActivation a){return enqueueSimple(a,Kind.SEEK_END,null);}
    public ProtosFutureValue size(ProtosActivation a){return enqueueSimple(a,Kind.SIZE,null);}
    public ProtosFutureValue truncate(ProtosActivation a,Object v){
        BigInteger n=validatedNonNegative(a,v);if(n==null)return invalidFuture(a);
        return enqueueSimple(a,Kind.TRUNCATE,n);
    }

    private ProtosFutureValue enqueueSimple(ProtosActivation a,Kind k,BigInteger n){
        Objects.requireNonNull(a);requireDomain(a);
        if(!(backend instanceof ExtendedBackend))
            return failedFuture(a,ProtosCoreErrors.StandardError.I_O_ERROR);
        return enqueue(new Request(k,begin(a),n,null,null));
    }
    private BigInteger validatedNonNegative(ProtosActivation a,Object v){
        Objects.requireNonNull(a);requireDomain(a);BigInteger n=integer(v);
        return n!=null&&n.signum()>=0?n:null;
    }
    private ProtosFutureValue invalidFuture(ProtosActivation a){
        return failedFuture(a,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
    }
    private ProtosIoOperation begin(ProtosActivation a){return lifecycle.beginOperation(a);}

    private ProtosFutureValue enqueue(Request r){
        if(!r.op.future().isPending())return r.op.future();
        synchronized(this){operations.addLast(r);}
        r.op.onCancellation(()->cancel(r));
        pump();
        return r.op.future();
    }

    private void pump(){
        Request r;
        synchronized(this){r=operations.peekFirst();if(r==null||r.started)return;r.started=true;}
        switch(r.kind){
            case READ -> startRead(r);
            case WRITE -> startWrite(r);
            case FLUSH -> startReceiver(r,c->extended().flush(c));
            case POSITION -> startInteger(r,c->extended().position(c),false);
            case SEEK -> startInteger(r,c->extended().seek(r.number,c),true);
            case SEEK_BY -> startInteger(r,c->extended().seekBy(r.number,c),true);
            case SEEK_END -> startInteger(r,c->extended().seekToEnd(c),true);
            case SIZE -> startInteger(r,c->extended().size(c),false);
            case TRUNCATE -> startReceiver(r,c->extended().truncate(r.number,c));
        }
    }

    private void startRead(Request r){
        byte[] buffered=null;
        synchronized(this){
            if(!unread.isEmpty()){
                int n=Math.min(r.number.intValueExact(),unread.size());
                buffered=new byte[n];
                for(int i=0;i<n;i++)buffered[i]=unread.removeFirst();
            }
        }
        if(buffered!=null){completeReadData(r,buffered);return;}
        try{
            setCancellation(r,backend.read(r.number.intValueExact(),new ReadCompletion(){
                public void data(byte[] b){completeReadData(r,b);}
                public void eof(){if(r.op.commit())r.op.resolve(ProtosNullValue.INSTANCE);finish(r);}
                public void failed(){failIo(r);finish(r);}
            }));
        }catch(RuntimeException ex){failIo(r);finish(r);}
    }

    private void completeReadData(Request r,byte[] bytes){
        Objects.requireNonNull(bytes);
        if(bytes.length==0||bytes.length>r.number.intValueExact()){failIo(r);finish(r);return;}
        if(!r.op.commit()){
            synchronized(this){for(int i=bytes.length-1;i>=0;i--)unread.addFirst(bytes[i]);}
            finish(r);return;
        }
        ProtosBytesValue result=new ProtosBytesValue(bytesPrototype);
        for(byte b:bytes)result.indexedAdd(new ProtosIntegerValue(BigInteger.valueOf(b&0xff)));
        r.op.resolve(result);finish(r);
    }

    private void startWrite(Request r){
        if(r.bytes.length==0){if(r.op.commit())r.op.resolve(receiver);finish(r);return;}
        try{
            setCancellation(r,backend.write(r.bytes.clone(),new WriteCompletion(){
                public void succeeded(){if(r.op.commit())r.op.resolve(receiver);finish(r);}
                public void failed(int k){
                    if(k<0||k>r.bytes.length)throw new IllegalArgumentException("invalid contributed prefix");
                    if(k>0)r.op.commit();
                    failIo(r);finish(r);
                }
            }));
        }catch(RuntimeException ex){failIo(r);finish(r);}
    }

    @FunctionalInterface private interface ReceiverStarter{Cancellation start(ReceiverCompletion c);}
    private void startReceiver(Request r,ReceiverStarter starter){
        try{
            setCancellation(r,starter.start(new ReceiverCompletion(){
                public void succeeded(){if(r.op.commit())r.op.resolve(receiver);finish(r);}
                public void failed(){failIo(r);finish(r);}
            }));
        }catch(RuntimeException ex){failIo(r);finish(r);}
    }

    @FunctionalInterface private interface IntegerStarter{Cancellation start(IntegerCompletion c);}
    private void startInteger(Request r,IntegerStarter starter,boolean changesPosition){
        try{
            setCancellation(r,starter.start(new IntegerCompletion(){
                public void succeeded(BigInteger n){
                    if(n==null||n.signum()<0){failIo(r);finish(r);return;}
                    if(r.op.commit())r.op.resolve(new ProtosIntegerValue(n));
                    finish(r);
                }
                public void failed(){failIo(r);finish(r);}
            }));
        }catch(RuntimeException ex){failIo(r);finish(r);}
    }

    private ExtendedBackend extended(){return (ExtendedBackend)backend;}
    private void setCancellation(Request r,Cancellation c){
        synchronized(this){r.cancellation=c;}
        if(r.op.terminal()&&c!=null)c.cancel();
    }
    private void cancel(Request r){
        Cancellation c;boolean removed=false;
        synchronized(this){
            c=r.cancellation;
            if(!r.started){removed=operations.remove(r);if(removed&&r.kind==Kind.WRITE)retainedWriteBytes-=r.bytes.length;}
        }
        if(c!=null)c.cancel();
        if(removed)pump();
    }
    private void finish(Request r){
        synchronized(this){
            if(operations.remove(r)&&r.kind==Kind.WRITE)retainedWriteBytes-=r.bytes.length;
        }
        pump();
    }
    private void failIo(Request r){
        r.op.fail(ProtosCoreErrors.newOccurrence(r.op.origin(),ProtosCoreErrors.StandardError.I_O_ERROR));
    }
    private void releaseWriteBytes(int n){synchronized(this){retainedWriteBytes-=n;}}
    private ProtosFutureValue failedFuture(ProtosActivation a,ProtosCoreErrors.StandardError kind){
        ProtosFutureValue f=new ProtosFutureValue(a.prelude().orElseThrow().futurePrototype(),domain);
        f.fail(ProtosCoreErrors.newOccurrence(a,kind));return f;
    }
    private void requireDomain(ProtosActivation a){
        if(a.executionDomain()!=domain)throw new IllegalArgumentException("I/O flow belongs to another Actor domain");
    }
    private static BigInteger integer(Object v){
        if(v instanceof ProtosIntegerValue i)return i.value();
        if(v instanceof ProtosFixedIntegerValue i)return i.value();
        return null;
    }
    private static byte[] snapshot(ProtosBytesValue b){
        List<Object>s=b.indexedSnapshot();byte[]out=new byte[s.size()];
        for(int i=0;i<s.size();i++){
            BigInteger n=integer(s.get(i));
            if(n==null||n.signum()<0||n.compareTo(BigInteger.valueOf(255))>0)
                throw new IllegalStateException("Bytes invariant violated");
            out[i]=(byte)n.intValue();
        }
        return out;
    }
    private static final class Request{
        final Kind kind;final ProtosIoOperation op;final BigInteger number;final byte[]bytes;
        boolean started;Cancellation cancellation;
        Request(Kind kind,ProtosIoOperation op,BigInteger number,byte[]bytes,Cancellation cancellation){
            this.kind=kind;this.op=op;this.number=number;this.bytes=bytes;this.cancellation=cancellation;
        }
    }
}
