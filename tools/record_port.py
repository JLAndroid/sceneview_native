"""Record provenance and per-source changes. --stamp marks modified Kotlin files for Apache-2.0.

Run after intentional edits. This writes manifests, not Gradle/build state.
"""
from pathlib import Path
import argparse
import hashlib
import json
from publication_config import VERSION

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = ROOT/'upstream/4.35.0'
MARKER = '// Native View/XML port of SceneView 4.35.0; modified 2026-09-16. See PORTING.md.\n'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def record(stamp=False):
    mappings = {}
    for p in (ARCHIVE/'sceneview').rglob('*.kt'):
        mappings[ROOT/'sceneview-native/src/main/java'/p.relative_to(ARCHIVE/'sceneview')] = p
    for source_set in ('commonMain', 'androidMain'):
        base = ARCHIVE/'sceneview-core-android'/source_set
        for p in base.rglob('*.kt'):
            mappings[ROOT/'sceneview-core/src/main/java'/p.relative_to(base)] = p
    actual = {p for module in ('sceneview-core', 'sceneview-native', 'sample')
              for p in (ROOT/module/'src/main').rglob('*.kt')}
    files = []
    for target in sorted(actual | set(mappings)):
        original = mappings.get(target)
        status = 'omitted_frontend' if not target.exists() else 'new'
        if target.exists() and original:
            clean = target.read_text('utf-8').removeprefix(MARKER)
            status = 'unchanged' if clean == original.read_text('utf-8') else 'modified'
        if stamp and status in ('modified', 'new'):
            text = target.read_text('utf-8')
            if not text.startswith(MARKER): target.write_text(MARKER+text, 'utf-8')
        files.append({
            'file': target.relative_to(ROOT).as_posix(), 'status': status,
            'upstream': original.relative_to(ROOT).as_posix() if original else None,
            'sha256': sha(target) if target.exists() else None,
            'upstream_sha256': sha(original) if original else None,
        })
    manifest = {'upstream_version':'4.35.0', 'port_version':VERSION,
                'date':'2026-09-17', 'scope':'Android 3D + Android/core sources; not a full multi-platform repository clone',
                'counts':{s:sum(f['status']==s for f in files) for s in ('unchanged','modified','new','omitted_frontend')},
                'files':files}
    (ROOT/'PORT-MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n','utf-8')
    repository_files = []
    base = ARCHIVE/'repository'
    for p in sorted(base.rglob('*')):
        if p.is_file():
            rel = p.relative_to(base).as_posix()
            url = ('https://services.gradle.org/distributions/'+p.name if p.name.endswith('.zip.sha256')
                   else 'https://cdn.jsdelivr.net/gh/sceneview/sceneview@v4.35.0/'+rel)
            repository_files.append({'file':p.relative_to(ROOT).as_posix(),'sha256':sha(p),'url':url})
    for name in ('gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar'):
        repository_files.append({'file':name,'sha256':sha(ROOT/name),
                                 'origin':'Local copy of standard Gradle wrapper bootstrap from existing workspace; not executed'})
    (ROOT/'upstream/repository-files.json').write_text(json.dumps(repository_files,indent=2)+'\n','utf-8')
    print(json.dumps(manifest['counts']))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--stamp',action='store_true')
    record(parser.parse_args().stamp)
