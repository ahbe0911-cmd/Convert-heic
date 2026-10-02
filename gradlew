#!/usr/bin/env sh
set -e
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. Open the project in Android Studio or run the GitHub Actions workflow." >&2
exit 1
