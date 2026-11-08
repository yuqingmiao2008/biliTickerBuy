"""biliTickerBuy standalone local server (runs inside the APK via Chaquopy).

Everything project-specific is imported lazily inside `start()` so that
BTB_HOME is set before util/__init__.py initialises its paths.
"""

import json
import os
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

HOME = ""
PORT = 7861

_logs = []
_lock = threading.Lock()
_state = {
    "running": False,
    "stage": "",
    "username": "",
    "order_id": None,
    "payment_url": None,
    "finished": None,
}
_stop_flag = {"stop": False}

_imports = {}


def _log(message):
    line = time.strftime("%H:%M:%S ") + str(message)
    with _lock:
        _logs.append(line)
        if len(_logs) > 3000:
            del _logs[:1000]


def _set(**kwargs):
    with _lock:
        _state.update(kwargs)


def _cookies_path():
    return os.path.join(HOME, "cookies.json")


def _cookie_manager():
    CookieManager = _imports["CookieManager"]
    return CookieManager(_cookies_path())


def _current_cookies():
    try:
        manager = _cookie_manager()
        return manager.get_cookies(force=True)
    except Exception:
        return None


def _accounts():
    try:
        return _cookie_manager().get_accounts()
    except Exception:
        return []


def _login_state():
    get_login_state = _imports["get_login_state"]
    try:
        state = get_login_state(cookies_path=_cookies_path())
        _set(username=state.get("username") or "")
        return state
    except Exception as exc:
        return {"ok": False, "logged_in": False, "username": "", "error": repr(exc)}


def _start_qr():
    import io
    import qrcode
    import qrcode.image.svg

    start_qr_login = _imports["start_qr_login"]
    result = start_qr_login(qr_image_path=False)
    if not result.get("ok"):
        return {"ok": False, "error": result.get("error", "二维码生成失败")}
    url = result["login_url"]
    image = qrcode.make(url, image_factory=qrcode.image.svg.SvgPathImage)
    buffer = io.BytesIO()
    image.save(buffer)
    return {
        "ok": True,
        "key": result["qrcode_key"],
        "svg": buffer.getvalue().decode("utf-8"),
    }


def _poll_qr(key):
    poll_qr_login = _imports["poll_qr_login"]
    result = poll_qr_login(key, cookies_path=_cookies_path(), timeout_seconds=6)
    if result.get("ok") and result.get("status") == "confirmed":
        cookies = result.get("cookies") or []
        try:
            manager = _cookie_manager()
            manager.add_account(cookies)
        except Exception as exc:
            _log(f"保存账号失败：{exc}")
        _set(username=result.get("username") or "")
    return result


def _run_one(name, payload, interval, time_start):
    BuyConfig = _imports["BuyConfig"]
    buy_stream = _imports["buy_stream"]

    config = BuyConfig(
        tickets_info=json.dumps(payload, ensure_ascii=False),
        time_start=time_start or "",
        interval=int(interval),
        show_qrcode=False,
        auto_open_payment_url=False,
    )
    _log(f"[{name}] 任务启动，间隔 {interval}ms，开抢时间 {time_start or '立即'}")
    try:
        for event in buy_stream(config):
            message = getattr(event, "message", None)
            if message:
                _log(f"[{name}] {message}")
            state = getattr(event, "state", None)
            if state is not None:
                updates = {}
                stage = getattr(state, "stage", "")
                if stage:
                    updates["stage"] = stage
                order_id = getattr(state, "order_id", None)
                if order_id:
                    updates["order_id"] = order_id
                payment_url = getattr(state, "payment_qr_url", None)
                if payment_url:
                    updates["payment_url"] = payment_url
                if updates:
                    _set(**updates)
            if _stop_flag["stop"]:
                _log(f"[{name}] 已请求停止")
                break
        _set(finished=name)
        _log(f"[{name}] 任务结束")
    except Exception as exc:
        _log(f"[{name}] 异常：{exc}")
        _log(traceback.format_exc(limit=3))


