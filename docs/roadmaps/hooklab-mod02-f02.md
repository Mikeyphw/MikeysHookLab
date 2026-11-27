# MOD-02-F02 — D8 Synthetic Descriptor & Adaptive Navigation Repair

Preimage: PR 37 head `6fd750780d280d1db5c878fd0c318d706cdcf012`. Hosted run 45938203070 attempt 1: 40 successful, 4 failed, 3 skipped. Visual pack signing refused; the 600dp real navigation gesture did not reach the catalog; the aggregate and final gates refused accordingly.

D8 emits valid synthetic interface classes with hyphens (e.g. `Callback-IA` and `Callback-CC`). The previous class descriptor profile rejected them. Both independent readers now allow hyphens only in the final simple class name, preserving exact module namespace, entrypoint, integrity and uniqueness checks. Cross-language signed fixtures exercise these names; actual hosted D8/ART remains mandatory. The failed standalone DEX was not retained, so the exact failed descriptor is not asserted. The CLI reports bounded validation stages and hosted build emits the DEX digest and length before signing.

The 600dp evidence records a focused window with no catalog selection, title or search field after the gesture. Its exact cause is not established. The test now waits for stable visible navigation geometry, binds to the current decor and injects a real touch at the visible center. It does not programmatically select a route, retry the gesture, suppress accessibility checks or relax catalog assertions. All ten adaptive cases remain required.
