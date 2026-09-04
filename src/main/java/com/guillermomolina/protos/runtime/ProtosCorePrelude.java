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

public final class ProtosCorePrelude {
    private static final ProtosObjectValue CONTEXT =
            new ProtosObjectValue(ProtosObjectValue.rootObject());
    private static final ProtosObjectValue NUMBER =
            new ProtosObjectValue(ProtosObjectValue.rootObject());
    private static final ProtosObjectValue INTEGER =
            new ProtosObjectValue(NUMBER);
    private static final ProtosObjectValue FLOAT =
            new ProtosObjectValue(NUMBER);

    private ProtosCorePrelude() {}

    public static ProtosObjectValue contextPrototype() {
        return CONTEXT;
    }

    public static ProtosObjectValue numberPrototype() {
        return NUMBER;
    }

    public static ProtosObjectValue integerPrototype() {
        return INTEGER;
    }

    public static ProtosObjectValue floatPrototype() {
        return FLOAT;
    }

    public static ProtosObjectValue numericPrototypeFor(Object value) {
        if (value instanceof ProtosIntegerValue) {
            return INTEGER;
        }
        if (value instanceof ProtosFloatValue) {
            return FLOAT;
        }
        throw new IllegalArgumentException(
                "value is not a Core numeric value: " + value.getClass().getSimpleName());
    }

    public static ProtosObjectValue newExecutionContext() {
        return new ProtosObjectValue(CONTEXT);
    }
}
