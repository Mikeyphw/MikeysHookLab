# UX-R02 candidate acceptance

Base: `b37a4a27447cbdcfaabda08da90942d1de83f5e3`, tree `045b9a5567a2a44a05a3244cc0e26e706c961f16`. All R02 runtime verdicts pending. Local JVM harness checks real pure route/cache classes; source guards do not prove Android behavior.

| Promise | Production path | Qualification evidence still required |
|---|---|---|
| P01 | `catalogHeader`, `openCatalogGroup` | Actual group header taps (icon, title, whitespace), zero writes |
| P02 | Independent chevron, state description, 48dp | Native tap, focus and expanded state checks; TalkBack |
| P03 | `CatalogRoutes.group`, `showApp`, group detail link | Single-hook and invalid ID journeys |
| P04 | `catalogCard`, `showApp` | Exact per-hook route, no redundant configure control |
| P05 | `CatalogRoutes.app`, `packageDetail` | Same-package filtered fixture, all registered hooks, exact Back |
| P06 | `AppIconProvider` real package metadata | Hosted installed icon fixture; actual target apps on device |
| P07 | `iconPackages`, `iconStack` | Deterministic deduplication, cap, RTL and missing drawable |
| P08 | Explicit availability enum and fallback | Missing, unknown, install/uninstall lifecycle |
| P09 | Shared `hostIcon`, `appIdentity` | Home/catalog/group/setup/scope/health semantics |
| P10 | `RouteTrail`, route frames, exact IDs | All entry origins, recreation and invalid restoration |
| P11 | `FlatUi.actionRow`, separate chevron | Touch bounds, keyboard, TalkBack |
| P12 | Native rows replacing legacy buttons | UI hierarchy, obsolete caller and guard checks |
| P13 | Persistent query/mode/expansion/scroll and saved focus/IME | Updates, recreation, process restart, large font |
| P14 | Navigation-only callbacks | Spy for preferences/scope, explicit controls regressions |
| P15 | `IconStateCache`, package receiver, configuration invalidation | Local cache behavior; real package lifecycle/rotation |

The native test suite exercises six candidate screens, including VIS-002–005, plus the existing functional fixture. No screenshot is an approved golden. CI24 retains exact source and attempt binding, uses bounded rate-limit-only retries, and fails closed with structured failure receipts.
