"""Terminal promotion uses the same exact-diff gate as the console, including --yes."""
import http.server
import json
import pathlib
import shutil
import subprocess
import tempfile
import threading
import unittest


class PromoteTest(unittest.TestCase):
    def run_helper(self, verified, status=200):
        posts = []
        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def do_GET(self):
                self.send_response(200); self.end_headers()
                self.wfile.write(json.dumps(dict(enabled=True, verified=verified,
                    files=[dict(path='src/Example.java', status='PROMOTE')], diff='reviewed diff',
                    diffSha256='a' * 64)).encode())
            def do_POST(self):
                posts.append(json.loads(self.rfile.read(int(self.headers['Content-Length']))))
                self.send_response(status); self.end_headers()
                self.wfile.write(json.dumps(dict(reason='server decision', promoted=['src/Example.java'],
                    conflicts=[])).encode())
        server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        try:
            with tempfile.TemporaryDirectory() as temp:
                root = pathlib.Path(temp); scripts = root / 'scripts'; scripts.mkdir()
                source = pathlib.Path(__file__).resolve().parents[1]
                for name in ('lib.sh', 'promote-workspace.sh'): shutil.copy(source / name, scripts / name)
                (root / '.demo').mkdir()
                (root / '.demo/tokens.env').write_text('BLUEPRINT_AGENT_TOKEN=test\nBLUEPRINT_APPROVER_TOKEN=test\n')
                import os
                env = dict(os.environ, MCP_PORT=str(server.server_port))
                result = subprocess.run(['bash', str(scripts / 'promote-workspace.sh'), '--yes'],
                    env=env, capture_output=True, text=True, timeout=5)
                self.assertFalse((root / 'arcade').exists(), 'helper must not copy files itself')
                return result, posts
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_yes_does_not_bypass_acceptance(self):
        result, posts = self.run_helper(False)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], posts)

    def test_verified_diff_is_submitted_to_server(self):
        result, posts = self.run_helper(True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([dict(diffSha256='a' * 64)], posts)

    def test_server_stale_or_busy_rejection_is_not_bypassed(self):
        result, posts = self.run_helper(True, 409)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, len(posts))
