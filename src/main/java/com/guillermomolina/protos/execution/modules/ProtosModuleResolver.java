/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import java.util.Optional;

/**
 * Host authority boundary for {@code import(String)}.
 *
 * <p>D242: {@link #resolve} returns a canonical key whose {@link ProtosModuleKey.Kind} is
 * authoritative. A MODULE key names a logical module directory and {@link #loadSource} must return
 * exactly that directory's {@code module.protos}; a SOURCE key names one exactly authorized
 * {@code .protos} file and {@link #loadSource} returns exactly that file. Resolvers never return a
 * SOURCE key for a {@code module.protos} initializer.
 */
public interface ProtosModuleResolver {
    ProtosModuleKey resolve(String exactSpecifier, Optional<ProtosModuleKey> importingModule) throws Exception;
    ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception;

    static ProtosModuleResolver rejecting() {
        return new ProtosModuleResolver() {
            @Override
            public ProtosModuleKey resolve(String exactSpecifier, Optional<ProtosModuleKey> importingModule)
                    throws Exception {
                throw new Exception("no host module resolver configured");
            }
            @Override
            public ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception {
                throw new Exception("no host module resolver configured");
            }
        };
    }
}
