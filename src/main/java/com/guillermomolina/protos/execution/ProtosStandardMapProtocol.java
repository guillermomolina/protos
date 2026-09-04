/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;
import com.guillermomolina.protos.runtime.*; import java.math.BigInteger; import java.util.*;
public final class ProtosStandardMapProtocol {
 private ProtosStandardMapProtocol(){}
 public static void install(ProtosObjectValue p){
  for(String s:List.of("call","at","atPut","containsKey","remove","size","each"))if(p.hasLocalSlot(s))throw new IllegalStateException("Core Map already defines "+s);
  p.createLocalSlot("call",ProtosClosureValue.nativeClosure((a,x)->{arity(a,x,0);if(!(a.receiver() instanceof ProtosObjectValue r)||!delegatesTo(r,p))throw err(a);return new ProtosMapValue(r);}));
  p.createLocalSlot("at",ProtosClosureValue.nativeClosure((a,x)->{ProtosMapValue m=map(a);arity(a,x,1);var e=find(m,x.get(0),a);if(e==null)throw err(a);return e.value();}));
  p.createLocalSlot("containsKey",ProtosClosureValue.nativeClosure((a,x)->{ProtosMapValue m=map(a);arity(a,x,1);return find(m,x.get(0),a)==null?ProtosBooleanValue.FALSE:ProtosBooleanValue.TRUE;}));
  p.createLocalSlot("atPut",ProtosClosureValue.nativeClosure((a,x)->{ProtosMapValue m=map(a);arity(a,x,2);mutationEntry(m,a);Object k=x.get(0),v=x.get(1);var e=find(m,k,a);if(e!=null){if(m.isFrozen())throw err(a);m.replaceValue(e,v);return v;}if(!m.isOpen())throw err(a);BigInteger h=hash(m,k,a);if(!m.isOpen())throw err(a);m.append(k,h,v);return v;}));
  p.createLocalSlot("remove",ProtosClosureValue.nativeClosure((a,x)->{ProtosMapValue m=map(a);arity(a,x,1);mutationEntry(m,a);if(!m.isOpen())throw err(a);var e=find(m,x.get(0),a);if(e==null||!m.isOpen())throw err(a);return m.remove(e);}));
  p.createLocalSlot("size",ProtosClosureValue.nativeClosure((a,x)->{ProtosMapValue m=map(a);arity(a,x,0);return new ProtosIntegerValue(BigInteger.valueOf(m.keyedSize()));}));
  p.createLocalSlot("each",ProtosClosureValue.nativeClosure((a,x)->{ProtosMapValue m=map(a);arity(a,x,1);Object b=x.get(0);invokable(b,a);for(var e:m.keyedSnapshot())ProtosInvocation.invoke(b,List.of(e.key(),e.value()),a);return m;}));
 }
 private static ProtosMapValue.Entry find(ProtosMapValue m,Object k,ProtosActivation a){BigInteger h=hash(m,k,a);for(var e:m.keyedSnapshot()){if(!e.recordedHash().equals(h))continue;m.enterComparison();Object q;try{q=ProtosInvocation.invokeMessage(k,"==",List.of(e.key()),a);}finally{m.leaveComparison();}if(q==ProtosBooleanValue.TRUE)return e;if(q!=ProtosBooleanValue.FALSE)throw err(a);}return null;}
 private static BigInteger hash(ProtosMapValue m,Object k,ProtosActivation a){m.enterComparison();Object h;try{h=ProtosInvocation.invokeMessage(k,"hash",List.of(),a);}finally{m.leaveComparison();}if(h instanceof ProtosIntegerValue i)return i.value();if(h instanceof ProtosFixedIntegerValue i)return i.value();throw err(a);}
 private static void mutationEntry(ProtosMapValue m,ProtosActivation a){if(m.comparisonActive()||m.isFrozen())throw err(a);}
 private static ProtosMapValue map(ProtosActivation a){if(!(a.receiver() instanceof ProtosMapValue m))throw err(a);return m;}
 private static void arity(ProtosActivation a,List<?> x,int n){if(x.size()!=n)throw err(a);}
 private static void invokable(Object c,ProtosActivation a){ProtosPrelude p=a.prelude().orElseThrow();ProtosSlotLookupResult s;try{s=ProtosValueLookup.lookup(c,"call",p).orElseThrow(()->err(a));}catch(UnsupportedOperationException e){throw err(a);}if(!(s.value() instanceof ProtosClosureValue))throw err(a);}
 private static boolean delegatesTo(ProtosObjectValue r,ProtosObjectValue p){Object c=r;while(c instanceof ProtosObjectValue o){if(o==p)return true;c=o.parent().orElse(null);}return false;}
 private static ProtosSignalException err(ProtosActivation a){return new ProtosSignalException(ProtosCoreErrors.newError(a));}
}
