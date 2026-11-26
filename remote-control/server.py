#!/usr/bin/env python3
"""
폰 리모컨 서버 - 안드로이드 휴대폰의 브라우저로 노트북을 원격 제어합니다.

노트북에서 이 파일을 실행한 뒤, 같은 Wi-Fi에 연결된 휴대폰으로
화면에 표시되는 주소(또는 QR 코드)에 접속하면 됩니다.

필요 패키지: pip install -r requirements.txt
"""

import argparse
import asyncio
import io
import json
import os
import secrets
import socket
import sys
import threading
from concurrent.futures import ThreadPoolExecutor

# Windows 화면 배율(125%, 150% 등)에서 좌표가 어긋나지 않도록 DPI 인식을 먼저 켭니다.
if sys.platform == "win32":
    try:
        import ctypes

        ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except Exception:
        try:
            ctypes.windll.user32.SetProcessDPIAware()
        except Exception:
            pass

try:
    import mss
    from aiohttp import WSMsgType, web
    from PIL import Image, ImageDraw
    from pynput.keyboard import Controller as KeyboardController
    from pynput.keyboard import Key
    from pynput.mouse import Button
    from pynput.mouse import Controller as MouseController
except ImportError as e:
    print(f"[오류] 필요한 패키지가 없습니다: {e.name}")
    print("       다음 명령으로 설치하세요:  pip install -r requirements.txt")
    sys.exit(1)

HERE = os.path.dirname(os.path.abspath(__file__))

mouse = MouseController()
keyboard = KeyboardController()

# ---------------------------------------------------------------------------
# 화면 캡처 (mss 는 스레드 간 공유가 안 되므로 전용 스레드 하나에서만 사용)
# ---------------------------------------------------------------------------
_capture_pool = ThreadPoolExecutor(max_workers=1, thread_name_prefix="capture")
_tls = threading.local()


def _sct():
    if not hasattr(_tls, "sct"):
        _tls.sct = (getattr(mss, "MSS", None) or mss.mss)()
    return _tls.sct


def list_monitors():
    mons = _sct().monitors[1:]  # [0] 은 전체 화면을 합친 가상 모니터
    return [
        {"left": m["left"], "top": m["top"], "width": m["width"], "height": m["height"]}
        for m in mons
    ]


def get_monitor(index):
    mons = _sct().monitors
    if index < 1 or index >= len(mons):
        index = 1
    return mons[index]


def capture_jpeg(mon_index, scale, quality):
    mon = get_monitor(mon_index)
    shot = _sct().grab(mon)
    img = Image.frombytes("RGB", shot.size, shot.bgra, "raw", "BGRX")

    # macOS 레티나처럼 캡처 픽셀과 좌표 단위가 다를 수 있으므로 비율을 따로 계산
    px_ratio = img.width / mon["width"]
    if scale < 0.999:
        new_size = (max(1, int(img.width * scale)), max(1, int(img.height * scale)))
        img = img.resize(new_size, Image.BILINEAR)
        px_ratio *= scale

    # 스크린샷에는 마우스 커서가 찍히지 않으므로 직접 그려줍니다.
    try:
        mx, my = mouse.position
        cx = (mx - mon["left"]) * px_ratio
        cy = (my - mon["top"]) * px_ratio
        if 0 <= cx < img.width and 0 <= cy < img.height:
            r = max(4, int(9 * px_ratio))
            d = ImageDraw.Draw(img)
            d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(0, 0, 0), width=max(2, r // 3))
            d.ellipse((cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2), outline=(255, 255, 255), width=max(1, r // 4))
            d.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), fill=(255, 40, 40))
    except Exception:
        pass

    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=quality, optimize=False)
    return buf.getvalue()


# ---------------------------------------------------------------------------
# 입력 처리
# ---------------------------------------------------------------------------
BUTTONS = {"left": Button.left, "right": Button.right, "middle": Button.middle}

SPECIAL_KEYS = {
    "esc": Key.esc, "tab": Key.tab, "enter": Key.enter, "backspace": Key.backspace,
    "delete": Key.delete, "space": Key.space, "up": Key.up, "down": Key.down,
    "left": Key.left, "right": Key.right, "home": Key.home, "end": Key.end,
    "page_up": Key.page_up, "page_down": Key.page_down, "ctrl": Key.ctrl,
    "alt": Key.alt, "shift": Key.shift, "cmd": Key.cmd, "win": Key.cmd,
    "caps_lock": Key.caps_lock,
}
for _i in range(1, 13):
    SPECIAL_KEYS[f"f{_i}"] = getattr(Key, f"f{_i}")
for _name in ("media_volume_up", "media_volume_down", "media_volume_mute",
              "media_play_pause", "media_next", "media_previous"):
    if hasattr(Key, _name):
        SPECIAL_KEYS[_name] = getattr(Key, _name)
for _name in ("print_screen", "insert", "menu"):
    if hasattr(Key, _name):
        SPECIAL_KEYS[_name] = getattr(Key, _name)


def to_key(name):
    name = str(name).lower()
    if name in SPECIAL_KEYS:
        return SPECIAL_KEYS[name]
    if len(name) == 1:
        return name
    raise ValueError(f"알 수 없는 키: {name}")