def _start_tasks(body):
    if _state["running"]:
        return {"ok": False, "error": "已有任务在运行"}
    raw_config = body.get("config_text") or ""
    try:
        payload = json.loads(raw_config)
    except Exception as exc:
        return {"ok": False, "error": f"配置 JSON 解析失败：{exc}"}
    if not isinstance(payload, dict):
        return {"ok": False, "error": "配置必须是 JSON 对象"}

    interval = int(body.get("interval") or 1000)
    interval = max(10, min(5000, interval))
    time_start = (body.get("time_start") or "").strip()
    multi = bool(body.get("multi_account"))

    tasks = []
    accounts = _accounts() if multi else []
    if accounts:
        for account in accounts:
            item = dict(payload)
            item["cookies"] = account.cookies
            detail = str(item.get("detail") or "").strip()
            item["detail"] = f"{detail}-{account.name}" if detail else account.name
            tasks.append((account.name, item))
    else:
        cookies = _current_cookies()
        if cookies:
            payload["cookies"] = cookies
        tasks.append(("当前账号", payload))

    _stop_flag["stop"] = False
    _set(running=True, finished=None, stage="启动中", order_id=None, payment_url=None)
    _log(f"准备启动 {len(tasks)} 个任务")

    def runner():
        try:
            for name, item in tasks:
                if _stop_flag["stop"]:
                    break
                _run_one(name, item, interval, time_start)
        finally:
            _set(running=False)

    threading.Thread(target=runner, daemon=True).start()
    return {"ok": True, "tasks": [name for name, _ in tasks]}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def _send(self, code, body, content_type="application/json; charset=utf-8"):
        data = body.encode("utf-8") if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _json(self, payload, code=200):
        self._send(code, json.dumps(payload, ensure_ascii=False))

    def _body(self):
        length = int(self.headers.get("Content-Length") or 0)
        if not length:
            return {}
        try:
            return json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            return {}

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path in ("/", "/index.html"):
            self._send(200, INDEX_HTML, "text/html; charset=utf-8")
            return
        if parsed.path == "/api/status":
            login = _login_state()
            with _lock:
                state = dict(_state)
            self._json({
                "ok": True,
                "login": login,
                "accounts": [{"name": a.name, "uid": a.uid} for a in _accounts()],
                **{k: state[k] for k in ("running", "stage", "order_id", "payment_url", "finished")},
            })
            return
        if parsed.path == "/api/logs":
            query = parse_qs(parsed.query)
            after = int((query.get("after") or ["0"])[0])
            with _lock:
                lines = _logs[after:]
                total = len(_logs)
            self._json({"ok": True, "lines": lines, "next": total})
            return
        self._json({"ok": False, "error": "not found"}, 404)

    def do_POST(self):
        parsed = urlparse(self.path)
        body = self._body()
        try:
            if parsed.path == "/api/login/qr":
                self._json(_start_qr())
            elif parsed.path == "/api/login/poll":
                self._json(_poll_qr(body.get("key") or ""))
            elif parsed.path == "/api/start":
                self._json(_start_tasks(body))
            elif parsed.path == "/api/stop":
                _stop_flag["stop"] = True
                _log("收到停止请求")
                self._json({"ok": True})
            elif parsed.path == "/api/cookies":
                manager = _cookie_manager()
                cookies = body.get("cookies") or []
                if not cookies:
                    self._json({"ok": False, "error": "cookies 为空"})
                else:
                    manager.db.insert("cookie", cookies)
                    try:
                        manager.add_account(cookies)
                    except Exception as exc:
                        _log(f"账号信息获取失败：{exc}")
                    self._json({"ok": True})
            else:
                self._json({"ok": False, "error": "not found"}, 404)
        except Exception as exc:
            _log(traceback.format_exc(limit=3))
            self._json({"ok": False, "error": repr(exc)}, 500)


