# Autonomous Verification Rules (Cloud CI)

Do NOT run local Gradle builds. Instead, verify changes using GitHub Actions:

1. Commit and Push:
   git add .
   git commit -m "agent: <summary of changes>"
   git push origin master

2. Watch the Cloud Build:
   gh run watch

3. Handle Errors:
   - If the build fails, fetch the failure logs:
     gh run view --log-failed
   - Analyze the compiler or lint stack trace, modify the local files to fix the error, and push another commit.
   - Repeat until the build finishes successfully.

4. Download the Resulting APK:
   gh run download -n app-debug
