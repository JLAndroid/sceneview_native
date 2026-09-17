"""Generate an original, deterministic glTF 2.0 cube + named Spin animation and PNG.

Uses only Python's standard library. Assets are licensed under the repository's Apache-2.0.
"""
from pathlib import Path
import json
import math
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'sample/src/main/assets'


def png_checker():
    size = 64
    rows = bytearray()
    for y in range(size):
        rows.append(0)
        for x in range(size):
            rows.extend((32, 112, 215, 255) if (x // 8 + y // 8) % 2 else (255, 213, 76, 255))
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>2I5B', size, size, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


def make_glb():
    binary = bytearray()
    views, accessors = [], []
    def add(values, fmt, component_type, data_type, count, bounds=None, target=None):
        while len(binary) % 4:
            binary.append(0)
        data = struct.pack('<' + fmt * len(values), *values)
        view = dict(buffer=0, byteOffset=len(binary), byteLength=len(data))
        if target:
            view['target'] = target
        views.append(view)
        binary.extend(data)
        accessor = dict(bufferView=len(views)-1, componentType=component_type, count=count, type=data_type)
        if bounds:
            accessor.update(min=bounds[0], max=bounds[1])
        accessors.append(accessor)
        return len(accessors)-1

    faces = [
        ((0, 0, 1), [(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]),
        ((0, 0,-1), [(1,-1,-1),(-1,-1,-1),(-1,1,-1),(1,1,-1)]),
        ((1, 0, 0), [(1,-1,1),(1,-1,-1),(1,1,-1),(1,1,1)]),
        ((-1,0, 0), [(-1,-1,-1),(-1,-1,1),(-1,1,1),(-1,1,-1)]),
        ((0, 1, 0), [(-1,1,1),(1,1,1),(1,1,-1),(-1,1,-1)]),
        ((0,-1, 0), [(-1,-1,-1),(1,-1,-1),(1,-1,1),(-1,-1,1)]),
    ]
    positions, normals, uvs, indices = [], [], [], []
    for face_index, (normal, corners) in enumerate(faces):
        for corner in corners:
            positions.extend(v * .5 for v in corner)
            normals.extend(normal)
        uvs.extend([0,1, 1,1, 1,0, 0,0])
        indices.extend(face_index * 4 + v for v in [0,1,2,0,2,3])
    pos = add(positions, 'f', 5126, 'VEC3', 24, ([-.5]*3, [.5]*3), 34962)
    norm = add(normals, 'f', 5126, 'VEC3', 24, target=34962)
    uv = add(uvs, 'f', 5126, 'VEC2', 24, target=34962)
    idx = add(indices, 'H', 5123, 'SCALAR', 36, target=34963)
    times = add([0,1,2,3,4], 'f', 5126, 'SCALAR', 5, ([0], [4]))
    quaternions = []
    for i in range(5):
        quaternions.extend([0, math.sin(i * math.pi / 4), 0, math.cos(i * math.pi / 4)])
    rotation = add(quaternions, 'f', 5126, 'VEC4', 5)
    doc = {
        'asset': {'version':'2.0', 'generator':'SceneView Native learning cube generator',
                  'copyright':'Apache-2.0; original procedural sample'},
        'scene':0, 'scenes':[{'nodes':[0]}], 'nodes':[{'name':'LearningCube','mesh':0}],
        'meshes':[{'primitives':[{'attributes':{'POSITION':pos,'NORMAL':norm,'TEXCOORD_0':uv},
                                  'indices':idx, 'material':0}]}],
        'materials':[{'name':'Blue','pbrMetallicRoughness':{
            'baseColorFactor':[.10,.43,.85,1], 'metallicFactor':.15,'roughnessFactor':.35}}],
        'animations':[{'name':'Spin', 'samplers':[{'input':times,'output':rotation,'interpolation':'LINEAR'}],
                       'channels':[{'sampler':0,'target':{'node':0,'path':'rotation'}}]}],
        'buffers':[{'byteLength':len(binary)}], 'bufferViews':views, 'accessors':accessors,
    }
    encoded = json.dumps(doc, separators=(',',':')).encode()
    encoded += b' ' * (-len(encoded) % 4)
    binary += b'\0' * (-len(binary) % 4)
    return (struct.pack('<III', 0x46546c67, 2, 12 + 8 + len(encoded) + 8 + len(binary))
            + struct.pack('<II', len(encoded), 0x4e4f534a) + encoded
            + struct.pack('<II', len(binary), 0x004e4942) + binary)


if __name__ == '__main__':
    for name, data in [('models/learning_cube.glb', make_glb()), ('textures/checker.png', png_checker())]:
        path = ASSETS / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        print(f'{path.relative_to(ROOT)}: {len(data)} bytes')
