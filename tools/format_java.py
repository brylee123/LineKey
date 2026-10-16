# SPDX-License-Identifier: Apache-2.0
"""Apply or verify the project's pinned 4-space Java style and structural rules."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request

VERSION = "1.28.0"
SHA256 = "32342e7c1b4600f80df3471da46aee8012d3e1445d5ea1be1fb71289b07cc735"
URL = ("https://github.com/google/google-java-format/releases/download/"
       f"v{VERSION}/google-java-format-{VERSION}-all-deps.jar")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check without modifying files")
    parser.add_argument("--java", help="JDK java executable; otherwise use JAVA_HOME or PATH")
    parser.add_argument("--formatter-jar", type=Path, help="Use an existing checksum-verified jar")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java_home = os.environ.get("JAVA_HOME")
    java = args.java or (str(Path(java_home) / "bin" / "java") if java_home else shutil.which("java"))
    if not java:
        parser.error("A JDK is required. Set JAVA_HOME or pass --java.")
    jar = args.formatter_jar or root / ".gradle" / "style" / f"google-java-format-{VERSION}.jar"
    if not jar.exists():
        if args.formatter_jar:
            parser.error(f"Formatter jar does not exist: {jar}")
        jar.parent.mkdir(parents=True, exist_ok=True)
        payload = urllib.request.urlopen(URL, timeout=60).read()
        if hashlib.sha256(payload).hexdigest() != SHA256:
            raise RuntimeError("Downloaded formatter checksum does not match the pinned version")
        jar.write_bytes(payload)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != SHA256:
        raise RuntimeError("Formatter checksum does not match the pinned version")
    files = sorted(str(file) for folder in ["app/src", "core/src", "tools/java"]
                   for file in (root / folder).rglob("*.java"))
    for name in files:
        file = Path(name)
        payload = file.read_bytes()
        if b"\r\n" in payload:
            if args.check:
                raise RuntimeError(f"Use LF line endings: {file}; run the formatter")
            file.write_bytes(payload.replace(b"\r\n", b"\n"))
    mode = ["--dry-run", "--set-exit-if-changed"] if args.check else ["--replace"]
    subprocess.run([java, "-jar", str(jar), "--aosp", *mode, *files], check=True)
    subprocess.run([java, "--source", "17", str(root / "tools/java/CheckJavaStyle.java"), str(root)], check=True)
    print(f"Java formatting {'verified' if args.check else 'applied'}: {len(files)} files")


if __name__ == "__main__":
    main()
