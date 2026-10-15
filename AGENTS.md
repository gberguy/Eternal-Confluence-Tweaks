# Eternal Confluence Tweaks — Codex Instructions

## Project and compatibility
- This repository is Eternal Confluence Tweaks (ECT), a modular compatibility and bug-fix mod designed for the Eternal Confluence modpack but usable independently.
- Target Minecraft 1.12.2, Forge 1.12.2, and Java 8-compatible mod bytecode. The pack may run on Cleanroom, but ECT must not require Cleanroom and must also work with ordinary Forge.
- Follow the existing package, PatchId, PatchSettings, config-generation, event-handler, ASM/coremod, and test conventions. Inspect current implementations before extending them.
- Each fix must be independently configurable unless the task explicitly specifies otherwise. Uninstalled target mods, disabled patches, and combinations of optional mods must not break startup or unrelated fixes.
- Keep client/server boundaries correct. Do not eagerly load optional-mod classes from classes loaded unconditionally. Preserve safe fallbacks when a target mod or expected API is missing.

## Changes and scope
- Implement only the requested behavior. Preserve working patches and unrelated functionality; avoid speculative refactors, cleanup, or dependency updates.
- Inspect `git status`, tracked diffs, and untracked files before editing. Treat all pre-existing local changes as user work. Do not discard, overwrite, or stage them without an explicit request.
- Never commit, push, tag, create releases, or alter Git history unless explicitly instructed. Do not automatically bump `mod_version`.
- Do not add explanatory source comments, section-label comments, docstrings, or other nonfunctional annotation unless explicitly requested. Preserve existing comments when modifying code.
- Do not create or revise README, CHANGELOG, credits, or other documentation unless the specific task asks for it. Report needed documentation or version changes instead of making them unasked.
- Do not modify target-mod JARs or bundle them into ECT.

## Investigation and implementation
- For mod compatibility work, verify actual behavior against the exact released target-mod JAR and APIs where possible; upstream source branches can differ from shipped releases.
- Identify the failing method, condition, or event path before implementing a fix. Clearly distinguish verified causes from hypotheses. Do not add broad workarounds that bypass normal game mechanics or can double-apply effects.
- Keep fixes narrowly targeted and reversible by their config switches. Preserve ordinary behavior when the target mod is absent, the condition is not met, or the patch is disabled.
- Use the project's established `compileOnly rfg.deobf(...)` approach for optional compile-time mod APIs when appropriate. Do not shade dependencies. If dependencies are added, maintain compatibility with both Gradle and the standalone `tools/build.py` CI build.
- For ASM/coremod changes, handle class-loading and transformation timing carefully and add regression tests for the modified bytecode or runtime decisions. Avoid fragile assumptions about class names, method descriptors, or load order.
- If proof requires diagnostics, keep them specific, gated, and temporary. Do not represent a hypothesis as a confirmed fix.

## Build and verification
- Use the existing Gradle wrapper and build setup. The local environment already configures Gradle's Java runtime; do not override `JAVA_HOME` or `org.gradle.java.home` without a demonstrated reason. The mod itself must remain Java 8 compatible.
- After code changes, run `python tools/build.py`, `.\gradlew.bat build`, relevant tests, and `git diff --check` where the environment permits. Remember that Git's normal diff does not show untracked files.
- Check that the generated ECT JAR does not include optional dependency classes. Distinguish compilation/ASM tests from actual in-game verification.
- If a tool, dependency, or test is unavailable, say exactly which validation was not performed. Do not assert an in-game fix without an in-game test.

## Completion report
- Summarize behavior changed, exact files created/modified, tests run and results, remaining risks or in-game tests, and any explicitly unrequested release/documentation work still needed.
- Leave final review, staging, commits, tags, pushes, and publishing to the user unless separately authorized.
