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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deliberately inert zero-authority in-process restriction for the plain-Java conformance provider
 * doubles. Those doubles reach no host object, I/O, network, process, native, environment,
 * reflection, class-loading, or polyglot facility, and nothing is provisioned to them; this
 * mechanism proves only that the runtime opens their compartments through an admitted mechanism.
 * It is test machinery, never a generic sandbox for arbitrary host code.
 */
final class ProtosForeignInertRestriction
        implements ProtosForeignProviderEnforcement.InProcessRestriction {
    final AtomicInteger confinements = new AtomicInteger();

    static Optional<ProtosForeignProviderEnforcement> zeroAuthority() {
        return Optional.of(new ProtosForeignInertRestriction());
    }

    @Override
    public ProtosForeignProviderCompartment openConfinedCompartment(
            ProtosForeignProviderFactory factory) {
        confinements.incrementAndGet();
        return factory.openCompartment();
    }
}
