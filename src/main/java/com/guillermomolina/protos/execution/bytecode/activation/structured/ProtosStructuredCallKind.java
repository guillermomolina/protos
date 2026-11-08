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

import com.guillermomolina.protos.execution.ProtosPreparedCalls.StructuredNativeInvocation;

/**
 * I113: the structured-dispatch protocols a lowered site can test a prepared
 * call for and prepare. The Bytecode builder fixes one constant per
 * {@code IsStructuredCall} or {@code PrepareStructuredCall} instruction. Each constant carries its own predicate body, so a call on a
 * constant receiver devirtualizes under partial evaluation without an
 * ordinal lookup.
 */
public enum ProtosStructuredCallKind {
    BOOLEAN {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredBoolean();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredBoolean();
        }
    },

    CASE_OF {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredCaseOf();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredCaseOf();
        }
    },

    OBJECT {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredObjectCall();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredObjectCall();
        }
    },

    IMPORT {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredImportCall();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredImportCall();
        }
    },

    ENSURE {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredEnsure();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredEnsure();
        }
    },

    LOCAL_EACH {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredArrayEach()
                    || prepared.isStructuredBytesEach()
                    || prepared.isStructuredEnvironmentEach()
                    || prepared.isStructuredIdentityMapEach()
                    || prepared.isStructuredMapEach();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            if (prepared.isStructuredArrayEach()) {
                return prepared.prepareStructuredArrayEach();
            }
            if (prepared.isStructuredBytesEach()) {
                return prepared.prepareStructuredBytesEach();
            }
            if (prepared.isStructuredEnvironmentEach()) {
                return prepared.prepareStructuredEnvironmentEach();
            }
            if (prepared.isStructuredIdentityMapEach()) {
                return prepared.prepareStructuredIdentityMapEach();
            }
            if (prepared.isStructuredMapEach()) {
                return prepared.prepareStructuredMapEach();
            }
            throw new IllegalStateException(
                    "Not a structured local each call");
        }
    },

    ARRAY_MATCH {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredArrayMatch();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredArrayMatch();
        }
    },

    FOREIGN_EACH {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredForeignEach();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredForeignEach();
        }
    },

    IDENTITY_MAP_AT_IF_ABSENT {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredIdentityMapAtIfAbsent();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredIdentityMapAtIfAbsent();
        }
    },

    MAP_MATCH {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredMapMatch();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredMapMatch();
        }
    },

    MAP_READ_LOOKUP {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredMapReadLookup();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredMapReadLookup();
        }
    },

    MAP_AT_PUT {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredMapAtPut();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredMapAtPut();
        }
    },

    MAP_REMOVE {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredMapRemove();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredMapRemove();
        }
    },

    ERROR_HANDLER {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredErrorHandler();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.prepareStructuredErrorHandler();
        }
    },

    /**
     * I113 (former PrepareStructuredIndexedEachCall): reached only after the
     * inline-literal indexed each admission, so exactly one of Array.each and
     * Bytes.each was selected; only {@link #prepare} is emitted for it.
     */
    INDEXED_EACH {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredArrayEach() || prepared.isStructuredBytesEach();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            return prepared.isStructuredArrayEach()
                    ? prepared.prepareStructuredArrayEach()
                    : prepared.prepareStructuredBytesEach();
        }
    },

    /**
     * I113 (former PrepareStructuredTwoParameterEachCall): reached only after
     * the inline-literal two-parameter each admission, so exactly one of
     * Environment.each, Map.each and IdentityMap.each was selected. For
     * Environment.each the preparation still validates callability, then
     * converts, validates and canonically orders the complete portable
     * snapshot before any callback child exists.
     */
    TWO_PARAMETER_EACH {
        @Override
        boolean admits(StructuredNativeInvocation prepared) {
            return prepared.isStructuredEnvironmentEach()
                    || prepared.isStructuredMapEach()
                    || prepared.isStructuredIdentityMapEach();
        }

        @Override
        Object prepare(StructuredNativeInvocation prepared) {
            if (prepared.isStructuredEnvironmentEach()) {
                return prepared.prepareStructuredEnvironmentEach();
            }
            return prepared.isStructuredMapEach()
                    ? prepared.prepareStructuredMapEach()
                    : prepared.prepareStructuredIdentityMapEach();
        }
    };

    /** The exact capability predicate of the protocol this constant names. */
    abstract boolean admits(StructuredNativeInvocation prepared);

    /**
     * The structured state this protocol prepares from an admitted call;
     * precondition {@link #admits}.
     */
    abstract Object prepare(StructuredNativeInvocation prepared);
}
