#!/usr/bin/env bash
# Script to launch Astra AI FastAPI Backend
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "=== Launching Astra AI Backend ==="
if [ -f ".env" ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

python3 main.py
