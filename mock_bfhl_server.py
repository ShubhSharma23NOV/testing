"""
Mock BFHL Server — mimics bfhldevapigw.healthrx.co.in locally
Run this BEFORE starting your Spring Boot app.

Usage:
    python3 mock_bfhl_server.py
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json, datetime

FAKE_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mock_token"
WEBHOOK_PATH = "/hiring/testWebhook/JAVA"

class BFHLHandler(BaseHTTPRequestHandler):

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length)) if length else {}

        # ── Route 1: generateWebhook ──────────────────────────────────
        if self.path == "/hiring/generateWebhook/JAVA":
            print(f"\n[generateWebhook] Received: {body}")

            name   = body.get("name", "")
            reg_no = body.get("regNo", "")
            email  = body.get("email", "")

            if not all([name, reg_no, email]):
                self._send(400, {"message": "Missing required fields"})
                return

            last_two = int(reg_no[-2:]) if reg_no[-2:].isdigit() else 0
            question = "Q1 (odd)" if last_two % 2 != 0 else "Q2 (even)"
            print(f"[generateWebhook] regNo={reg_no}, last2={last_two} → {question}")

            self._send(200, {
                "webhook": f"http://localhost:8888{WEBHOOK_PATH}",
                "accessToken": FAKE_TOKEN
            })

        # ── Route 2: testWebhook (your submission) ────────────────────
        elif self.path == WEBHOOK_PATH:
            auth = self.headers.get("Authorization", "")
            print(f"\n[testWebhook] Auth header : {auth}")
            print(f"[testWebhook] Body        : {body}")

            query = body.get("finalQuery", "")
            if not query:
                self._send(400, {"message": "finalQuery is missing"})
                return

            if not auth.startswith("Bearer "):
                self._send(401, {"message": "Missing or invalid Bearer token"})
                return

            print(f"\n✅ SUBMISSION RECEIVED SUCCESSFULLY")
            print(f"   Query: {query}")
            self._send(200, {
                "message": "SQL query received successfully",
                "receivedAt": datetime.datetime.now().isoformat(),
                "query": query
            })

        else:
            self._send(404, {"statusCode": 404, "message": "Resource not found"})

    def _send(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", len(body))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        pass  # suppress default Apache-style logs (we do our own)

if __name__ == "__main__":
    port = 8888
    print(f"🚀 Mock BFHL server running on http://localhost:{port}")
    print(f"   POST /hiring/generateWebhook/JAVA  → returns webhook + token")
    print(f"   POST /hiring/testWebhook/JAVA       → receives your SQL query")
    print(f"\nWaiting for requests...\n")
    HTTPServer(("0.0.0.0", port), BFHLHandler).serve_forever()