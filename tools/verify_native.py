"""Offline structural checks only. Does NOT compile Kotlin, resolve Gradle or exercise Android/GPU.

Usage: python tools/verify_native.py [--report]
No third-party Python packages are needed.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import struct
import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ('sceneview-core', 'sceneview-native', 'sample')


def code_only(text):
    """Mask comments and literal text while retaining line numbers. Not a Kotlin parser."""
    result = list(text)
    i = 0
    def mask(start, end):
        for k in range(start, end):
            if result[k] != '\n':
                result[k] = ' '
    def string_end(start, quote):
        at = start + len(quote)
        while at < len(text):
            if text.startswith(quote, at):
                if quote == '"""':
                    while at < len(text) and text[at] == '"':
                        at += 1
                    return at
                return at + len(quote)
            if quote != '"""' and text[at] == '\\':
                at += 2
            elif text.startswith('${', at) and quote != "'":
                # Skip nested Kotlin strings in interpolation expressions.
                at += 2
                depth = 1
                while at < len(text) and depth:
                    if text[at] in ('"', "'"):
                        q = '"""' if text.startswith('"""', at) else text[at]
                        at = string_end(at, q)
                    else:
                        depth += (text[at] == '{') - (text[at] == '}')
                        at += 1
            else:
                at += 1
        raise ValueError(f'Unterminated string/character literal at line {text.count(chr(10), 0, start)+1}')
    while i < len(text):
        start = i
        if text.startswith('//', i):
            i = text.find('\n', i)
            if i == -1:
                i = len(text)
        elif text.startswith('/*', i):
            depth = 1
            i += 2
            while i < len(text) and depth:
                if text.startswith('/*', i): depth += 1; i += 2
                elif text.startswith('*/', i): depth -= 1; i += 2
                else: i += 1
            if depth:
                raise ValueError('Unterminated block comment')
        elif text[i] in ('"', "'"):
            quote = '"""' if text.startswith('"""', i) else text[i]
            i = string_end(i, quote)
        else:
            i += 1
            continue
        mask(start, i)
    return ''.join(result)


