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
        if(ProtosNumericValueSupport.sameCurrentFamilyIdentity(left,right))return true;
        if(left instanceof ProtosStringValue a && right instanceof ProtosStringValue b)return a.value().equals(b.value());
        if(left instanceof ProtosActorRefValue a && right instanceof ProtosActorRefValue b)return a.denotesSameIncarnation(b);
        if(left instanceof ProtosGroupRefValue a && right instanceof ProtosGroupRefValue b)return a.denotesSameReference(b);
        if(left instanceof ProtosRawForeignValue a && right instanceof ProtosRawForeignValue b)return a.sameForeignIdentity(b);
        return false;
    }
    /** Identity hash; every family tag and host hash is confined to the signed-long range. */
    public static long identityHash(Object value){
        java.util.Objects.requireNonNull(value,"value");
        if(ProtosNumericValueSupport.isCurrentNumber(value))return ProtosNumericValueSupport.currentNumericIdentityHash(value);
        if(value instanceof ProtosStringValue st)return tagged(31,st.value().hashCode());
        if(value instanceof ProtosActorRefValue ref)return tagged(34,Long.hashCode(ref.incarnationIdentityForRuntime()));
        if(value instanceof ProtosGroupRefValue ref)return tagged(35,ref.semanticIdentityForRuntime().hashCode());
        if(value instanceof ProtosRawForeignValue raw)return raw.taggedIdentityHash();
        if(value==ProtosBooleanValue.TRUE)return tagged(32,1); if(value==ProtosBooleanValue.FALSE)return tagged(32,0); if(value==ProtosNullValue.INSTANCE)return tagged(33,0);
        return Integer.toUnsignedLong(System.identityHashCode(value));
    }
    private static long tagged(int family,int hash){return (((long)family)<<32)^Integer.toUnsignedLong(hash);}
}
