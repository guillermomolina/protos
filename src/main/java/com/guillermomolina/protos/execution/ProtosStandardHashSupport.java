/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;
import com.guillermomolina.protos.runtime.*; import java.math.BigInteger;
public final class ProtosStandardHashSupport {
 private ProtosStandardHashSupport(){}
 public static void installObjectHash(){var o=ProtosObjectValue.rootObject();if(!o.hasLocalSlot("hash"))o.createLocalSlot("hash",ProtosClosureValue.nativeClosure((a,x)->{if(!x.isEmpty())throw new ProtosSignalException(ProtosCoreErrors.newError(a));return new ProtosIntegerValue(ProtosIdentity.identityHash(a.receiver()));}));}
 public static void installNumberHash(ProtosObjectValue n){if(!n.hasLocalSlot("hash"))n.createLocalSlot("hash",ProtosClosureValue.nativeClosure((a,x)->{if(!x.isEmpty())throw new ProtosSignalException(ProtosCoreErrors.newError(a));Object v=a.receiver();BigInteger h;if(v instanceof ProtosIntegerValue i)h=i.value();else if(v instanceof ProtosFixedIntegerValue i)h=i.value();else if(v instanceof ProtosFloatValue f){double d=f.value();if(Double.isNaN(d))h=BigInteger.valueOf(2146959360L);else{BigInteger z=ProtosStandardNumericConversionProtocol.exactIntegralBinary64(d);h=z!=null?z:BigInteger.valueOf(Double.hashCode(d==0.0?0.0:d));}}else throw new ProtosSignalException(ProtosCoreErrors.newError(a));return new ProtosIntegerValue(h);}));}
 public static void installStringHash(ProtosObjectValue s){if(!s.hasLocalSlot("hash"))s.createLocalSlot("hash",ProtosClosureValue.nativeClosure((a,x)->{if(!x.isEmpty()||!(a.receiver() instanceof ProtosStringValue v))throw new ProtosSignalException(ProtosCoreErrors.newError(a));return new ProtosIntegerValue(BigInteger.valueOf(v.value().hashCode()));}));}
}
