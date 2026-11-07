#!/usr/bin/env python3
"""Minimal zipalign: rewrites a zip so STORED entries' data start on 4-byte (.so: 4096) boundaries.
Optionally adds extra files (name=path). resources.arsc forced STORED."""
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
extra = dict(a.split('=',1) for a in sys.argv[3:])
zin = zipfile.ZipFile(src)
entries = [(i, zin.read(i.filename)) for i in zin.infolist()]
for name, path in extra.items():
    zi = zipfile.ZipInfo(name, date_time=(2008,1,1,0,0,0)); zi.compress_type = zipfile.ZIP_DEFLATED
    entries.append((zi, open(path,'rb').read()))
with open(dst, 'wb') as f:
    zout = zipfile.ZipFile(f, 'w')
    for info, data in entries:
        zi = zipfile.ZipInfo(info.filename, date_time=(2008,1,1,0,0,0))
        store = info.compress_type == zipfile.ZIP_STORED or info.filename == 'resources.arsc'
        zi.compress_type = zipfile.ZIP_STORED if store else zipfile.ZIP_DEFLATED
        zi.external_attr = info.external_attr
        if store:
            align = 4096 if info.filename.endswith('.so') else 4
            hdr = 30 + len(zi.filename.encode())
            pos = f.tell() + hdr
            pad = (-pos - 4) % align  # 4 bytes for extra header
            zi.extra = b'\x35\xd9' + pad.to_bytes(2,'little') + b'\x00'*pad
        zout.writestr(zi, data)
    zout.close()
