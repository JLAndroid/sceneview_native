"""Verify built AARs/publications and produce the user's AAR delivery. Run after Gradle succeeds."""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import hashlib
import json
import shutil
import struct
import xml.etree.ElementTree as ET
from publication_config import GROUP, GROUP_PATH, VERSION

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT/'dist'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    # Refuse to label a failed or incomplete build as deliverable.
    logs = ['build-native3-aar.log', 'build-native3-delivery.log',
            'build-native3-packaged-sample.log', 'build-native3-raw-aar.log',
            'build-native3-raw-aar-release.log', 'build-native3-api21.log']
    for name in logs:
        text = (ROOT/name).read_text('utf-8-sig')
        assert 'BUILD SUCCESSFUL' in text and 'BUILD FAILED' not in text, name
    dependencies = json.loads((DIST/'packaged-dependencies.json').read_text())
    assert dependencies['resolved'] and dependencies['noComposeArtifacts']
    modules = dependencies['modules']
    for name in ('sceneview-core', 'sceneview-native'):
        assert any(m['group']==GROUP and m['name']==name and
                   m['version']==VERSION and m['type']=='module' for m in modules), name
    assert not any(m['group'].startswith(('androidx.compose','org.jetbrains.compose')) for m in modules)

    raw_dependencies = json.loads((DIST/'raw-aar-dependencies.json').read_text())
    assert raw_dependencies['resolved'] and raw_dependencies['noComposeArtifacts']
    assert not any(m['group']==GROUP for m in raw_dependencies['modules']), 'Raw AAR test must not resolve project/Maven substitutes'
    for graph in (dependencies, raw_dependencies):
        for group, name, version in (
            ('org.jetbrains.kotlin', 'kotlin-stdlib', '2.0.21'),
            ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.9.0'),
            ('dev.romainguy', 'kotlin-math-jvm', '1.5.3')):
            assert any(m['group']==group and m['name']==name and m['version']==version for m in graph['modules']), (group,name,version)
    test_report = ROOT/'sceneview-core/build/test-results/testReleaseUnitTest/TEST-io.github.sceneview.math.Kotlin19CompatibilityTest.xml'
    tests = ET.parse(test_report).getroot()
    assert tests.attrib['tests']=='3' and tests.attrib['failures']=='0' and tests.attrib['errors']=='0'
    for source in [ROOT/'build.gradle.kts', ROOT/'compatibility-check/build.gradle']:
        assert '1.9.0' in source.read_text('utf-8') and '8.2.2' in source.read_text('utf-8')
    for module in ('sceneview-core','sceneview-native','sample'):
        assert 'skip-metadata-version-check' not in (ROOT/module/'build.gradle.kts').read_text('utf-8')

    for module in ('sceneview-core', 'sceneview-native'):
        lint_report = ROOT/module/'build/reports/lint-results-release.xml'
        assert not ET.parse(lint_report).getroot().findall('issue'), str(lint_report)

    (DIST/'aar').mkdir(parents=True, exist_ok=True)
    inspected = []
    for name in ('sceneview-core', 'sceneview-native'):
        source = ROOT/name/'build/outputs/aar'/f'{name}-release.aar'
        publication = DIST/'repository'/GROUP_PATH/name/VERSION
        published = publication/f'{name}-{VERSION}.aar'
        assert source.read_bytes() == published.read_bytes(), 'Published AAR differs from build output'
        target = DIST/'aar'/f'{name}-{VERSION}.aar'
        raw_copy = ROOT/'compatibility-check/app/libs'/target.name
        assert source.read_bytes()==raw_copy.read_bytes(), 'Raw AAR consumer tested different bytes'
        shutil.copy2(source, target)
        with ZipFile(target) as aar:
            assert aar.testzip() is None
            assert {'classes.jar','AndroidManifest.xml'} <= set(aar.namelist())
            manifest = ET.fromstring(aar.read('AndroidManifest.xml'))
            assert manifest.find('uses-sdk').attrib['{http://schemas.android.com/apk/res/android}minSdkVersion']=='21'

            with ZipFile(BytesIO(aar.read('classes.jar'))) as classes:
                bytecode = {n:classes.read(n) for n in classes.namelist() if n.endswith('.class')}
                assert bytecode
                assert all(b'androidx/compose/' not in b and b'org/jetbrains/compose/' not in b
                           for b in bytecode.values()), 'Unexpected bytecode reference to Compose'
                assert all(b'java/util/function/Consumer' not in b for b in bytecode.values()), 'Consumer reference remains in AAR'
                for forbidden in (b'android/util/FloatProperty', b'android/util/IntProperty',
                                  b'android/view/GestureDetector$OnContextClickListener'):
                    assert all(forbidden not in b for b in bytecode.values()), forbidden
                versions = sorted({struct.unpack_from('>H', b, 6)[0] for b in bytecode.values()})
                assert versions == [61], ('Expected Java 17 bytecode', versions)
                if name == 'sceneview-native':
                    assert 'io/github/sceneview/SceneView.class' in bytecode
                    assert 'io/github/sceneview/TextureSceneView.class' in bytecode
                    assert b'android/widget/FrameLayout' in bytecode['io/github/sceneview/SceneView.class']
                    for asset in (ROOT/name/'src/main/assets').rglob('*'):
                        if asset.is_file():
                            assert aar.read('assets/'+asset.relative_to(ROOT/name/'src/main/assets').as_posix()) == asset.read_bytes()
                    assert 'res/values/values.xml' in aar.namelist()
            inspected.append({'module':name,'file':target.relative_to(DIST).as_posix(),
                              'bytes':target.stat().st_size,'sha256':sha(target),
                              'class_count':len(bytecode),'class_major_versions':versions,
                              'no_compose_bytecode_references':True,'no_consumer_bytecode_references':True})
        ET.parse(publication/f'{name}-{VERSION}.pom')
        metadata = json.loads((publication/f'{name}-{VERSION}.module').read_text())
        assert metadata['component']['group'] == GROUP
        assert metadata['component']['module'] == name
        assert metadata['component']['version'] == VERSION
        if name == 'sceneview-native':
            for variant in metadata['variants']:
                for dependency in variant.get('dependencies', []):
                    if dependency['group'] == 'com.google.android.filament':
                        assert dependency['version']['strictly'] == '1.72.1'

    apk = ROOT/'sample/build/outputs/apk/debug/sample-debug.apk'
    with ZipFile(apk) as archive:
        assert archive.testzip() is None
        native_libs = sorted(n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so'))
        assert native_libs, 'Sample APK is missing Filament native libraries'
    report = {
        'date':'2026-09-17','version':VERSION,
        'jdk':'17.0.17','gradle':'8.2','agp':'8.2.2','kotlin':'1.9.0',
        'kotlin_stdlib':'2.0.21','jvm_target':17,'kotlin_math':'1.5.3','coroutines':'1.9.0',
        'compileSdk':34,'minSdk':21,'filament':'1.72.1',
        'release_aar_build':'passed','local_maven_publication':'passed',
        'sample_with_source_dependencies':'passed','sample_with_published_aar_dependencies':'passed',
        'sample_with_raw_aar_files':'passed','raw_aar_release_r8_build':'passed',
        'release_lint_vital':'passed','api21_newapi_inlinedapi_lint':'passed','math_regression_tests':3,
        'resolved_raw_aar_components':len(raw_dependencies['modules']),
        'resolved_packaged_sample_components':len(modules),'no_compose_components':True,
        'artifacts':inspected,
        'sample_apk':{'path':apk.relative_to(ROOT).as_posix(),'bytes':apk.stat().st_size,
                      'sha256':sha(apk),'native_libraries':native_libs},
        'raw_aar_sample_apk':{'path':'compatibility-check/app/build/outputs/apk/debug/app-debug.apk',
                              'sha256':sha(ROOT/'compatibility-check/app/build/outputs/apk/debug/app-debug.apk')},
        'raw_aar_release_apk':{'path':'compatibility-check/app/build/outputs/apk/release/app-release-unsigned.apk',
                                'sha256':sha(ROOT/'compatibility-check/app/build/outputs/apk/release/app-release-unsigned.apk')},
        'unit_test_report':{'path':test_report.relative_to(ROOT).as_posix(),'sha256':sha(test_report)},
        'build_logs':[{'file':name,'sha256':sha(ROOT/name)} for name in logs],
        'not_performed':['device installation','device/GPU/lifecycle/transparency testing',
                         'full Android lint','instrumentation tests','minified APK runtime testing'],
    }
    (DIST/'BUILD-REPORT.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8')
    for name in ('DELIVERY.md','README.md','PORTING.md','VALIDATION.md','LICENSE','NOTICE','PORT-MANIFEST.json'):
        shutil.copy2(ROOT/name, DIST/name)
    shutil.copy2(ROOT/'tools/aar-dependencies.gradle', DIST/'aar-dependencies.gradle')

    files = sorted(p for p in DIST.rglob('*') if p.is_file() and
                   not p.name.endswith('.zip') and p.name != 'SHA256SUMS.txt')
    (DIST/'SHA256SUMS.txt').write_text(''.join(f'{sha(p)}  {p.relative_to(DIST).as_posix()}\n' for p in files),'utf-8')
    print(json.dumps({'aar':inspected,'report':str(DIST/'BUILD-REPORT.json')},ensure_ascii=False,indent=2))



if __name__ == '__main__':
    main()
