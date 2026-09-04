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

package com.guillermomolina.protos.runtime;

public final class ProtosIdentity {
    private ProtosIdentity() {}
    public static boolean identical(Object left,Object right) {
        if(left==right)return true;
        if(left instanceof ProtosIntegerValue a && right instanceof ProtosIntegerValue b)return a.value().equals(b.value());
        if(left instanceof ProtosFixedIntegerValue a && right instanceof ProtosFixedIntegerValue b)return a.family()==b.family()&&a.value().equals(b.value());
        if(left instanceof ProtosFloatValue a && right instanceof ProtosFloatValue b){double av=a.value(),bv=b.value();if(Double.isNaN(av)&&Double.isNaN(bv))return true;return Double.doubleToRawLongBits(av)==Double.doubleToRawLongBits(bv);}
        if(left instanceof ProtosStringValue a && right instanceof ProtosStringValue b)return a.value().equals(b.value());
        return false;
    }
    public static java.math.BigInteger identityHash(Object value){
        java.util.Objects.requireNonNull(value,"value");
        if(value instanceof ProtosIntegerValue i)return tagged(1,i.value().hashCode());
        if(value instanceof ProtosFixedIntegerValue i)return tagged(10+i.family().ordinal(),i.value().hashCode());
        if(value instanceof ProtosFloatValue f){double d=f.value();long bits=Double.isNaN(d)?0x7ff8000000000000L:Double.doubleToRawLongBits(d);return tagged(30,Long.hashCode(bits));}
        if(value instanceof ProtosStringValue st)return tagged(31,st.value().hashCode());
        if(value==ProtosBooleanValue.TRUE)return tagged(32,1); if(value==ProtosBooleanValue.FALSE)return tagged(32,0); if(value==ProtosNullValue.INSTANCE)return tagged(33,0);
        return java.math.BigInteger.valueOf(Integer.toUnsignedLong(System.identityHashCode(value)));
    }
    private static java.math.BigInteger tagged(int family,int hash){long x=(((long)family)<<32)^Integer.toUnsignedLong(hash);return java.math.BigInteger.valueOf(x);}
}
