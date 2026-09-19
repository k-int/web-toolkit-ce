#!/usr/bin/env python3
"""Fail closed on absent/failed/skipped required suites; retain compact local evidence."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

status, cleanup, require_unit = int(sys.argv[1]), sys.argv[2], sys.argv[3] == 'true'
root = Path(__file__).resolve().parent.parent
suites = []
problems = []
for task in ['integrationTest'] + (['test'] if require_unit else []):
    files = list((root / 'build/test-results' / task).glob('TEST-*.xml'))
    if not files:
        problems.append(f'{task}: no JUnit reports')
    for path in files:
        suite = ET.parse(path).getroot()
        row = {'task': task, 'name': suite.get('name')}
        row.update({key: int(suite.get(key, 0)) for key in ['tests', 'failures', 'errors', 'skipped']})
        suites.append(row)
        if row['tests'] == 0 or any(row[k] for k in ['failures', 'errors', 'skipped']):
            problems.append(f"{row['name']}: absent, failed or skipped cases")
required = {
    'com.k_int.web.toolkit.ToolkitLifecycleSpec': 15,
    'com.k_int.web.toolkit.files.StorageMigrationSpec': 11,
    'com.k_int.web.toolkit.custprops.CustomPropertiesSpec': 15,
}
for name, minimum in required.items():
    if not any(s['name'] == name and s['tests'] >= minimum for s in suites):
        problems.append(f'{name}: fewer than {minimum} required cases')
if require_unit and sum(s['tests'] for s in suites if s['task'] == 'test') < 125:
    problems.append('test: fewer than 125 baseline unit cases')
if status:
    problems.append(f'Gradle/fixture exit status {status}')
if cleanup != 'passed':
    problems.append('disposable fixture cleanup failed')
def git(*args):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()
inputs = sorted(p for folder in ['src', 'grails-app', 'scripts', 'gradle']
                for p in (root / folder).rglob('*') if p.is_file())
inputs += [root / 'build.gradle', root / 'gradle.properties']
source_hash = hashlib.sha256()
for path in inputs:
    source_hash.update(str(path.relative_to(root)).encode() + b'\0' + path.read_bytes())
artifacts = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
             for p in (root / 'build/libs').glob('*.jar')}
report = {'source': git('rev-parse', 'HEAD'), 'dirty': bool(git('status', '--porcelain')),
          'input_sha256': source_hash.hexdigest(), 'artifact_sha256': artifacts,
          'fixture_images': sys.argv[4:6], 'command': sys.argv[6:],
          'suites': suites, 'cleanup': cleanup, 'problems': problems,
          'log': 'build/reports/toolkit-qualification.log', 'passed': not problems}
report_path = root / 'build/reports/toolkit-qualification.json'
report_path.parent.mkdir(parents=True, exist_ok=True)
report_path.write_text(json.dumps(report, indent=2) + '\n')
totals = {key: sum(s[key] for s in suites) for key in ['tests', 'failures', 'errors', 'skipped']}
print(json.dumps({'passed': report['passed'], **totals, 'cleanup': cleanup,
                  'problems': problems, 'report': str(report_path)}))
sys.exit(1 if problems else 0)
