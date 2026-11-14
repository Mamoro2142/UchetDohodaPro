"""Package only an APK that has passed signing, upgrade and regression checks."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

source = Path("app/build/outputs/apk/release/app-release.apk")
build_tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
badging = subprocess.check_output([str(build_tools / "aapt"), "dump", "badging", str(source)], text=True).splitlines()[0]
package = re.search(r"name='([^']+)'", badging).group(1)
code = int(re.search(r"versionCode='([0-9]+)'", badging).group(1))
version = re.search(r"versionName='([^']+)'", badging).group(1)
assert package == "com.example.uchetdohodapro"
assert re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version)
assert code > 10

unit_tests = 0
for report in Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"):
    suite = ET.parse(report).getroot()
    assert int(suite.attrib["failures"]) == 0 and int(suite.attrib["errors"]) == 0
    assert int(suite.attrib.get("skipped", "0")) == 0
    unit_tests += int(suite.attrib["tests"])
assert unit_tests > 0
for report in ("upgrade-instrumentation.txt", "reinstall-instrumentation.txt"):
    assert "OK (1 test)" in (Path("regression-evidence") / report).read_text()
lint = {}
for variant in ("debug", "release"):
    report = ET.parse(f"app/build/reports/lint-results-{variant}.xml").getroot()
    assert not any(issue.get("severity") in ("Error", "Fatal") for issue in report)
    lint[variant] = {"errors": 0, "warnings": sum(issue.get("severity") == "Warning" for issue in report)}

certificate = subprocess.check_output([str(build_tools / "apksigner"), "verify", "--print-certs", str(source)], text=True)
digest = re.search(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", certificate).group(1)
destination = Path("dist")
destination.mkdir(exist_ok=True)
name = f"UchetDohodaPro-{version}.apk"
shutil.copyfile(source, destination / name)
checksum = hashlib.sha256((destination / name).read_bytes()).hexdigest()
(destination / "SHA256SUMS.txt").write_text(f"{checksum}  {name}\n")
metadata = {
    "applicationId": package, "versionName": version, "versionCode": code,
    "commit": os.environ["GITHUB_SHA"],
    "workflow": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    "apk": name, "sha256": checksum, "certificateSha256": digest,
    "unitTests": unit_tests, "androidScenarios": 2, "lint": lint,
    "upgradeFrom": "1.0.9", "network": "offline", "screen": "480x800 at 240 dpi",
}
(destination / "BUILD_INFO.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
with open(os.environ["GITHUB_OUTPUT"], "a") as outputs:
    outputs.write(f"artifact_name=UchetDohodaPro-{version}\n")
print(json.dumps(metadata, ensure_ascii=False, indent=2))
