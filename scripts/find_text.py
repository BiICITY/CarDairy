"""Prints the center of the UI element with the given text from a uiautomator dump."""
import re
import sys

xml = open(sys.argv[1], encoding="utf8").read()
match = re.search(r'text="%s"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(sys.argv[2]), xml)
if match:
    x1, y1, x2, y2 = map(int, match.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
