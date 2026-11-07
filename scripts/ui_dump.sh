#!/bin/bash
# ui_dump.sh —— 强制重新 dump 界面层级，并打印前台包名 + 可点击/文本节点
# 用法：scripts/ui_dump.sh [emulator-5554] [--click|--text]
set -u
DEV="${1:-emulator-5554}"
MODE="${2:---click}"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
adb -s "$DEV" shell rm -f /sdcard/ui.xml >/dev/null 2>&1
OUT=$(adb -s "$DEV" shell uiautomator dump /sdcard/ui.xml 2>&1)
if ! echo "$OUT" | grep -q "dumped to"; then
  echo "DUMP FAILED: $OUT"
  exit 1
fi
adb -s "$DEV" shell cat /sdcard/ui.xml > /tmp/ui.xml
python3 - "$MODE" <<'PY'
import re, sys
mode = sys.argv[1]
x = open('/tmp/ui.xml', encoding='utf-8').read()
m = re.search(r'package="([^"]*)"', x)
print('pkg =', m.group(1) if m else '?')
pat = r'<node[^>]*clickable="true"[^>]*>' if mode == '--click' else r'<node[^>]*>'
for n in re.findall(pat, x):
    b = re.search(r'bounds="([^"]*)"', n)
    t = re.search(r'text="([^"]*)"', n)
    if mode == '--click':
        print(' ', (t.group(1)[:22] if t and t.group(1) else '-'), b.group(1))
    else:
        if t and t.group(1).strip():
            print(' ', t.group(1)[:28], b.group(1))
PY
