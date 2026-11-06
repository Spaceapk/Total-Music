#!/usr/bin/env python3
"""Prepare a release, or explicitly increase the code of a same-version revision."""
import argparse
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
path = root / 'total-music-release.properties'
text = path.read_text()
properties = dict(line.split('=', 1) for line in text.splitlines()
                  if line and not line.startswith('#'))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('version')
parser.add_argument('--revision-code', type=int, help='Same-version rebuild only: code greater than all previous delivered APKs')
args = parser.parse_args()
requested = args.version
if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', requested):
    raise SystemExit('Use a stable version such as 1.0.3')
if args.revision_code is not None:
    if requested != properties['versionName']:
        raise SystemExit('--revision-code requires the existing version name')
    next_code = args.revision_code
else:
    if tuple(map(int, requested.split('.'))) <= tuple(map(int, properties['versionName'].split('.'))):
        raise SystemExit('The new version must be greater than the current version')
    next_code = int(properties['versionCode']) + 1
if next_code <= int(properties['versionCode']):
    raise SystemExit('The new versionCode must be greater than the current code')
text = text.replace('versionName=' + properties['versionName'], 'versionName=' + requested)
text = text.replace('versionCode=' + properties['versionCode'],
                    'versionCode=' + str(next_code))
temporary = path.with_suffix('.tmp')
temporary.write_text(text)
temporary.replace(path)
print(f"Prepared {requested}, versionCode {next_code}")
