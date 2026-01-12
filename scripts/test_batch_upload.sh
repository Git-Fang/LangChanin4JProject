#!/usr/bin/env bash
set -euo pipefail
PORT=8080
BASE_URL="http://localhost:/xiaozhi/personal/upload/batch"
ROOT_DIR=/d/个人资料/AI实战--RAG增强型翻译/RAGTranslation--docker
TEST_DIR="/test-batch-files"
mkdir -p ""
FILE1=\/file1.txt\nFILE2=\/file2.txt\necho "Testing batch upload to: "
print_json() { if command -v jq >/dev/null 2>&1; then echo "" | jq; else echo ""; fi; }
echo "=== VECTORIZE Test ==="
RESPONSE=
print_json ""
echo "=== TERMS Test ==="
RESPONSE=
print_json ""
echo "Test completed."
