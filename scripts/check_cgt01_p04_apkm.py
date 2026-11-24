#!/usr/bin/env python3
"""Read-only, stdlib DEX symbol verification for the one pinned ChatGPT APKM.
Does not run code, inspect messages, install apps or enable hooks.
"""
import argparse
import hashlib
import io
import json
import struct
import zipfile

EXPECTED = {
    'package': 'com.openai.chatgpt',
    'version': '1.2026.321',
    'versionCode': '2632128',
    'base_sha256': '3ef776e51c81b47a16938a027b03e3dc0c059fa5825f746ba35955709f6d63ad',
    'classes4_sha256': 'a2d2b9725d0cc5eceeb466bdbd736eb91b7a78b2288040d5b466af04ce70d76d',
}
OWNER = 'Llxl;'
RENDER = ('a', 'V', ['Ljava/lang/String;', 'Ljava/lang/String;', 'Lxpm0;',
     'Lr0o0;', 'Lr0o0;', 'F', 'Z', 'Z', 'Z', 'Z',
     'Lkotlin/jvm/functions/Function0;', 'Lkotlin/jvm/functions/Function2;',
     'Z', 'Z', 'Lkotlin/jvm/functions/Function0;', 'Z', 'Lva41;', 'Lrqr;', 'I', 'I'])
BUTTON = ('c', 'V', ['I', 'I', 'Lco3;', 'Lrqr;', 'Lr0o0;'])


def dex_symbols(dex):
    if dex[:4] != b'dex\n' or len(dex) < 112:
        raise ValueError('MALFORMED_DEX')
    u32 = lambda n: struct.unpack_from('<I', dex, n)[0]
    u16 = lambda n: struct.unpack_from('<H', dex, n)[0]
    if u32(32) != len(dex) or u32(36) != 112:
        raise ValueError('DEX_LENGTH_OR_HEADER_MISMATCH')
    def leb(off):
        shift = val = 0
        for _ in range(5):
            digit = dex[off]; off += 1
            val |= (digit & 127) << shift
            if digit < 128:
                return val, off
            shift += 7
        raise ValueError('INVALID_LEB128')
    strings = []
    for i in range(u32(56)):
        offset = u32(u32(60) + 4*i)
        _, offset = leb(offset)
        strings.append(dex[offset:dex.index(0, offset)].decode('utf-8', 'replace'))
    types = [strings[u32(u32(68)+4*i)] for i in range(u32(64))]
    prototypes = []
    for i in range(u32(72)):
        off = u32(76)+i*12
        params_off = u32(off+8)
        args = ([types[u16(params_off+4+j*2)] for j in range(u32(params_off))]
                if params_off else [])
        prototypes.append((types[u32(off+4)], args))
    methods = []
    for i in range(u32(88)):
        off = u32(92)+i*8
        methods.append((types[u16(off)], strings[u32(off+4)], *prototypes[u16(off+2)]))
    found = {}
    for i in range(u32(96)):
        off = u32(100)+32*i
        if types[u32(off)] != OWNER:
            continue
        data_off = u32(off+24)
        if data_off == 0:
            raise ValueError('CODEBLOCK_CLASS_MISSING_DATA')
        fields, p = leb(data_off)
        instance_fields, p = leb(p)
        direct, p = leb(p)
        virtual, p = leb(p)
        for _ in range(fields+instance_fields):
            _, p = leb(p); _, p = leb(p)
        for count in (direct, virtual):
            midx = 0
            for _ in range(count):
                delta, p = leb(p); midx += delta
                flags, p = leb(p)
                code_off, p = leb(p)
                method = methods[midx]
                if method[0] == OWNER and method[1] in ('a','c'):
                    found[method[1]] = {
                        'return_type': method[2], 'params': method[3],
                        'static': bool(flags & 0x8), 'code_off': code_off,
                    }
    return strings, found, dex


def check(apkm):
    with zipfile.ZipFile(apkm) as pack:
        info = json.loads(pack.read('info.json'))
        if (info.get('pname') != EXPECTED['package'] or
            info.get('release_version') != EXPECTED['version'] or
            str(info.get('versioncode')) != EXPECTED['versionCode']):
            raise ValueError('UNSUPPORTED_APKM_IDENTITY')
        base = pack.read('base.apk')
    base_hash = hashlib.sha256(base).hexdigest()
    if base_hash != EXPECTED['base_sha256']:
        raise ValueError('BASE_APK_HASH_MISMATCH')
    with zipfile.ZipFile(io.BytesIO(base)) as apk:
        dex = apk.read('classes4.dex')
    dex_hash = hashlib.sha256(dex).hexdigest()
    if dex_hash != EXPECTED['classes4_sha256']:
        raise ValueError('DEX_HASH_MISMATCH')
    strings, symbols, data = dex_symbols(dex)
    for expected_name, (method, return_type, params) in [('CodeBlockImpl', RENDER), ('CopyButtonImpl', BUTTON)]:
        actual = symbols.get(method)
        if actual is None or not actual['static'] or actual['return_type'] != return_type or actual['params'] != params:
            raise ValueError('DEX_METHOD_SIGNATURE_MISMATCH:'+expected_name)
        marker_id = strings.index(expected_name)
        off = actual['code_off']
        if not off:
            raise ValueError('RENDERER_WITHOUT_CODE:'+expected_name)
        instructions = off+16
        count = struct.unpack_from('<I', data, off+12)[0]
        # Conservative: inspect all words for const-string reference to the exact marker.
        # It proves the method references the marker, not the UI gesture.
        seen = any((struct.unpack_from('<H', data, instructions+j*2)[0] & 255) == 0x1a and
                   struct.unpack_from('<H', data, instructions+(j+1)*2)[0] == marker_id
                   for j in range(count-1))
        if not seen:
            raise ValueError('DEX_MARKER_NOT_REFERENCED:'+expected_name)
    return {
        'verdict': 'STATIC_DEX_PINS_PASS_NOT_RUNTIME',
        'apk_package': EXPECTED['package'], 'version_name': EXPECTED['version'],
        'version_code': int(EXPECTED['versionCode']), 'base_sha256': base_hash,
        'dex': 'classes4.dex', 'dex_sha256': dex_hash, 'owner': OWNER,
        'renderer': 'lxl.a', 'renderer_parameter_count': len(RENDER[2]),
        'callback_parameter_indices': [10,14], 'copy_button_renderer': 'lxl.c',
        'device_copy_causality': 'UNVERIFIED', 'termux': 'NOT_IMPLEMENTED'
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apkm', help='Original ChatGPT_1.2026.321 APKM, never extracted/written')
    args = parser.parse_args()
    try:
        print(json.dumps(check(args.apkm), sort_keys=True))
    except (ValueError,KeyError,IndexError,struct.error,zipfile.BadZipFile,OSError) as e:
        print(json.dumps({'verdict':'REFUSED','reason':str(e).split(':')[0]}))
        return 2
    return 0
if __name__ == '__main__':
    raise SystemExit(main())
