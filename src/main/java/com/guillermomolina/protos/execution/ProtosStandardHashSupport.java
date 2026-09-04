/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;
import com.guillermomolina.protos.runtime.*; import java.math.BigInteger;
public final class ProtosStandardHashSupport {
 private ProtosStandardHashSupport(){}
 public static void installObjectHash(){var o=ProtosObjectValue.rootObject();if(!o.hasLocalSlot("hash"))o.createLocalSlot("hash",ProtosClosureValue.nativeClosure((a,x)->{if(!x.isEmpty())throw new ProtosSignalException(ProtosCoreErrors.newError(a));return new ProtosIntegerValue(BigInteger.valueOf(Integer.toUnsignedLong(System.identityHashCode(a.receiver()))));}));}
 public static void installNumberHash(ProtosObjectValue n){if(!n.hasLocalSlot("hash"))n.createLocalSlot("hash",ProtosClosureValue.nativeClosure((a,x)->{if(!x.isEmpty())throw new ProtosSignalException(ProtosCoreErrors.newError(a));Object v=a.receiver();BigInteger h;if(v instanceof ProtosIntegerValue i)h=i.value();else if(v instanceof ProtosFixedIntegerValue i)h=i.value();else if(v instanceof ProtosFloatValue f){double d=f.value();if(Double.isNaN(d))h=BigInteger.valueOf(2146959360L);else{BigInteger z=ProtosStandardNumericConversionProtocol.exactIntegralBinary64(d);h=z!=null?z:BigInteger.valueOf(Double.hashCode(d==0.0?0.0:d));}}else throw new ProtosSignalException(ProtosCoreErrors.newError(a));return new ProtosIntegerValue(h);}));}
 public static void installStringHash(ProtosObjectValue s){if(!s.hasLocalSlot("hash"))s.createLocalSlot("hash",ProtosClosureValue.nativeClosure((a,x)->{if(!x.isEmpty()||!(a.receiver() instanceof ProtosStringValue v))throw new ProtosSignalException(ProtosCoreErrors.newError(a));return new ProtosIntegerValue(BigInteger.valueOf(v.value().hashCode()));}));}
}
