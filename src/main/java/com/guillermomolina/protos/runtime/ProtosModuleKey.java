/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.runtime;

import java.util.Objects;

/** Canonical host-produced module identity. This is an internal runtime value. */
public record ProtosModuleKey(String canonicalId) {
    public ProtosModuleKey {
        Objects.requireNonNull(canonicalId, "canonicalId");
    }
}
