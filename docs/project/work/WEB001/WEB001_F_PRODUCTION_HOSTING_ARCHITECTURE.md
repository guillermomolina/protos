# WEB001-F — Production hosting architecture

GitHub coordination: Issue
[#294](https://github.com/guillermomolina/protos/issues/294), child of
WEB001 / [#278](https://github.com/guillermomolina/protos/issues/278).

Status: **RATIFIED / CLOSED — CANDIDATE B SELECTED**

## Decision trigger

WEB001-B selected GitHub Pages as the initial hosting mechanism while explicitly
keeping the generated website as ordinary portable static output. After the
website bootstrap and public landing were implemented, the project owner
identified an existing private Docker/Traefik production environment already
used for other `*.guillermomolina.com` services and asked whether the Protos site
should use that environment instead.

`protos-website/AGENTS.md` treats a change to durable hosting/deployment
architecture as an explicit-approval checkpoint. WEB001-F therefore stopped
implementation and compared the alternatives before any production Docker stage,
Traefik stack, DNS change or Pages activation was published.

## Constraints retained from WEB001-B

WEB001-F changes hosting only. The following remain ratified:

- `guillermomolina/protos-website` is the independent public companion repo;
- `guillermomolina/protos` remains canonical for language/specification and the
  maintained sources it owns;
- the website consumes canonical Protos content read-only from an exact locked
  Git revision;
- native Node and Docker/Compose development paths use the same application
  contracts;
- Astro + Starlight remains the website framework;
- output remains an ordinary static site;
- website and consumed Protos revisions remain identifiable for provenance; and
- any future service executing untrusted Protos code remains an independent
  security/deployment boundary.

## Alternatives considered

### Candidate A — GitHub Pages only

Keep the originally ratified Pages deployment.

This minimizes operations and provides managed static hosting, but introduces a
separate production control plane from the owner's existing infrastructure.

### Candidate B — self-hosted Docker + Traefik only

Keep the public website repository infrastructure-neutral, add a generic
multi-stage production image that contains only built static output in its final
runtime, and keep real routing/TLS/network deployment configuration in private
`guillermomolina/docker-home`.

This creates one production authority, matches the existing operating model,
keeps private topology private, permits exact image/revision identification and
rollback, and avoids production source bind mounts.

### Candidate C — dual active hosting

Operate Pages and self-hosting as concurrent production-capable mechanisms.

Rejected because it creates two deployment authorities, drift and DNS/validation
complexity without a current requirement for active-active hosting.

### Candidate D — Pages primary with self-hosted standby

Keep Pages canonical and maintain Docker as a dormant fallback.

Rejected because the mostly unused path can silently rot while still imposing a
second deployment contract.

## Ratified decision

On 2026-09-10, after the alternatives and recommendation were presented, the
project owner explicitly approved **Candidate B**.

The selected production path is:

```text
protos-website revision
        |
        | multi-stage static build
        v
immutable production image
        |
        | private Compose deployment
        v
Traefik `proxy` network
        |
        | TLS / Let's Encrypt
        v
protos.guillermolina.com
```

The final production image must contain the static serving runtime and built site
only; Node/build dependencies do not belong in the serving layer. The running
container must not depend on a bind-mounted source checkout.

Traefik-specific labels, network conventions, host paths and operational
configuration belong to private `guillermomolina/docker-home`, not to the public
website repository.

GitHub Pages is **superseded** as the WEB001 production host. The existing
unactivated/manual Pages workflow is not retained as standby; a later bounded
implementation slice removes it while adding the public generic production-image
contract.

## Operational consequences

Implementation proceeds in separate bounded steps:

1. add and validate the generic production Docker target in
   `guillermomolina/protos-website`, updating website-local governance to reflect
   the ratified hosting model and retiring the dormant Pages workflow;
2. add `protos.guillermolina.com/` privately to `guillermomolina/docker-home`
   following its existing Compose/Makefile/Traefik conventions; and
3. perform DNS binding and actual public activation only as an explicit final
   operational action.

A future move to managed static hosting or a CDN remains possible because the
website artifact is still ordinary static output. Such a move would be a later
hosting decision, not a reason to maintain two production authorities now.

## Non-effects

This ratification:

- changes no Protos language/specification semantics;
- changes no runtime or Standard Library implementation;
- changes no website code or production container yet;
- activates no GitHub Pages environment;
- changes no DNS;
- publishes no private infrastructure details into the public website repo;
- introduces no playground runtime; and
- changes no implementation version or license terms.
