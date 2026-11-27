#!/usr/bin/env python3
"""The HMG-API pin is in hmgapi-sdk.json (the SDK) and in the mod workflow's @ref in ci.yml
and in release.yml. They must name the same HMG-API:
{"ref": sha} with @sha, {"version": v} with @v<v>. Run by CI."""
import json, os, re, sys
pin = json.load(open('hmgapi-sdk.json'))
want = pin['ref'] if 'ref' in pin else 'v' + pin['version']
bad = 0
for wf in ('.github/workflows/ci.yml', '.github/workflows/release.yml'):
    if not os.path.exists(wf): continue
    refs = re.findall(r'uses:\s*Open-HMG/HMG-API/\.github/workflows/mod\.yml@(\S+)', open(wf).read())
    if refs != [want]:
        print(f'{wf}: mod.yml at {refs}, but hmgapi-sdk.json pins {want}: change them together')
        bad = 1
print('ok    HMG-API pin', want) if not bad else None
sys.exit(bad)
