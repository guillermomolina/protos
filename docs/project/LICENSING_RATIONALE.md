# Why Protos uses APL-1.0

> **Non-normative licensing rationale.** This document explains the project goals
> and trade-offs that led to choosing the Adaptive Public License 1.0 (APL-1.0).
> It does not change or interpret the license. The authoritative terms are in
> [`LICENSE.TXT`](../../LICENSE.TXT).

## The short version

Protos is intended to be comfortable to use for open-source, commercial, and
proprietary software alike.

Choosing Protos as a programming language should not force an application to
adopt the Protos license. Programs written in Protos are not automatically
covered by APL-1.0 merely because they are compiled or run with Protos.

At the same time, the project wanted the Protos implementation itself to remain
open when modified versions of that implementation are distributed. APL-1.0 was
chosen because its boundary maps unusually well to those two goals.

In project terms, the intended balance is:

> **Your software can use the license that fits your project. Protos itself
> remains open when the APL requires reciprocity.**

The license text, not this summary, determines whether a particular work or
scenario falls on either side of that boundary.

## What we wanted from the license

The licensing discussion started from practical goals rather than from a
preference for a particular license family.

### Make Protos genuinely open source

The project wanted an OSI-approved open-source license, including for commercial
use. This ruled out keeping the previous Server Side Public License (SSPL) as the
long-term license even though some of its reciprocity goals were attractive.

### Do not claim applications written in Protos

A language implementation and a program written with that language are different
things. The project did not want choosing Protos to dictate whether an
application, service, game, library, or other product must itself be open source.

This distinction matters for adoption: companies and individuals should be able
to evaluate Protos on its technical merits without first changing the licensing
model of the software they intend to build.

### Allow separately licensed independent extensions

The project also wanted room for ecosystems containing both open and proprietary
components. APL-1.0 explicitly defines `Independent Module` and `Larger Work`
concepts, and Sections 3.6 and 3.7 describe how those components relate to the
Licensed Work.

An independent module or plugin can therefore use a separate license when it
actually satisfies the applicable APL definitions. This document deliberately
does not try to replace those definitions with a simplified technical rule.

### Allow private internal experimentation

Organizations often need local patches, integrations, diagnostics, or
experimental changes that are never distributed outside the organization. The
project did not want ordinary internal use by itself to create a publication
obligation.

APL-1.0 addresses that case directly in Section 3.5, **Internal Use
Modifications**. Those modifications can remain internal unless the conditions
in the license that make distribution obligations applicable are reached.

### Preserve reciprocity when modified Protos is distributed

The other side of the balance is that modifying the Protos implementation and
then distributing the licensed work is different from merely writing software in
Protos.

For distributions of the Licensed Work, APL-1.0 contains source-availability and
license-preservation requirements. This is the reciprocity the project wanted:
people remain free to build proprietary software *with* Protos, while recipients
of a distributed modified Protos retain the rights provided by the APL for the
Protos portion.

The project also valued that APL's `Source Code` definition includes associated
interface definition files and scripts used to control compilation and
installation. The goal was meaningful source availability for the licensed work,
not a nominal source dump that omits the material needed to work with it.

## Alternatives considered

The choice was not a judgment that the alternatives are bad licenses. Each draws
a different boundary, and a different project could reasonably prefer one of
them.

### SSPL

Protos previously used the Server Side Public License v1. Its strong reciprocity
was compatible with some project instincts, but SSPL is not an OSI-approved
open-source license. Being an open-source project in the OSI sense was an
explicit goal, so SSPL was not retained.

### AGPL

The GNU Affero General Public License was a serious candidate. It is an
OSI-approved strong copyleft license, but its network-use provisions make remote
interaction with modified software an important part of its reciprocity model.

That was not the boundary Protos was trying to optimize for. The project wanted
to distinguish primarily between private/internal modification and distribution
of the Protos implementation, without making network use the central trigger.

### EUPL

The European Union Public Licence was also a strong candidate. Its open-source
status and reciprocal model were attractive.

For Protos, however, APL-1.0's explicit treatment of internal-use modifications,
independent modules, and larger works mapped more directly to the questions the
project wanted the license text to answer.

### RPL

The Reciprocal Public License was considered for similar reasons: it provides an
open-source reciprocity model without simply choosing a permissive license.

Again, the deciding factor was not that RPL was unsuitable in general. APL's
specific vocabulary around the boundary of the Licensed Work matched the Protos
use cases more naturally.

## Why APL-1.0 won

APL-1.0 brought the desired pieces together in one OSI-approved license:

- programs written using Protos are not automatically part of the Licensed Work;
- internal modifications have an explicit treatment in Section 3.5;
- independent modules and larger works have explicit treatment in Sections 3.6
  and 3.7;
- distribution of the Licensed Work carries reciprocal source and license
  obligations; and
- the source-code definition includes relevant build and installation scripts.

That combination is the main reason APL-1.0 was preferred. The objective was not
to maximize how much surrounding software becomes copyleft. It was to put the
reciprocity boundary around **Protos itself** while leaving a broad and useful
space for software built with, around, or on top of Protos.

## A community-oriented boundary

The license choice is meant to support an ecosystem, not narrow one.

The project welcomes open-source applications, proprietary applications,
commercial products, research, internal deployments, libraries, tooling, and
extensions. Contributors and users do not need to share the same business model
or licensing preferences for their own independent software.

What the project asks through APL-1.0 is narrower: when the license applies to a
distributed Protos Licensed Work, preserve the freedoms and source availability
that the license grants to its recipients.

Because real products can have complex boundaries, anyone making a legal
licensing decision should read the complete Protos-specific APL-1.0, including
its Exhibit A, in [`LICENSE.TXT`](../../LICENSE.TXT). This rationale records why
the project chose that license; it is not legal advice.
