#!/usr/bin/env python3
"""Deploy cloud/worker.js to the Cloudflare Worker via the REST API.

No wrangler required. Reads live script settings first and echoes the bindings
back on upload, so a deploy can never silently drop a binding.

Usage:
  python3 cloud/deploy.py --token-file /path/to/cf-token
  CLOUDFLARE_API_TOKEN=... python3 cloud/deploy.py

Rollback: `git diff` the worker.js you deployed, fix, redeploy — or re-upload
the archived pre-change bundle if you kept one.
"""
import argparse
import json
import os
import sys
import urllib.request
import urllib.error

SCRIPT_NAME = "wifi-data-guard"
DEFAULT_ACCOUNT = "2aeea2fa6df1442b5d54388f8e233a97"   # Gigaspaceturnip@gmail.com account
API = "https://api.cloudflare.com/client/v4"
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36"


def api(method, path, token, data=None, headers=None):
    req = urllib.request.Request(API + path, data=data, method=method,
                                 headers={"Authorization": f"Bearer {token}",
                                          "User-Agent": UA, **(headers or {})})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def get_settings(account, token):
    res = api("GET", f"/accounts/{account}/workers/scripts/{SCRIPT_NAME}/settings", token)
    if not res.get("success"):
        sys.exit(f"FATAL: could not read script settings: {res.get('errors')}")
    return res["result"]


def build_metadata(settings):
    """Echo live bindings/config into upload metadata (binding format for PUT)."""
    bindings = []
    for b in settings.get("bindings", []):
        t = b["type"]
        if t == "plain_text":
            bindings.append({"type": "plain_text", "name": b["name"], "text": b["text"]})
        elif t == "d1":
            bindings.append({"type": "d1", "name": b["name"], "id": b.get("id") or b["database_id"]})
        elif t == "kv_namespace":
            bindings.append({"type": "kv_namespace", "name": b["name"],
                             "namespace_id": b["namespace_id"]})
        else:
            sys.exit(f"FATAL: unhandled binding type {t!r} — extend build_metadata() first")
    return {
        "main_module": "worker.js",
        "compatibility_date": settings.get("compatibility_date", "2024-09-23"),
        "bindings": bindings,
        "observability": {"enabled": True, "head_sampling_rate": 1},
    }


def upload(account, token, metadata, module_bytes):
    boundary = "----dataguarddeploy7f27c5768dcd4ca6"
    parts = []
    parts.append(
        f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
        f'Content-Type: application/json\r\n\r\n{json.dumps(metadata)}\r\n'.encode())
    parts.append(
        f'--{boundary}\r\nContent-Disposition: form-data; name="worker.js"; '
        f'filename="worker.js"\r\n'
        f'Content-Type: application/javascript+module\r\n\r\n'.encode()
        + module_bytes + b"\r\n")
    parts.append(f'--{boundary}--\r\n'.encode())
    body = b"".join(parts)
    res = api("PUT", f"/accounts/{account}/workers/scripts/{SCRIPT_NAME}", token,
              data=body,
              headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    if not res.get("success"):
        sys.exit(f"FATAL: upload failed: {json.dumps(res.get('errors'), indent=2)}")
    return res["result"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--token-file", help="file containing the Cloudflare API token")
    ap.add_argument("--account", default=DEFAULT_ACCOUNT)
    ap.add_argument("--worker-file", default=os.path.join(os.path.dirname(__file__), "worker.js"))
    args = ap.parse_args()

    token = (open(args.token_file).read().strip() if args.token_file
             else os.environ.get("CLOUDFLARE_API_TOKEN", "")).strip()
    if not token:
        sys.exit("FATAL: no token — pass --token-file or set CLOUDFLARE_API_TOKEN")

    module = open(args.worker_file, "rb").read()
    settings = get_settings(args.account, token)
    meta = build_metadata(settings)
    print(f"bindings echoed: {[b['name'] for b in meta['bindings']]}, "
          f"compat {meta['compatibility_date']}, module {len(module)} B")
    result = upload(args.account, token, meta, module)
    print("DEPLOYED:", json.dumps({k: result.get(k) for k in
                                   ("id", "etag", "modified_on", "startup_time_ms") if k in result}))


if __name__ == "__main__":
    main()
