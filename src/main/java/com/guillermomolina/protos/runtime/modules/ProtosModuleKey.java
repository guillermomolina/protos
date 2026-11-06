/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime.modules;

import java.util.Objects;

/**
 * Canonical host-produced module identity. This is an internal runtime value.
 *
 * <p>D242: the resolved target kind is part of canonical identity. A MODULE key identifies one
 * logical module directory whose public object is initialized by its {@code module.protos}; a
 * SOURCE key identifies one exact authorized {@code .protos} file whose complete execution
 * Context is the import result; a FOREIGN key identifies one provider-authorized facade. Keys of
 * different kinds never compare equal, even when their canonical spellings coincide.
 */
public record ProtosModuleKey(Kind kind, String canonicalId) {
    /** D242 resolved target kinds. */
    public enum Kind {
        MODULE,
        SOURCE,
        FOREIGN
    }

    public ProtosModuleKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(canonicalId, "canonicalId");
    }

    public static ProtosModuleKey module(String canonicalId) {
        return new ProtosModuleKey(Kind.MODULE, canonicalId);
    }

    public static ProtosModuleKey source(String canonicalId) {
        return new ProtosModuleKey(Kind.SOURCE, canonicalId);
    }

    public static ProtosModuleKey foreign(String canonicalId) {
        return new ProtosModuleKey(Kind.FOREIGN, canonicalId);
    }

    public boolean isModule() {
        return kind == Kind.MODULE;
    }

    public boolean isSource() {
        return kind == Kind.SOURCE;
    }

    public boolean isForeign() {
        return kind == Kind.FOREIGN;
    }
}
