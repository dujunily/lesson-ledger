#!/bin/bash
# ui_tap.sh —— 按文字/描述定位节点并点击其中心点（避免手算坐标点歪）
# 用法：scripts/ui_tap.sh <device> <文字片段> [第几个，默认 1]
#       文字片段以 "desc:" 开头时匹配 content-desc
set -u
DEV="${1:?device}"
NEEDLE="${2:?text}"
INDEX="${3:-1}"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

adb -s "$DEV" shell rm -f /sdcard/ui.xml >/dev/null 2>&1
OUT=$(adb -s "$DEV" shell uiautomator dump /sdcard/ui.xml 2>&1)
if ! echo "$OUT" | grep -q "dumped to"; then
  echo "DUMP FAILED: $OUT"
  exit 1
fi
adb -s "$DEV" shell cat /sdcard/ui.xml > /tmp/ui_tap.xml

DEV="$DEV" python3 - "$NEEDLE" "$INDEX" <<'PY'
import os, re, subprocess, sys
needle, index = sys.argv[1], int(sys.argv[2])
dev = os.environ['DEV']
x = open('/tmp/ui_tap.xml', encoding='utf-8').read()
attr = 'content-desc' if needle.startswith('desc:') else 'text'
if needle.startswith('desc:'):
    needle = needle[5:]
hits = []
for n in re.findall(r'<node[^>]*>', x):
    t = re.search(attr + r'="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not (t and b and needle in t.group(1)):
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    hits.append((t.group(1), (x1 + x2) // 2, (y1 + y2) // 2))
if not hits:
    print('NOT FOUND:', needle)
    sys.exit(2)
print('命中 %d 个，取第 %d 个' % (len(hits), index))
label, cx, cy = hits[min(index, len(hits)) - 1]
print('  -> "%s" (%d,%d)' % (label[:30], cx, cy))
subprocess.run(['adb', '-s', dev, 'shell', 'input', 'tap', str(cx), str(cy)], check=False)
PY
