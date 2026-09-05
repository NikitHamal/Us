# On-device model bundle

Drop `needle2.cact` (~14 MB) into this folder to upgrade the offline provider from its
deterministic rule engines to real on-device inference.

`CactModelLoader` detects the file automatically at runtime:
- absent  -> `ModelState.RulesOnly`, offline confidence stays lower so the router can escalate
- present -> `ModelState.Ready`, confidence bonus applied, tool-calling surface exposed

No code change is required, and the file is never uploaded anywhere.
