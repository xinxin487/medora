#!/usr/bin/env bash
set -euo pipefail

java_command="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
java_version="$(${java_command} -version 2>&1 | head -n 1)"
if [[ "${java_version}" != *'version "21'* ]]; then
  echo "JDK 21 is required. Set JAVA_HOME to a JDK 21 installation before running this script." >&2
  exit 1
fi

(cd backend && mvn -Dtest='*Adk*,*Retrieval*,*Appointment*' test)
(cd backend && mvn test-compile exec:java -Dexec.mainClass=com.medora.eval.EvalCli -Dexec.classpathScope=test -Dexec.args="--cases src/test/resources/eval/cases/evolution-baseline-v1.yaml --scenarios src/test/resources/eval/scenarios/evolution-baseline-v1.yaml --report-dir ../docs/reports/m0-agent-baseline --threshold 1.0")
(cd frontend && npm run build)
