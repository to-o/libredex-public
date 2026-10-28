# Repository AI Agent Instructions

These instructions apply to AI coding assistants working in this repository.

## Git workflow
- The default integration branch is `master`.
- Do not create pull requests for routine changes.
- Do not create feature, temporary, or review branches unless the user explicitly requests one.
- For user-authorized implementation work, make changes on `master`, run relevant build/tests, then commit and push directly to `master`.
- Do not push untested or failing changes. If tests cannot be run, explain the limitation and obtain direction before pushing.
- Never force-push or rewrite published history without explicit authorization.
- At completion, report the changes, test results, and commit SHA; do not provide PR links.

## Architecture priorities
- Prefer Samsung's native DeX, SecondaryLauncher, DesktopMode, WindowManager, SystemUI, and physical DP/HDMI output over reimplementations.
- Keep the architecture minimal, easy to use, and low-overhead.
- Use the smallest necessary compatibility hooks. Avoid redundant rendering, polling, services, and video encoding on wired DeX.
- Preserve the existing wireless Sunshine/Moonlight pipeline as a separate output path.
- Verify device/One UI compatibility and rollback safety before removing existing workarounds.

## Current architecture review
- Review `docs/ARCHITECTURE_SIMPLIFICATION_PLAN.md` before planning any LibreDeX simplification work.
- The plan is a **review-first draft**. For review requests, inspect the current master source and refine the document with evidence, risks, and tests; do not change runtime code without explicit implementation authorization.
- Preserve Samsung-native behavior, existing device-proven compatibility workarounds, and safe rollback. Do not equate fewer lines of code with better performance.
