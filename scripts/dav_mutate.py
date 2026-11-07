#!/usr/bin/env python3
"""模拟「另一台设备」直接改写远端 data.json + manifest.json。

用法：
  python3 dav_mutate.py <root> <mode>
    mode = newer     把 seed-course-piano 的 note 改成新内容，updatedAt=现在（应被拉取合并）
    mode = conflict  改成新内容但 updatedAt 与本地完全相同（应生成冲突副本）
"""
import gzip
import hashlib
import json
import os
import sys
import time

root = sys.argv[1]
mode = sys.argv[2]
data_path = os.path.join(root, "data.json")
man_path = os.path.join(root, "manifest.json")

raw = gzip.decompress(open(data_path, "rb").read())
snap = json.loads(raw)
man = json.loads(open(man_path, "r", encoding="utf-8").read())

target = None
for c in snap["courses"]:
    if c["id"] == "seed-course-piano":
        target = c
        break
assert target, "seed-course-piano not found"

old_updated = target["updatedAt"]
if mode == "newer":
    target["note"] = "第二台设备改的备注 newer"
    target["updatedAt"] = int(time.time() * 1000)
elif mode == "conflict":
    target["note"] = "第二台设备改的备注 conflict"
    target["updatedAt"] = old_updated  # 与本地完全相同 → 5 秒内 + 内容不同
else:
    raise SystemExit("bad mode")

new_bytes = gzip.compress(json.dumps(snap, ensure_ascii=False, separators=(",", ":")).encode())
open(data_path, "wb").write(new_bytes)

etag = hashlib.md5(new_bytes).hexdigest()
man["dataEtag"] = etag
man["lastWriteAt"] = int(time.time() * 1000)
man["lastDeviceId"] = "other-device-sim"
man["recordCount"] = sum(len(snap[k]) for k in ("children", "courses", "payments", "lessons"))
open(man_path, "w", encoding="utf-8").write(json.dumps(man, ensure_ascii=False, separators=(",", ":")))

print("mode", mode, "oldUpdated", old_updated, "newUpdated", target["updatedAt"], "etag", etag)