def verify():
    failures, checks = [], []
    def check(ok, description):
        (checks if ok else failures).append(description)
    sources = sorted(p for m in MODULES for p in (ROOT/m/'src/main').rglob('*.kt'))
    texts, symbols = {}, set()
    for path in sources:
        relative = path.relative_to(ROOT).as_posix()
        try:
            source = code_only(path.read_text('utf-8'))
        except ValueError as e:
            failures.append(f'{relative}: {e}')
            continue
        texts[path] = source
        forbidden = re.search(r'androidx\.compose\b|org\.jetbrains\.compose\b|@Composable\b|'
                              r'\b(?:remember|mutableStateOf|mutableFloatStateOf|DisposableEffect|LaunchedEffect)\s*\(', source)
        check(not forbidden, f'No Compose code: {relative}')
        stack = []
        for char in source:
            if char in '({[': stack.append(char)
            elif char in ')}]':
                if not stack or stack.pop() != dict(zip(')}]', '({['))[char]:
                    stack.append('unbalanced'); break
        check(not stack, f'Balanced lexical delimiters: {relative}')
        check(not re.search(r'(?m)^\s*(?:internal\s+|public\s+)?(?:expect|actual)\s+(?:fun|class|object|val|var)', source),
              f'No KMP expect/actual declaration: {relative}')
        check(not re.search(r'(?m)^\s*@\w[^\n]*\s*\Z', source), f'No dangling annotation: {relative}')
        package = re.search(r'(?m)^package\s+([\w.]+)', source)
        if package:
            prefix = package.group(1) + '.'
            names = re.findall(r'\b(?:class|interface|object|typealias|val|var)\s+([\w]+)', source)
            names += re.findall(r'\bfun\s+(?:<[^\n]+?>\s*)?(?:[\w<>?]+\.)*([\w]+)\s*\(', source)
            names += re.findall(r'\b(?:val|var)\s+(?:[\w<>?]+\.)+([\w]+)', source)
            symbols.update(prefix + name for name in names)
    symbols.update({'io.github.sceneview.BuildConfig', 'io.github.sceneview.R'})
    unknown = set()
    for path, source in texts.items():
        for imp in re.findall(r'(?m)^import\s+(io\.github\.sceneview\.[\w.]+)', source):
            parts = imp.split('.')
            if not any('.'.join(parts[:n]) in symbols for n in range(4, len(parts)+1)):
                unknown.add((path.relative_to(ROOT).as_posix(), imp))
    # This catches missing imported declarations, but cannot replace Kotlin name/type resolution.
    check(not unknown, f'Internal imports have candidate declarations; unknown={sorted(unknown)}')

    xmls = sorted(p for m in MODULES for p in (ROOT/m/'src/main').rglob('*.xml'))
    for path in xmls:
        try: ET.parse(path)
        except ET.ParseError as e: failures.append(f'Invalid XML {path}: {e}')
    checks.append(f'XML parsed: {len(xmls)} files')
    attrs = {e.attrib['name'] for e in ET.parse(ROOT/'sceneview-native/src/main/res/values/attrs.xml').iter('attr')}
    for name in re.findall(r'R\.styleable\.SceneView_(\w+)', (ROOT/'sceneview-native/src/main/java/io/github/sceneview/SceneView.kt').read_text()):
        check(name in attrs, f'XML attribute exists: {name}')
    layout = ET.parse(ROOT/'sample/src/main/res/layout/activity_main.xml')
    ids = {value.split('/')[-1] for e in layout.iter() for key, value in e.attrib.items() if key.endswith('}id')}
    activity = (ROOT/'sample/src/main/java/local/sceneview/sample/MainActivity.kt').read_text('utf-8')
    check(set(re.findall(r'R\.id\.(\w+)', activity)) <= ids, 'Sample Kotlin IDs exist in XML')
    for element in layout.iter():
        if element.tag.startswith('io.github.sceneview.'):
            check(element.tag in symbols, f'XML View class exists: {element.tag}')

    for module in MODULES:
        build = (ROOT/module/'build.gradle.kts').read_text('utf-8')
        without_line_comments = re.sub(r'(?m)//.*$', '', build)
        check('compose' not in without_line_comments.lower(), f'No Compose build configuration in {module}')
        check(not re.search(r'["\']io\.github\.sceneview:', build), f'{module} uses local source, no upstream binary SceneView dependency')
    build = (ROOT/'sceneview-native/build.gradle.kts').read_text('utf-8')
    check(all(v == '1.72.1' for v in re.findall(r'com\.google\.android\.filament:[^:]+:([^"\n]+)', build)),
          'Filament dependency pins match 4.35.0 published artifacts')
    check('resolutionStrategy.eachDependency' in (ROOT/'build.gradle.kts').read_text(), 'Gradle contains a transitive Compose rejection guard (not executed)')
    common = ROOT/'upstream/4.35.0/sceneview-core-android/commonMain'
    check(all((ROOT/'sceneview-core/src/main/java'/p.relative_to(common)).is_file() for p in common.rglob('*.kt')),
          'All common core source paths are present (Android overrides used where applicable)')
    for entry in json.loads((ROOT/'upstream/provenance.json').read_text('utf-8')):
        path = ROOT/entry['file']
        check(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], f'Source artifact SHA-256: {entry["file"]}')
    for jar, folder in [('sceneview-4.35.0-sources.jar','sceneview'),
                        ('sceneview-core-android-sources.jar','sceneview-core-android')]:
        with ZipFile(ROOT/'upstream/4.35.0'/jar) as archive:
            for name in archive.namelist():
                if name.endswith('.kt'):
                    path = ROOT/'upstream/4.35.0'/folder/name
                    check(path.is_file() and path.read_bytes() == archive.read(name), f'Unmodified archived source: {folder}/{name}')
    if (ROOT/'PORT-MANIFEST.json').is_file():
        for entry in json.loads((ROOT/'PORT-MANIFEST.json').read_text('utf-8'))['files']:
            path = ROOT/entry['file']
            if entry['sha256']:
                check(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], f'Port inventory hash: {entry["file"]}')
    asset_count = 0
    with ZipFile(ROOT/'upstream/4.35.0/sceneview-4.35.0.aar') as archive:
        for name in archive.namelist():
            if name.startswith(('assets/', 'res/')) and not name.endswith('/'):
                path = ROOT/'sceneview-native/src/main'/name
                check(path.is_file() and path.read_bytes() == archive.read(name), f'Unmodified AAR asset: {name}')
                asset_count += 1
    check((ROOT/'sceneview-native/src/main/assets/environments/neutral/neutral_ibl.ktx').is_file(), 'Default IBL exists')

    data = (ROOT/'sample/src/main/assets/models/learning_cube.glb').read_bytes()
    magic, version, length = struct.unpack_from('<III', data)
    check((magic, version, length) == (0x46546c67, 2, len(data)), 'Sample GLB header/length')
    size, kind = struct.unpack_from('<II', data, 12)
    check(kind == 0x4e4f534a, 'Sample GLB JSON chunk')
    model = json.loads(data[20:20+size])
    bin_size, bin_kind = struct.unpack_from('<II', data, 20+size)
    check(bin_kind == 0x004e4942 and 28+size+bin_size == len(data), 'Sample GLB BIN chunk')
    check(model['asset']['version'] == '2.0' and model['animations'][0]['name'] == 'Spin', 'Sample glTF version and named animation')
    binary = data[28+size:]
    for accessor in model['accessors']:
        view = model['bufferViews'][accessor['bufferView']]
        count = {'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[accessor['type']]
        component_size = {5123:2,5126:4}[accessor['componentType']]
        check(accessor['count'] * count * component_size <= view['byteLength'] and
              view.get('byteOffset', 0) + view['byteLength'] <= len(binary), 'Sample accessor stays within BIN bounds')
    check((ROOT/'sample/src/main/assets/textures/checker.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), 'Sample texture is PNG')
    for name in ['LICENSE','NOTICE','gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar']:
        check((ROOT/name).is_file(), f'Delivery file exists: {name}')
    props = (ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text()
    check(bool(re.search(r'distributionSha256Sum=[a-f0-9]{64}', props)), 'Gradle distribution checksum pinned')
    return {
        'kind': 'structural-source-audit-not-compilation',
        'passed': not failures, 'checks_passed': len(checks), 'failures': failures,
        'kotlin_files': len(sources), 'xml_files': len(xmls), 'upstream_assets_compared': asset_count,
        'not_performed': ['Gradle execution', 'dependency resolution', 'Kotlin compilation/type checking',
                          'Android lint/unit/instrumentation tests', 'device/GPU/transparency/lifecycle tests'],
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--report', action='store_true')
    args = parser.parse_args()
    report = verify()
    encoded = json.dumps(report, indent=2, ensure_ascii=False)
    if args.report:
        (ROOT/'STATIC-VALIDATION.json').write_text(encoded+'\n', 'utf-8')
    print(encoded)
    sys.exit(0 if report['passed'] else 1)
