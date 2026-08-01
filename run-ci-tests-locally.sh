#!/usr/bin/env bash
#
# Local reproduction of the CI "test" stage.
#
# This mirrors the test loop from .gitlab-ci.yml / .github/workflows/gradle.yml:
# it discovers the same @JmcCheck/@Test methods (skipping @Disabled) and runs
# each one in its own daemonless Gradle invocation, in the same order. Run it
# from the repository root.
#
# Like the CI loop, each run is wrapped in `timeout` so a stuck/deadlocked test
# is killed after PER_TEST_TIMEOUT seconds (default 600 = 10 min) instead of
# hanging the whole sweep. The whole sweep is also bounded by an overall budget
# (TOTAL_BUDGET, default 10800 = 3 hours): once it is exhausted no further tests
# are started, and a test near the boundary is capped so the run does not exceed
# the budget. Timed-out, failed, and budget-skipped tests are collected and
# printed in a summary at the end; the script exits non-zero if any test timed
# out or failed.
#
# This is a throwaway debugging helper -- it does not need to be committed.
#
# Optional:
#   PER_TEST_TIMEOUT=<sec>  Per-method wall-clock limit (default 600 = 10 min).
#   TOTAL_BUDGET=<sec>      Overall wall-clock budget for the whole sweep
#                           (default 10800 = 3 hours).
#   START_AT=<substring>    Skip methods until one whose fully-qualified name
#                           contains <substring>, so you can jump near a suspected
#                           hang instead of running the whole suite first. Leave
#                           unset to run everything.
#
# Tip: to mimic the 2-vCPU GitHub-hosted runner, invoke under taskset, e.g.
#   taskset -c 0,1 ./run-ci-tests-locally.sh
#
# Note: on timeout, SIGTERM is sent first (so Gradle can tear down its worker),
# then SIGKILL 5s later as a backstop; a wedged worker JVM may briefly linger.
#
# Each method is echoed before it runs, so a hang clearly shows which test stalled.

set -eu

# --- Mirror the CI before_script environment (Z3 native libraries). Adjust the
# --- paths if your local libz3 lives elsewhere.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Djava.library.path=/usr/lib:/usr/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu/jni}"
export LD_LIBRARY_PATH="/usr/lib:/usr/lib/x86_64-linux-gnu:/usr/lib/x86_64-linux-gnu/jni:${LD_LIBRARY_PATH:-}"

TEST_FILES=$(find integration-test/src/test/java -name "*Test.java" \
  -exec grep -l -E "^[[:space:]]*@JmcCheck|^[[:space:]]*@Test" {} + \
  | sort)
if [ -z "$TEST_FILES" ]; then
  echo "No test classes found under integration-test/src/test/java."
  exit 0
