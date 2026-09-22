#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="$ROOT_DIR/docs/reports"
EXEC_PLUGIN="org.codehaus.mojo:exec-maven-plugin:3.5.0:java"

mkdir -p "$REPORT_DIR"

(
  cd "$ROOT_DIR/backend"
  mvn -B -Dtest='*ReportTest,RagEvaluationRunnerTest,*AppointmentConcurrencyIT' test
  mvn -B -DskipTests test-compile "$EXEC_PLUGIN" \
    -Dexec.mainClass=com.medora.eval.context.ContextEfficiencyReport \
    -Dexec.classpathScope=test \
    -Dexec.args="$REPORT_DIR"
  mvn -B -DskipTests test-compile "$EXEC_PLUGIN" \
    -Dexec.mainClass=com.medora.eval.rag.RagEvaluationRunner \
    -Dexec.classpathScope=test \
    -Dexec.args="$REPORT_DIR"
  mvn -B -DskipTests test-compile "$EXEC_PLUGIN" \
    -Dexec.mainClass=com.medora.eval.appointment.AppointmentLoadReport \
    -Dexec.classpathScope=test \
    -Dexec.args="$REPORT_DIR"
)

for report in context-efficiency rag-evaluation appointment-load; do
  test -s "$REPORT_DIR/$report.json"
  test -s "$REPORT_DIR/$report.md"
done

printf 'Generated sanitized A-stage reports in %s\n' "$REPORT_DIR"
