/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import com.guillermomolina.protos.execution.ProtosInvocation;
import java.math.BigInteger;
import java.util.*;

/** Ordered bounded state machine for the standard Core byte buffering wrappers. */
public final class ProtosBufferedByteIo {
    private static final int READ_AHEAD=8192, MAX_OUTPUT=1024*1024;
    private enum Mode { READER, WRITER }
    private final Mode mode; private final ProtosObjectValue receiver,target,bytesPrototype; private final ProtosActorExecutionDomain domain;
    private final boolean owning; private final ArrayDeque<Byte> input=new ArrayDeque<>(); private final ArrayDeque<Req> q=new ArrayDeque<>();
    private final ArrayList<ProtosFutureValue> closeFollowers=new ArrayList<>(); private byte[] output=new byte[0];
    private boolean active,closing,closed; private ProtosObjectValue closeError,outputError;

    private ProtosBufferedByteIo(Mode m,ProtosObjectValue r,ProtosObjectValue t,ProtosObjectValue bp,ProtosActivation a,boolean own){mode=m;receiver=r;target=t;bytesPrototype=bp;domain=a.executionDomain();owning=own;}
    public static ProtosBufferedByteIo reader(ProtosObjectValue r,ProtosObjectValue t,ProtosObjectValue bp,ProtosActivation a,boolean own){return new ProtosBufferedByteIo(Mode.READER,r,t,bp,a,own);}
    public static ProtosBufferedByteIo writer(ProtosObjectValue r,ProtosObjectValue t,ProtosObjectValue bp,ProtosActivation a,boolean own){return new ProtosBufferedByteIo(Mode.WRITER,r,t,bp,a,own);}

