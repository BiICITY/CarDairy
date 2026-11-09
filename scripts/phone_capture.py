"""Captures the real phone page for the demo video using sample drawings, not real photos.

Serves companion.html with a small script added that loads sample pictures, then screenshots it
with headless Edge at phone size. Usage: phone_capture.py <outdir>
"""
import json
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PAGE = Path(__file__).parent.parent / "app/src/main/assets/companion.html"
EDGE = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
NOW = int(time.time() * 1000)
HOUR = 3600000
OUTING = {"id": "1", "start": NOW - 6 * HOUR, "end": NOW - HOUR, "label": "바다"}

SAMPLE = """
<script>
const scenes = [['#7cc6e8','#f2d9a0'],['#6fb7dd','#e9cf94'],['#f7b267','#3f7fa6'],['#8fd3e8','#f5e0b0'],
  ['#5ba7d1','#efd59b'],['#9ad1c4','#d8c08a'],['#f4a26b','#2f6f96']];
function draw(i) {
  return new Promise(res => {
    const c = document.createElement('canvas'); c.width = 600; c.height = 600;
    const g = c.getContext('2d'), [sky, sand] = scenes[i % scenes.length];
    g.fillStyle = sky; g.fillRect(0, 0, 600, 600);
    g.fillStyle = '#fff6d6'; g.beginPath(); g.arc(120 + (i % 8) * 50, 120, 50, 0, 7); g.fill();
    g.fillStyle = '#3f8fbf'; g.fillRect(0, 300, 600, 110);
    g.fillStyle = sand; g.fillRect(0, 410, 600, 190);
    g.fillStyle = '#d9573f'; g.beginPath(); g.arc(200 + (i % 8) * 30, 470, 30, 0, 7); g.fill();
    g.fillRect(170 + (i % 8) * 30, 500, 60, 70);
    c.toBlob(b => res(new File([b], 's' + i + '.jpg', {type:'image/jpeg', lastModified: START + i * 170000})), 'image/jpeg', .9);
  });
}
const START = __START__;
window.addEventListener('load', async () => {
  await new Promise(r => setTimeout(r, 300));
  const stage = new URLSearchParams(location.search).get('stage');
  if (stage === 'pick') return;
  const files = []; for (let i = 0; i < 100; i++) files.push(await draw(i));
  const dt = new DataTransfer(); files.forEach(f => dt.items.add(f));
  const input = document.getElementById('files'); input.files = dt.files;
  input.dispatchEvent(new Event('change'));
  await new Promise(r => setTimeout(r, 2500));
  if (stage === 'done') {
    document.getElementById('stepReview').classList.add('hidden');
    document.getElementById('bar').classList.add('hidden');
    document.getElementById('stepDone').classList.remove('hidden');
  }
});
</script>
""".replace("__START__", str(OUTING["start"] + 600000))


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send(self, body):
        data = body.encode("utf8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8" if body.startswith("<!") else "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.startswith("/api/outings"):
            self.send(json.dumps([OUTING]))
        else:
            self.send(PAGE.read_text(encoding="utf8").replace("</body>", SAMPLE + "</body>"))

    def do_POST(self):
        meta = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or "[]")
        step = max(1, len(meta) // 7)
        self.send(json.dumps({"chosen": [m["id"] for m in meta[::step]][:7], "matched": len(meta), "excludedForSafety": 0}))


def main():
    out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(("127.0.0.1", 8798), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    for stage in ("pick", "review", "done"):
        shot = out / f"phone_{stage}.png"
        result = subprocess.run([
            EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--window-size=500,900",
            f"--user-data-dir={str(out / 'edge').replace('/', chr(92))}", "--virtual-time-budget=15000", f"--screenshot={str(shot).replace('/', chr(92))}",
            f"http://127.0.0.1:8798/?t=x&stage={stage}",
        ], timeout=90, check=False, capture_output=True)
        print(stage, shot.exists() and shot.stat().st_size, result.returncode, result.stderr[-200:])


if __name__ == "__main__":
    main()
