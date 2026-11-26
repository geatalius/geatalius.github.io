@echo off
chcp 65001 > nul
cd /d "%~dp0"
where python > nul 2>&1
if errorlevel 1 (
  echo Python이 설치되어 있지 않습니다. https://www.python.org/downloads/ 에서 설치하세요.
  echo 설치할 때 "Add python.exe to PATH" 를 꼭 체크하세요.
  pause
  exit /b 1
)
if not exist .venv (
  echo 처음 실행: 필요한 패키지를 설치합니다...
  python -m venv .venv
  .venv\Scripts\python -m pip install --upgrade pip > nul
  .venv\Scripts\python -m pip install -r requirements.txt
)
.venv\Scripts\python server.py %*
pause
