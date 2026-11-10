#!/usr/bin/env python3
"""Fails when an APK carries something that looks like a private credential.

Only public client IDs and anon keys may ship. Looks inside every entry (dex, resources, assets, BuildConfig strings)
for refresh tokens, client secrets, TeraBox `ndus` cookies, private keys and Google OAuth secrets.
Usage: python3 scripts/kai/scan_apk_secrets.py app.apk
"""
import re
import sys
import zipfile

PATTERNS = {
    "private key": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "Google OAuth client secret": re.compile(rb"GOCSPX-[A-Za-z0-9_-]{20,}"),
    "Google refresh token": re.compile(rb"1//0[A-Za-z0-9_-]{30,}"),
    "TeraBox ndus cookie value": re.compile(rb"ndus=[A-Za-z0-9_-]{20,}"),
    "refresh_token value": re.compile(rb"refresh_token\"?\s*[:=]\s*\"[A-Za-z0-9._/-]{20,}\""),
    "client_secret value": re.compile(rb"client_secret\"?\s*[:=]\s*\"[A-Za-z0-9._-]{16,}\""),
    "Supabase service role": re.compile(rb"\"role\"\s*:\s*\"service_role\""),
    "GitHub token": re.compile(rb"gh[pousr]_[A-Za-z0-9]{36,}"),
}


def scan(path: str) -> list[str]:
    found = []
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if info.is_dir() or info.filename.startswith("lib/"):
                continue
            data = archive.read(info.filename)
            for label, pattern in PATTERNS.items():
                if pattern.search(data):
                    found.append(f"{label} in {info.filename}")
    return found


if __name__ == "__main__":
    problems = scan(sys.argv[1])
    if problems:
        print("Possible secrets in the APK:\n" + "\n".join(problems), file=sys.stderr)
        sys.exit(1)
    print("No private credentials found.")
