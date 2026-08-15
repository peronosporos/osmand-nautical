# Execution & Verification Protocol (Cloud-Only)

1. Analyze & Plan:
   - Read relevant ViewModels, Repositories, Composables, and XML in HEAD.
   - Formulate a targeted change plan. Do not touch unrelated files.

2. Implement:
   - Apply production-ready code directly to the target files.
   - Zero-Stub Rule: Wire all callbacks, sliders, and UI events to real state or ViewModel functions. No empty TODOs.
   - Constraint: Do not run Gradle commands locally (no local assemble, test, or lint).

3. Pre-Commit Verification & Staging:
   - Check staged and untracked files:
     ```bash
     git status -s
     ```
   - Verify that only intended files were changed:
     ```bash
     git diff --stat
     ```
   - Stage, commit, and push (setting upstream tracking):
     ```bash
     git add <changed-and-new-files>
     git commit -m "<type>(<scope>): <concise description>"
     git push -u origin HEAD
     ```

4. Remote CI Monitoring & Self-Healing:
   - Wait 3 seconds for GitHub to register the workflow, then watch the latest run for HEAD:
     ```bash
     sleep 3 && gh run watch --exit-status
     ```
   - On Failure (Non-zero exit code):
     - Fetch failure logs: `gh run view --log-failed`
     - Analyze stack traces (compiler, lint, or test failures).
     - Apply fixes locally and push a new commit (max 3 retry attempts).
   - On Success (Exit Code 0):
     - Retrieve the built APK artifact: `gh run download -n app-debug`
