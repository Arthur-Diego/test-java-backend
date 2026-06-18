#!/usr/bin/env bash
# Run this ONCE before your interview to pre-fetch dependencies.
set -euo pipefail

echo "java: $(java -version 2>&1 | head -1)"
echo "mvn:  $(mvn -v | head -1)"
mvn -q dependency:go-offline
mvn -q compile -DskipTests
echo
echo "✓ Setup complete. On interview day, run:"
echo "    mvn spring-boot:run"
echo "API will listen on http://localhost:8080"