def start(home, port=7861):
    global HOME, PORT
    HOME = home
    PORT = int(port)
    os.environ["BTB_HOME"] = home
    os.environ["BTB_SKIP_INITIAL_TIME_SYNC"] = "1"
    os.environ["BTB_LOG_DIR"] = os.path.join(home, "logs")
    os.makedirs(HOME, exist_ok=True)

    from interface.auth import get_login_state, start_qr_login, poll_qr_login
    from util.request.CookieManager import CookieManager
    from app_cmd.config.BuyConfig import BuyConfig
    from task.buy import buy_stream

    _imports.update({
        "get_login_state": get_login_state,
        "start_qr_login": start_qr_login,
        "poll_qr_login": poll_qr_login,
        "CookieManager": CookieManager,
        "BuyConfig": BuyConfig,
        "buy_stream": buy_stream,
    })

    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    _log("本地服务已启动")
    return PORT


INDEX_HTML = """<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>BTB 手机版</title>
<style>
:root { color-scheme: dark; }
* { box-sizing: border-box; }
body { margin: 0; font-family: system-ui, -apple-system, sans-serif; background: #111; color: #eee; }
header { padding: 14px 16px; background: #1b1b1b; font-weight: 600; border-bottom: 1px solid #2a2a2a; }
main { padding: 14px; display: grid; gap: 14px; }
.card { background: #1b1b1b; border: 1px solid #2a2a2a; border-radius: 12px; padding: 14px; }
.card h2 { margin: 0 0 10px; font-size: 15px; color: #ff6699; }
.row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
button { background: #ff6699; color: #fff; border: 0; border-radius: 10px; padding: 10px 16px; font-size: 14px; }
button.ghost { background: #2a2a2a; }
input, textarea { width: 100%; background: #121212; color: #eee; border: 1px solid #333; border-radius: 8px; padding: 8px; font-size: 13px; }
textarea { min-height: 140px; font-family: ui-monospace, monospace; }
#qr { background: #fff; width: 220px; height: 220px; border-radius: 10px; display: none; }
.muted { color: #999; font-size: 12px; }
#logs { font-family: ui-monospace, monospace; font-size: 12px; white-space: pre-wrap; max-height: 45vh; overflow: auto; background: #0b0b0b; border-radius: 8px; padding: 8px; }
.ok { color: #4ade80; } .err { color: #f87171; }
</style>
</head>
<body>
<header>biliTickerBuy 独立版</header>
<main>
  <section class="card">
    <h2>账号</h2>
    <div id="loginState" class="muted">检查中…</div>
    <div class="row" style="margin-top:10px">
      <button id="qrBtn">生成登录二维码</button>
      <button id="refreshBtn" class="ghost">刷新账号</button>
    </div>
    <div id="qrWrap" style="margin-top:12px; display:none">
      <div id="qr"></div>
      <div class="muted" id="qrHint">用 B 站 App 扫码</div>
    </div>
    <div id="accounts" class="muted" style="margin-top:8px"></div>
  </section>

  <section class="card">
    <h2>配置</h2>
    <div class="row">
      <label class="muted">开抢时间</label>
      <input id="timeStart" placeholder="2026-11-11T20:00:00，留空立即抢">
    </div>
    <div class="row" style="margin-top:8px">
      <label class="muted">间隔(ms)</label>
      <input id="interval" type="number" min="10" max="5000" value="500">
      <label class="muted"><input id="multi" type="checkbox" style="width:auto"> 多账号并发</label>
    </div>
    <div class="muted" style="margin:8px 0 6px">抢票配置 JSON（生成配置后粘贴/导入的内容）</div>
    <textarea id="configText" placeholder='{"screen_id": ..., "sku_id": ..., "buyer_info": [...], ...}'></textarea>
    <div class="row" style="margin-top:8px">
      <input id="file" type="file" accept=".json,application/json" style="border:0;padding:0">
    </div>
  </section>

  <section class="card">
    <h2>运行</h2>
    <div id="runState" class="muted">空闲</div>
    <div class="row" style="margin-top:10px">
      <button id="startBtn">开始抢票</button>
      <button id="stopBtn" class="ghost">停止</button>
    </div>
    <div id="payment" style="margin-top:10px"></div>
  </section>

  <section class="card">
    <h2>日志</h2>
    <div id="logs"></div>
  </section>
</main>
<script>
let qrKey = null, logOffset = 0;
const $ = (id) => document.getElementById(id);

async function api(path, body) {
  const opt = body ? {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)} : {};
  const res = await fetch(path, opt);
  return res.json();
}

async function refreshStatus() {
  try {
    const s = await api('/api/status');
    const login = s.login || {};
    $('loginState').innerHTML = login.logged_in
      ? '<span class="ok">已登录：' + (login.username || '') + '</span>'
      : '<span class="err">未登录</span>';
    $('accounts').textContent = (s.accounts || []).map(a => a.name).join(' / ') || '暂无已保存账号';
    $('runState').textContent = s.running
      ? ('运行中 · ' + (s.stage || '') + (s.order_id ? (' · 订单 ' + s.order_id) : ''))
      : (s.finished ? ('已结束：' + s.finished) : '空闲');
    if (s.payment_url) {
      $('payment').innerHTML = '<a style="color:#4ade80" href="' + s.payment_url + '" target="_blank">打开支付链接</a>';
    }
  } catch (e) {}
}

async function refreshLogs() {
  try {
    const r = await api('/api/logs?after=' + logOffset);
    if (r.lines && r.lines.length) {
      logOffset = r.next;
      const box = $('logs');
      box.textContent += r.lines.join('\\n') + '\\n';
      box.scrollTop = box.scrollHeight;
    }
  } catch (e) {}
}

$('qrBtn').onclick = async () => {
  $('qrHint').textContent = '生成中…';
  const r = await api('/api/login/qr', {});
  if (!r.ok) { $('qrHint').textContent = r.error || '失败'; return; }
  qrKey = r.key;
  $('qrWrap').style.display = 'block';
  $('qr').style.display = 'block';
  $('qr').innerHTML = r.svg;
  $('qrHint').textContent = '用 B 站 App 扫码，成功后自动保存';
  pollQr();
};

async function pollQr() {
  if (!qrKey) return;
  const r = await api('/api/login/poll', {key: qrKey});
  if (r.ok && r.status === 'confirmed') {
    $('qrHint').innerHTML = '<span class="ok">登录成功</span>';
    qrKey = null;
    refreshStatus();
    return;
  }
  if (r.status === 'timeout') { setTimeout(pollQr, 1200); return; }
  if (r.ok === false && r.status === 'failed') { $('qrHint').textContent = r.message || '登录失败'; qrKey = null; return; }
  setTimeout(pollQr, 1200);
}

$('file').onchange = (e) => {
  const f = e.target.files[0];
  if (!f) return;
  const reader = new FileReader();
  reader.onload = () => { $('configText').value = reader.result; };
  reader.readAsText(f);
};

$('startBtn').onclick = async () => {
  const text = $('configText').value.trim();
  if (!text) { alert('先填入抢票配置 JSON'); return; }
  const r = await api('/api/start', {
    config_text: text,
    interval: parseInt($('interval').value || '500', 10),
    time_start: $('timeStart').value.trim(),
    multi_account: $('multi').checked
  });
  if (!r.ok) alert(r.error || '启动失败');
  refreshStatus();
};

$('stopBtn').onclick = () => api('/api/stop', {});
$('refreshBtn').onclick = refreshStatus;

setInterval(refreshStatus, 2000);
setInterval(refreshLogs, 1000);
refreshStatus(); refreshLogs();
</script>
</body>
</html>
"""