    public ProtosFutureValue read(ProtosActivation a,Object n0){check(a); BigInteger n=integer(n0); if(n==null||n.signum()<=0||n.compareTo(BigInteger.valueOf(Integer.MAX_VALUE))>0)return failed(a,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        synchronized(this){if(closing||closed)return lifecycle(a);}
        return enqueue(new Req(a,newFuture(a),Kind.READ,n.intValue(),null)); }
    public ProtosFutureValue write(ProtosActivation a,Object v){check(a); if(!(v instanceof ProtosBytesValue b))return failed(a,ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT);
        byte[] x=snapshot(b); synchronized(this){if(closing||closed)return lifecycle(a);if(outputError!=null)return failedSame(a,outputError);if(x.length>MAX_OUTPUT-output.length)return failed(a,ProtosCoreErrors.StandardError.I_O_CAPACITY_EXHAUSTED);}
        return enqueue(new Req(a,newFuture(a),Kind.WRITE,0,x)); }
    public ProtosFutureValue flush(ProtosActivation a){check(a); synchronized(this){if(closing||closed)return lifecycle(a);if(outputError!=null)return failedSame(a,outputError);} return enqueue(new Req(a,newFuture(a),Kind.FLUSH,0,null));}
    public ProtosFutureValue close(ProtosActivation a){check(a); ProtosFutureValue f=newFuture(a);f.attachCancellationProducer(()->{}); boolean start=false;
        synchronized(this){if(closed){if(closeError==null)f.resolve(receiver,a);else f.fail(closeError);return f;}closeFollowers.add(f);if(closing)return f;closing=true;start=!active&&q.isEmpty();}
        if(start)finalizeClose(a); return f; }

    private enum Kind { READ,WRITE,FLUSH }
    private static final class Req { final ProtosActivation a;final ProtosFutureValue f;final Kind k;final int n;final byte[]x;boolean committed;
        Req(ProtosActivation a,ProtosFutureValue f,Kind k,int n,byte[]x){this.a=a;this.f=f;this.k=k;this.n=n;this.x=x;} }
    private ProtosFutureValue enqueue(Req r){r.f.attachCancellationProducer(()->cancel(r)); synchronized(this){q.addLast(r);}pump();return r.f;}
    private void cancel(Req r){synchronized(this){if(r.committed||!q.remove(r))return;}r.f.cancelTerminal();pump();}
    private void pump(){Req r; synchronized(this){if(active)return;if(q.isEmpty()){if(closing)finalizeClose(null);return;}active=true;r=q.peekFirst();}
        switch(r.k){case READ->doRead(r);case WRITE->doWrite(r);case FLUSH->doFlush(r,false);} }
    private void done(Req r){boolean closeNow; synchronized(this){q.remove(r);active=false;closeNow=closing&&q.isEmpty();}if(closeNow)finalizeClose(r.a);else pump();}

    private void doRead(Req r){byte[] x; synchronized(this){x=takeInput(r.n);} if(x.length>0){r.committed=true;r.f.resolve(bytes(x),r.a);done(r);return;}
        ProtosFutureValue lower=invokeFuture(target,"read",List.of(new ProtosIntegerValue(BigInteger.valueOf(Math.max(r.n,READ_AHEAD)))),r.a,r.f); if(lower==null){done(r);return;}
        lower.observe(z->{switch(z.state()){case RESOLVED->{Object v=z.resolvedValue().orElseThrow();if(v==ProtosNullValue.INSTANCE){r.committed=true;r.f.resolve(v,r.a);done(r);}else if(v instanceof ProtosBytesValue b){byte[] got=snapshot(b);if(got.length==0){r.f.fail(ioError(r.a));done(r);return;}byte[] first; synchronized(this){for(byte c:got)input.addLast(c);first=takeInput(r.n);}r.committed=true;r.f.resolve(bytes(first),r.a);done(r);}else{r.f.fail(ioError(r.a));done(r);}}case FAILED-> {r.f.fail(z.failedError().orElseThrow());done(r);}case CANCELLED->{r.f.cancelTerminal();done(r);}case PENDING->{}}});
    }
    private void doWrite(Req r){synchronized(this){byte[] n=Arrays.copyOf(output,output.length+r.x.length);System.arraycopy(r.x,0,n,output.length,r.x.length);output=n;r.committed=true;}r.f.resolve(receiver,r.a);done(r);}
    private void doFlush(Req r,boolean closePath){byte[] x; synchronized(this){x=output.clone();}
        if(x.length==0){flushTarget(r,closePath);return;}
        ProtosFutureValue lower=invokeFuture(target,"write",List.of(bytes(x)),r.a,r.f);if(lower==null){poison(r,closePath);return;}
        lower.observe(z->{if(z.state()==ProtosFutureValue.State.RESOLVED){synchronized(this){if(output.length>=x.length)output=Arrays.copyOfRange(output,x.length,output.length);}r.committed=true;flushTarget(r,closePath);}else if(z.state()!=ProtosFutureValue.State.PENDING)poisonFrom(r,z,closePath);});
    }
    private void flushTarget(Req r,boolean closePath){if(target.lookupSlot("flush").isEmpty()){r.committed=true;if(closePath)finishFinalization(r.a,null);else{r.f.resolve(receiver,r.a);done(r);}return;}
        ProtosFutureValue lower=invokeFuture(target,"flush",List.of(),r.a,r.f);if(lower==null){poison(r,closePath);return;}lower.observe(z->{if(z.state()==ProtosFutureValue.State.RESOLVED){r.committed=true;if(closePath)finishFinalization(r.a,null);else{r.f.resolve(receiver,r.a);done(r);}}else if(z.state()!=ProtosFutureValue.State.PENDING)poisonFrom(r,z,closePath);});}
    private void poisonFrom(Req r,ProtosFutureValue z,boolean closePath){ProtosObjectValue e=z.state()==ProtosFutureValue.State.FAILED?z.failedError().orElseGet(()->ioError(r.a)):ioError(r.a);synchronized(this){if(outputError==null)outputError=e;}r.f.fail(e);if(closePath)finishFinalization(r.a,e);else done(r);}
    private void poison(Req r,boolean closePath){ProtosObjectValue e=ioError(r.a);synchronized(this){if(outputError==null)outputError=e;}r.f.fail(e);if(closePath)finishFinalization(r.a,e);else done(r);}

    private void finalizeClose(ProtosActivation maybe){ProtosActivation a=maybe; synchronized(this){if(closed)return;if(a==null){if(closeFollowers.isEmpty())return; /* followers always originate in this domain */}}
        if(a==null){finishCloseWithoutActivation();return;} if(mode==Mode.WRITER&&outputError==null&&output.length>0){Req r=new Req(a,newFuture(a),Kind.FLUSH,0,null);doFlush(r,true);}else finishFinalization(a,outputError);}
    private void finishCloseWithoutActivation(){/* only reachable after synchronous queue completion; next close caller will drive finalization */}
    private void finishFinalization(ProtosActivation a,ProtosObjectValue primary){if(owning){ProtosFutureValue cf=invokeFuture(target,"close",List.of(),a,null);if(cf==null){finishClose(a,primary!=null?primary:ioError(a));return;}if(primary!=null){finishClose(a,primary);return;}cf.observe(z->{if(z.state()==ProtosFutureValue.State.RESOLVED)finishClose(a,null);else if(z.state()!=ProtosFutureValue.State.PENDING)finishClose(a,z.state()==ProtosFutureValue.State.FAILED?z.failedError().orElseGet(()->ioError(a)):ioError(a));});}else finishClose(a,primary);}
    private void finishClose(ProtosActivation a,ProtosObjectValue e){List<ProtosFutureValue> fs;synchronized(this){if(closed)return;closed=true;closeError=e;fs=List.copyOf(closeFollowers);closeFollowers.clear();}for(var f:fs)if(e==null)f.resolve(receiver,a);else f.fail(e);}

    private ProtosFutureValue invokeFuture(ProtosObjectValue obj,String msg,List<?> args,ProtosActivation a,ProtosFutureValue outer){try{Object v=ProtosInvocation.invokeMessage(obj,msg,args,a);if(v instanceof ProtosFutureValue f)return f;if(outer!=null)outer.fail(ioError(a));return null;}catch(RuntimeException ex){if(outer!=null)outer.fail(ioError(a));return null;}}
    private ProtosFutureValue newFuture(ProtosActivation a){return new ProtosFutureValue(a.prelude().orElseThrow().futurePrototype(),domain);}
    private ProtosFutureValue failed(ProtosActivation a,ProtosCoreErrors.StandardError e){ProtosFutureValue f=newFuture(a);f.fail(ProtosCoreErrors.newOccurrence(a,e));return f;}
    private ProtosFutureValue failedSame(ProtosActivation a,ProtosObjectValue e){ProtosFutureValue f=newFuture(a);f.fail(e);return f;}
    private ProtosFutureValue lifecycle(ProtosActivation a){return failed(a,ProtosCoreErrors.StandardError.I_O_LIFECYCLE_ERROR);}
    private ProtosObjectValue ioError(ProtosActivation a){return ProtosCoreErrors.newOccurrence(a,ProtosCoreErrors.StandardError.I_O_ERROR);}
    private void check(ProtosActivation a){Objects.requireNonNull(a);if(a.executionDomain()!=domain)throw new IllegalArgumentException("buffered I/O belongs to another Actor domain");}
    private byte[] takeInput(int n){int k=Math.min(n,input.size());byte[]x=new byte[k];for(int i=0;i<k;i++)x[i]=input.removeFirst();return x;}
    private ProtosBytesValue bytes(byte[]x){ProtosBytesValue b=new ProtosBytesValue(bytesPrototype);for(byte c:x)b.indexedAdd(new ProtosIntegerValue(BigInteger.valueOf(c&255)));return b;}
    private static BigInteger integer(Object v){return v instanceof ProtosIntegerValue i?i.value():null;}
    private static byte[] snapshot(ProtosBytesValue b){int size=b.indexedSize().intValueExact();byte[]x=new byte[size];for(int j=0;j<x.length;j++){Object v=b.indexedAt(BigInteger.valueOf(j));if(!(v instanceof ProtosIntegerValue i))throw new IllegalStateException("invalid Bytes");int n=i.value().intValueExact();if(n<0||n>255)throw new IllegalStateException("invalid Bytes octet");x[j]=(byte)n;}return x;}
}
