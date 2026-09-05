/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;
import com.guillermomolina.protos.runtime.*; import java.math.BigInteger; import java.util.*;
public final class ProtosStandardPathProtocol {
 private ProtosStandardPathProtocol(){}
 public static void install(ProtosObjectValue p){
  for(String s:List.of("relative","rooted","child","parentComponent","==","hash"))if(p.hasLocalSlot(s))throw new IllegalStateException("Core Path already defines "+s);
  p.createLocalSlot("relative",ProtosClosureValue.nativeClosure((a,x)->{factory(a,x,p);return new ProtosPathValue(p,false,List.of());}));
  p.createLocalSlot("rooted",ProtosClosureValue.nativeClosure((a,x)->{factory(a,x,p);return new ProtosPathValue(p,true,List.of());}));
  p.createLocalSlot("child",ProtosClosureValue.nativeClosure((a,x)->{var q=path(a);arity(a,x,1);if(!(x.get(0) instanceof ProtosStringValue s))throw err(a);String n=s.value();if(n.isEmpty()||n.equals(".")||n.equals(".."))throw err(a);return q.child(n);}));
  p.createLocalSlot("parentComponent",ProtosClosureValue.nativeClosure((a,x)->{var q=path(a);arity(a,x,0);return q.parentComponent();}));
  p.createLocalSlot("==",ProtosClosureValue.nativeClosure((a,x)->{var q=path(a);arity(a,x,1);return x.get(0) instanceof ProtosPathValue z&&q.structurallyEquals(z)?ProtosBooleanValue.TRUE:ProtosBooleanValue.FALSE;}));
  p.createLocalSlot("hash",ProtosClosureValue.nativeClosure((a,x)->{var q=path(a);arity(a,x,0);return new ProtosIntegerValue(BigInteger.valueOf(q.structuralHash()));}));
 }
 private static void factory(ProtosActivation a,List<?>x,ProtosObjectValue p){arity(a,x,0);if(!(a.receiver() instanceof ProtosObjectValue q)||!delegates(q,p))throw err(a);}
 private static ProtosPathValue path(ProtosActivation a){if(!(a.receiver() instanceof ProtosPathValue p))throw err(a);return p;}
 private static void arity(ProtosActivation a,List<?>x,int n){if(x.size()!=n)throw err(a);}
 private static boolean delegates(ProtosObjectValue r,ProtosObjectValue p){Object c=r;while(c instanceof ProtosObjectValue o){if(o==p)return true;c=o.parent().orElse(null);}return false;}
 private static ProtosSignalException err(ProtosActivation a){return new ProtosSignalException(ProtosCoreErrors.newError(a));}
}
