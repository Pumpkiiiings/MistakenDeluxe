# ADR-001: Incremental modular refactor

## Status

Accepted

## Context

Mistaken contains substantial gameplay behavior, configuration, scripting and third-party integrations. The existing API/Core/addon boundaries are useful, but lifecycle management, optional integrations and large scripting bindings accumulated avoidable coupling.

## Decision

Keep the modular monolith and improve it incrementally. Preserve public API contracts and gameplay configuration while extracting focused adapters and services. Optional plugin integrations must not introduce local-file build dependencies.

## Trade-offs

- Existing code remains in service while it is gradually simplified.
- Temporary adapters may use reflection when the alternative is a hard optional dependency.
- Larger hotspots are split only after characterization tests cover their observable behavior.

## Consequences

- Releases can continue during the refactor.
- Build reproducibility and regression protection improve before structural changes.
- A full rewrite is not required unless future requirements invalidate the current module boundaries.
