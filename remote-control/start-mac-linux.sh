#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
if [ ! -d .venv ]; then
  echo "처음 실행: 필요한 패키지를 설치합니다..."
  python3 -m venv .venv
  .venv/bin/python -m pip install --upgrade pip > /dev/null
  .venv/bin/python -m pip install -r requirements.txt
fi
exec .venv/bin/python server.py "$@"
