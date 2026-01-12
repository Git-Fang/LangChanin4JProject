#!/usr/bin/env bash
set -euo pipefail

# Local end-to-end test for batch upload endpoint
ROOT_DIR=$(cd "$(dirname "$0")/.." >/dev/null 2>&1; pwd)
TEST_DIR="$ROOT_DIR/test-batch-files"
mkdir -p "$TEST_DIR"
FILE1="$TEST_DIR/file1.txt"
FILE2="$TEST_DIR/file2.txt"
echo "This is a sample document for batch testing. File 1." > "$FILE1"
echo "Another sample document for batch testing. File 2." > "$FILE2"

BASE_URL="http://localhost:8000/xiaozhi/personal/upload/batch"

echo 'Checking server availability (will show errors if not running)...'
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL" || true)
if [ "$HTTP_CODE" -ge 400 ]; then
  echo "Server responded with status ${HTTP_CODE}. Ensure backend is running on port 8000."
else
  echo "Server reachable (HTTP ${HTTP_CODE}). Proceeding with tests."
fi

# Helper to print response nicely if jq available
print_json() {
  if command -v jq >/dev/null 2>&1; then
    echo "$1" | jq
  else
    echo "$1"
  fi
}

# VECTORIZE path test
echo "\n=== VECTORIZE Test ==="
RESPONSE=$(curl -s -X POST \
  -F "files=@$FILE1" \
  -F "files=@$FILE2" \
  -F "operation=VECTORIZE" \
  "$BASE_URL")
print_json "$RESPONSE"

# TERMS path test
echo "\n=== TERMS Test ==="
RESPONSE=$(curl -s -X POST \
  -F "files=@$FILE1" \
  -F "files=@$FILE2" \
  -F "operation=TERMS" \
  "$BASE_URL")
print_json "$RESPONSE"

echo "\nTest completed. Cleaning up test files."
# Optional cleanup
rm -f "$FILE1" "$FILE2"; rmdir "$TEST_DIR" 2>/dev/null || true
