"""Legacy validator for the previous GitHub Raw Maven release (not used by JitPack).

This does not execute Gradle or compile code. Run after producing a verified delivery.
Use --check to validate the checked-in Maven files without writing anything.
"""
from pathlib import Path
from zipfile import ZipFile
import argparse
import hashlib
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
GROUP = 'local.sceneview'
MODULES = ('sceneview-core', 'sceneview-native')
HASHES = ('md5', 'sha1', 'sha256', 'sha512')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def prepare(check_only=False):
    report = json.loads((ROOT/'dist/BUILD-REPORT.json').read_text('utf-8'))
    version = report['version']
    require(version == '4.35.0-native.3-kotlin1.9',
            'This script is only for the archived GitHub Raw release; use JitPack for new releases')
    artifacts = {a['module']: a for a in report['artifacts']}
    staged = {}
    for module in MODULES:
        relative = Path('local/sceneview')/module/version
        source = ROOT/'dist/repository'/relative
        stem = f'{module}-{version}'
        aar = source/f'{stem}.aar'
        require(hashlib.sha256(aar.read_bytes()).hexdigest() == artifacts[module]['sha256'],
                f'{module}: AAR differs from the verified delivery')
        with ZipFile(source/f'{stem}-sources.jar') as archive:
            actual = set()
            for path in (ROOT/module/'src/main/java').rglob('*.kt'):
                name = path.relative_to(ROOT/module/'src/main/java').as_posix()
                actual.add(name)
                require(archive.read(name).decode('utf-8').replace('\r\n', '\n') == path.read_text('utf-8'),
                        f'{module}: source changed since delivery: {name}; rebuild before publishing')
            require({n for n in archive.namelist() if n.endswith('.kt')} == actual,
                    f'{module}: source inventory changed since delivery')
        with ZipFile(aar) as archive:
            require(archive.testzip() is None, f'{module}: invalid AAR')
            assets = ROOT/module/'src/main/assets'
            expected = {f'assets/{p.relative_to(assets).as_posix()}': p for p in assets.rglob('*') if p.is_file()}
            require({n for n in archive.namelist() if n.startswith('assets/') and not n.endswith('/')} == set(expected),
                    f'{module}: asset inventory changed since delivery')
            for name, path in expected.items():
                require(archive.read(name) == path.read_bytes(), f'{module}: asset changed: {name}')
        pom = ET.parse(source/f'{stem}.pom').getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        for field, value in (('groupId', GROUP), ('artifactId', module), ('version', version), ('packaging', 'aar')):
            require(pom.findtext('m:'+field, namespaces=ns) == value, f'{module}: invalid POM {field}')
        metadata = json.loads((source/f'{stem}.module').read_text('utf-8'))
        require(all(metadata['component'][key] == value for key, value in
                    (('group', GROUP), ('module', module), ('version', version))), f'{module}: invalid module coordinates')
        for variant in metadata['variants']:
            for file in variant.get('files', []):
                data = (source/file['url']).read_bytes()
                require(len(data) == file['size'], f'{module}: incorrect artifact size')
                for algorithm in HASHES:
                    require(hashlib.new(algorithm, data).hexdigest() == file[algorithm],
                            f'{module}: incorrect {algorithm} in Gradle metadata')
        if module == 'sceneview-native':
            dependencies = pom.findall('m:dependencies/m:dependency', ns)
            require(any(d.findtext('m:groupId', namespaces=ns) == GROUP and
                        d.findtext('m:artifactId', namespaces=ns) == 'sceneview-core' and
                        d.findtext('m:version', namespaces=ns) == version for d in dependencies),
                    'Native POM must depend on the matching core artifact')
            for variant in metadata['variants']:
                if variant['attributes'].get('org.gradle.category') == 'library':
                    require(any(d['group'] == GROUP and d['module'] == 'sceneview-core' and
                                d['version']['requires'] == version for d in variant['dependencies']),
                            'Native Gradle metadata must depend on the matching core artifact')
        for suffix in ('.aar', '-sources.jar', '.pom', '.module'):
            name = stem+suffix
            data = (source/name).read_bytes()
            staged[relative/name] = data
            for algorithm in HASHES:
                checksum = hashlib.new(algorithm, data).hexdigest()
                require((source/(name+'.'+algorithm)).read_text().strip() == checksum,
                        f'{module}: invalid delivery checksum for {name}')
                staged[relative/(name+'.'+algorithm)] = checksum.encode('ascii')

    # Release coordinates are immutable: reject replacing an existing version.
    for relative, data in staged.items():
        target = ROOT/'maven'/relative
        if target.exists():
            require(target.read_bytes() == data, f'Refusing to overwrite an existing release: {relative}')
        elif check_only:
            raise ValueError(f'Missing published artifact: {relative}')
    for module in MODULES:
        base = Path('local/sceneview')/module
        existing = ROOT/'maven'/base
        versions = sorted({version} | {p.name for p in existing.iterdir() if p.is_dir()}) if existing.exists() else [version]
        xml = ET.Element('metadata')
        ET.SubElement(xml, 'groupId').text = GROUP
        ET.SubElement(xml, 'artifactId').text = module
        versioning = ET.SubElement(xml, 'versioning')
        ET.SubElement(versioning, 'latest').text = version
        ET.SubElement(versioning, 'release').text = version
        available = ET.SubElement(versioning, 'versions')
        for item in versions:
            ET.SubElement(available, 'version').text = item
        ET.indent(xml)
        data = ET.tostring(xml, encoding='utf-8', xml_declaration=True)+b'\n'
        staged[base/'maven-metadata.xml'] = data
        for algorithm in HASHES:
            staged[base/('maven-metadata.xml.'+algorithm)] = hashlib.new(algorithm, data).hexdigest().encode('ascii')
    for relative, data in staged.items():
        target = ROOT/'maven'/relative
        if check_only:
            require(target.is_file() and target.read_bytes() == data, f'Publication mismatch: {relative}')
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    print(json.dumps({'version': version, 'files': len(staged), 'bytes': sum(map(len, staged.values())),
                      'mode': 'check' if check_only else 'stage', 'gradle_executed': False}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    prepare(parser.parse_args().check)
