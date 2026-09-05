/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import java.util.Optional;

/** Host boundary for canonical module resolution and source retrieval. */
public interface ProtosModuleResolver {
    ProtosModuleKey resolve(String exactSpecifier, Optional<ProtosModuleKey> importingModule) throws Exception;
    String loadSource(ProtosModuleKey key) throws Exception;

    static ProtosModuleResolver rejecting() {
        return new ProtosModuleResolver() {
            @Override
            public ProtosModuleKey resolve(String exactSpecifier, Optional<ProtosModuleKey> importingModule)
                    throws Exception {
                throw new Exception("no host module resolver configured");
            }
            @Override
            public String loadSource(ProtosModuleKey key) throws Exception {
                throw new Exception("no host module resolver configured");
            }
        };
    }
}
