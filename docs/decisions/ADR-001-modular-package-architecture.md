# ADR-001: Modular Package Architecture Inside `:app` vs. Granular Gradle Submodules

## Context
The product specification outlines clean boundaries across models, design system, decoders, computer-vision math, shader pipeline, storage, and UI features.

## Decision
Implement strict **package-level Clean Architecture boundaries** (`com.example.prismdiff.core.*`, `domain.*`, `feature.*`) inside the `:app` Gradle module with explicit Kotlin interfaces and constructor injection, rather than splitting into 15–20 separate Gradle subprojects initially.

## Alternatives Considered
1. **20+ Gradle subprojects (`:core:model`, `:core:vision`, `:feature:studio`, ...)**: Enforces compile-time visibility but adds substantial AGP 9.1.1 build configuration overhead, duplicated manifest/namespace files, and slower incremental builds in container environments.
2. **Flat package structure**: Fast to start but causes UI/math coupling and violates SOLID boundaries.

## Trade-offs & Reversibility
- **Pros**: Fast builds, seamless KSP Room & Compose compiler integration, zero boilerplate duplication, and clean test execution via `gradle :app:testDebugUnitTest`.
- **Cons**: Developers/agents must respect package dependency direction (`feature -> domain -> core`, never `core -> feature`) by discipline and code review.
- **Trigger to revisit**: Split into Gradle library modules if APK modularization (Dynamic Feature Delivery) or >100k LOC compile times require separate compilation caches.
