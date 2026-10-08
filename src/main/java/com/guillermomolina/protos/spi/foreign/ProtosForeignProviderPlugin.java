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
package com.guillermomolina.protos.spi.foreign;

/**
 * Public service contract of one external foreign-module provider (I085-A).
 *
 * <p>A provider is an independent artifact (one or more JARs installed outside Protos) that
 * declares this interface in {@code META-INF/services}. Protos discovers it only from provider
 * paths explicitly configured by the host, never from the global class path, and fixes it in the
 * RuntimeHost provider registry when that host is constructed (PLAT053). Its authority profile is
 * selected by the host, never by the provider or by an import specifier (PLAT052).
 *
 * <p>The contract is independent of the execution mechanism: a provider may be backed by a
 * Truffle language, host Java, a native library reached through FFM or JNI, an external bridge,
 * or anything else. Foreign values are opaque handles of the provider's own representation; the
 * provider classifies them and performs the authorized foreign operations through its {@link
 * ProtosForeignValueOperations}. It never implements Protos semantics: module keys, facades,
 * caching, identity, Actor isolation, conversion rules, errors, and lifetime belong to the shared
 * Protos foreign substrate.
 *
 * <p>Registration runs only {@link #providerId()} and {@link #scheme()}; it must not initialize a
 * foreign runtime. Every other method runs lazily, on first real use of the scheme.
 */
public interface ProtosForeignProviderPlugin {
    /**
     * Stable host-metadata identity of this provider; non-empty and without surrounding
     * whitespace. It is never a guest-visible name.
     */
    String providerId();

    /**
     * The import scheme this provider owns: {@code import("scheme:target")}. It must match {@code
     * [a-z][a-z0-9+.-]*} and must not be a scheme reserved by source module specifiers.
     */
    String scheme();

    /**
     * Canonicalizes the exact target spelling that follows the scheme without opening a session.
     * Equivalent spellings must return equal strings; the result must be stable semantic identity
     * and must never encode a session, Context, or runtime address. A thrown exception rejects
     * the import.
     */
    String canonicalTarget(String target);

    /**
     * Opens one logical session. Protos opens at most one live session per Actor and provider,
     * owns its Actor association, and closes it with that Actor or its Process; a closed session
     * is never reopened or reused.
     */
    ProtosForeignPluginSession openSession(ProtosForeignPluginEnvironment environment)
            throws Exception;

    /**
     * The provider's single value contract. Called once, lazily, when the provider is first
     * used; it must not depend on any session.
     */
    ProtosForeignValueOperations valueOperations();
}