fi
TEST_METHODS=$(for FILE in $TEST_FILES; do
  CLASS_NAME=$(printf "%s" "$FILE" | sed 's|integration-test/src/test/java/||; s|/|.|g; s|\.java$||')
  awk -v className="$CLASS_NAME" '
    BEGIN { in_block=0; want=0; disabled=0 }
    {
      line=$0
      if (in_block) {
        if (line ~ /\*\//) { sub(/^.*\*\//, "", line); in_block=0 } else { next }
      }
      if (line ~ /\/\*/) { in_block=1; sub(/\/\*.*$/, "", line) }
      sub(/^[[:space:]]+/, "", line)
      if (line ~ /^\/\// || line == "") { next }
    }
    /^[[:space:]]*@Disabled/ { disabled=1; next }
    /^[[:space:]]*@JmcCheck/ { want=1; next }
    /^[[:space:]]*@Test/ { want=1; next }
    want && $0 ~ /void[[:space:]]+[[:alnum:]_]+[[:space:]]*\(/ {
      if (!disabled) {
        line=$0
        sub(/^.*void[[:space:]]+/, "", line)
        sub(/[[:space:]]*\(.*/, "", line)
        print className "." line
      }
      want=0
      disabled=0
    }
  ' "$FILE"
done)
if [ -z "$TEST_METHODS" ]; then
  echo "No test methods found under integration-test/src/test/java."
  exit 0
fi

PER_TEST_TIMEOUT="${PER_TEST_TIMEOUT:-600}"   # 10 minutes per test
TOTAL_BUDGET="${TOTAL_BUDGET:-10800}"          # 3 hours for the whole sweep

started=1
[ -n "${START_AT:-}" ] && started=0

TIMED_OUT=""
FAILED=""
NOT_RUN=""

# $SECONDS is the elapsed wall-clock time since the script started.
for TEST_METHOD in $TEST_METHODS; do
  if [ "$started" -eq 0 ]; then
    case "$TEST_METHOD" in
      *"$START_AT"*) started=1 ;;
      *) echo ">>> skipping $TEST_METHOD (waiting for START_AT='$START_AT')"; continue ;;
    esac
  fi

  # Stop starting new tests once the overall 3h budget is gone.
  remaining=$(( TOTAL_BUDGET - SECONDS ))
  if [ "$remaining" -le 0 ]; then
    echo ">>> budget (${TOTAL_BUDGET}s) exhausted; not running: $TEST_METHOD"
    NOT_RUN="${NOT_RUN}${NOT_RUN:+ }$TEST_METHOD"
    continue
  fi

  # Cap this test's timeout so the sweep never overruns the overall budget.
  this_to="$PER_TEST_TIMEOUT"
  clamped=0
  if [ "$this_to" -gt "$remaining" ]; then
    this_to="$remaining"
    clamped=1
  fi

  echo ">>> running $TEST_METHOD (timeout ${this_to}s; ${remaining}s left in budget)"
  rc=0
  # SIGTERM at the limit so Gradle can shut its worker down cleanly;
  # SIGKILL 30s later as a backstop if the deadlocked JVM ignores TERM.
  # --console=plain + </dev/null: the rich console's cursor control triggers
  # SIGTTOU under a script and stops the launcher JVM (state T); plain output
  # and a detached stdin avoid that.
  timeout -k 30s "${this_to}s" \
    ./gradlew --no-daemon --console=plain :integration-test:test --tests "$TEST_METHOD" </dev/null || rc=$?
  if [ "$rc" -eq 124 ] || [ "$rc" -eq 137 ]; then
    if [ "$clamped" -eq 1 ]; then
      # Cut short by the overall budget, not a genuine per-test hang.
      echo ">>> BUDGET CUTOFF (ran ${this_to}s, 3h budget reached): $TEST_METHOD"
      NOT_RUN="${NOT_RUN}${NOT_RUN:+ }$TEST_METHOD"
    else
      echo ">>> TIMEOUT after ${this_to}s: $TEST_METHOD"
      TIMED_OUT="${TIMED_OUT}${TIMED_OUT:+ }$TEST_METHOD"
    fi
  elif [ "$rc" -ne 0 ]; then
    echo ">>> FAILED (exit $rc): $TEST_METHOD"
    FAILED="${FAILED}${FAILED:+ }$TEST_METHOD"
  fi
done

echo
echo "===================== summary (elapsed ${SECONDS}s) ====================="
if [ -n "$TIMED_OUT" ]; then
  echo "Timed out (> ${PER_TEST_TIMEOUT}s):"
  for t in $TIMED_OUT; do echo "  - $t"; done
fi
if [ -n "$FAILED" ]; then
  echo "Failed:"
  for t in $FAILED; do echo "  - $t"; done
fi
if [ -n "$NOT_RUN" ]; then
  echo "Not run / cut short (3h budget):"
  for t in $NOT_RUN; do echo "  - $t"; done
fi
if [ -z "$TIMED_OUT" ] && [ -z "$FAILED" ] && [ -z "$NOT_RUN" ]; then
  echo "All tests passed within ${PER_TEST_TIMEOUT}s each (total ${SECONDS}s)."
  exit 0
fi
# Non-zero only on real timeouts/failures; a clean budget cutoff alone is not a failure.
if [ -n "$TIMED_OUT" ] || [ -n "$FAILED" ]; then
  exit 1
fi
exit 0