def handle_input(msg, state):
    t = msg.get("t")
    if t == "move":
        mouse.move(int(round(msg.get("dx", 0))), int(round(msg.get("dy", 0))))
    elif t == "moveto":
        mon = state["monitor"]
        x = mon["left"] + min(max(float(msg["x"]), 0.0), 1.0) * (mon["width"] - 1)
        y = mon["top"] + min(max(float(msg["y"]), 0.0), 1.0) * (mon["height"] - 1)
        mouse.position = (int(x), int(y))
    elif t == "click":
        mouse.click(BUTTONS.get(msg.get("b", "left"), Button.left), 2 if msg.get("double") else 1)
    elif t == "down":
        mouse.press(BUTTONS.get(msg.get("b", "left"), Button.left))
    elif t == "up":
        mouse.release(BUTTONS.get(msg.get("b", "left"), Button.left))
    elif t == "scroll":
        mouse.scroll(int(msg.get("dx", 0)), int(msg.get("dy", 0)))
    elif t == "text":
        s = str(msg.get("s", ""))[:2000]
        if s:
            keyboard.type(s)
    elif t == "key":
        k = to_key(msg["k"])
        for _ in range(min(int(msg.get("n", 1)), 200)):
            keyboard.press(k)
            keyboard.release(k)
    elif t == "combo":
        keys = [to_key(k) for k in msg.get("keys", [])][:5]
        for k in keys:
            keyboard.press(k)
        for k in reversed(keys):
            keyboard.release(k)


# ---------------------------------------------------------------------------
# 웹 서버
# ---------------------------------------------------------------------------
async def index(request):
    return web.FileResponse(os.path.join(HERE, "static", "index.html"))


async def ws_handler(request):
    token = request.app["token"]
    given = request.query.get("t", "")
    if not secrets.compare_digest(given.encode(), token.encode()):
        print(f"[거부] 잘못된 접속 코드: {request.remote}")
        return web.Response(status=403, text="Forbidden")

    ws = web.WebSocketResponse(heartbeat=20, max_msg_size=1 << 20)
    await ws.prepare(request)
    print(f"[연결] {request.remote}")

    loop = asyncio.get_running_loop()
    monitors = await loop.run_in_executor(_capture_pool, list_monitors)
    state = {"mon_index": 1, "monitor": dict(monitors[0])}
    await ws.send_json({"t": "info", "monitors": monitors, "platform": sys.platform,
                        "host": socket.gethostname()})

    try:
        async for m in ws:
            if m.type != WSMsgType.TEXT:
                continue
            try:
                msg = json.loads(m.data)
            except ValueError:
                continue
            t = msg.get("t")
            try:
                if t == "frame":
                    idx = int(msg.get("mon", 1))
                    if idx != state["mon_index"] and 1 <= idx <= len(monitors):
                        state["mon_index"] = idx
                        state["monitor"] = dict(monitors[idx - 1])
                    scale = min(max(float(msg.get("scale", 0.5)), 0.1), 1.0)
                    quality = min(max(int(msg.get("q", 60)), 10), 95)
                    data = await loop.run_in_executor(
                        _capture_pool, capture_jpeg, state["mon_index"], scale, quality)
                    await ws.send_bytes(data)
                elif t == "mon":
                    idx = int(msg.get("mon", 1))
                    if 1 <= idx <= len(monitors):
                        state["mon_index"] = idx
                        state["monitor"] = dict(monitors[idx - 1])
                elif t == "ping":
                    await ws.send_json({"t": "pong", "ts": msg.get("ts")})
                else:
                    handle_input(msg, state)
            except Exception as e:  # 개별 명령 실패가 연결을 끊지 않도록
                print(f"[경고] {t} 처리 실패: {e}")
    finally:
        print(f"[해제] {request.remote}")
    return ws


def lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))  # 실제로 패킷을 보내지는 않음
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def print_qr(url):
    try:
        import qrcode
    except ImportError:
        print("  (QR 코드를 보려면: pip install qrcode)")
        return
    qr = qrcode.QRCode(border=1)
    qr.add_data(url)
    qr.make(fit=True)
    qr.print_ascii(invert=True)


def main():
    p = argparse.ArgumentParser(description="안드로이드 폰으로 노트북을 원격 제어하는 서버")
    p.add_argument("--port", type=int, default=8765, help="포트 번호 (기본 8765)")
    p.add_argument("--host", default="0.0.0.0", help="바인드 주소 (기본 0.0.0.0)")
    p.add_argument("--code", default=os.environ.get("REMOTE_CODE"),
                   help="접속 코드를 직접 지정 (기본: 실행할 때마다 무작위 생성)")
    args = p.parse_args()

    token = args.code or secrets.token_urlsafe(9)
    app = web.Application()
    app["token"] = token
    app.router.add_get("/", index)
    app.router.add_get("/ws", ws_handler)
    app.router.add_static("/static/", os.path.join(HERE, "static"))

    url = f"http://{lan_ip()}:{args.port}/#{token}"
    print()
    print("=" * 60)
    print("  폰 리모컨 서버가 실행되었습니다")
    print("=" * 60)
    print("  휴대폰을 노트북과 같은 Wi-Fi에 연결한 뒤,")
    print("  크롬에서 아래 주소를 열거나 QR 코드를 스캔하세요.\n")
    print(f"  주소     : {url}")
    print(f"  접속 코드: {token}\n")
    print_qr(url)
    print("\n  종료하려면 Ctrl+C 를 누르세요.")
    print("=" * 60)

    web.run_app(app, host=args.host, port=args.port, print=None)


if __name__ == "__main__":
    main()
