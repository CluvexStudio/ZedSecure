#!/usr/bin/env python3
"""Remove comments from Kotlin, Java and XML sources without touching code.

A lexer, not a regular expression: `//` inside a string, `/*` inside a raw
string, and `"` inside a `${...}` template all have to be understood to know
where a comment really starts.
"""
import sys

CODE, STRING, RAW, CHAR, LINE, BLOCK, BACKTICK = range(7)


def scan_kotlin(src, nested_blocks=True, report_state=False):
    """Return the (start, end) of every comment span."""
    spans = []
    state = CODE
    i, n = 0, len(src)
    depth = 0
    stack = []
    braces = 0
    start = 0
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if state == CODE:
            if c == "/" and nxt == "/":
                state, start = LINE, i
                i += 2
                continue
            if c == "/" and nxt == "*":
                state, start, depth = BLOCK, i, 1
                i += 2
                continue
            if c == '"':
                if src[i:i + 3] == '"""':
                    state = RAW
                    i += 3
                    continue
                state = STRING
                i += 1
                continue
            if c == "'":
                state = CHAR
                i += 1
                continue
            if c == "`":
                state = BACKTICK
                i += 1
                continue
            if stack:
                if c == "{":
                    braces += 1
                elif c == "}":
                    if braces == 0:
                        state, braces = stack.pop()
                        i += 1
                        continue
                    braces -= 1
            i += 1
            continue
        if state == LINE:
            if c == "\n":
                spans.append((start, i))
                state = CODE
            i += 1
            continue
        if state == BLOCK:
            if nested_blocks and c == "/" and nxt == "*":
                depth += 1
                i += 2
                continue
            if c == "*" and nxt == "/":
                depth -= 1
                i += 2
                if depth == 0:
                    spans.append((start, i))
                    state = CODE
                continue
            i += 1
            continue
        if state == BACKTICK:
            if c == "`":
                state = CODE
            i += 1
            continue
        if state in (STRING, CHAR):
            if c == "\\":
                i += 2
                continue
            if c == "\n" and state == STRING:
                state = CODE
                i += 1
                continue
            if (state == STRING and c == '"') or (state == CHAR and c == "'"):
                state = CODE
                i += 1
                continue
            if state == STRING and c == "$" and nxt == "{":
                stack.append((STRING, braces))
                state, braces = CODE, 0
                i += 2
                continue
            i += 1
            continue
        if state == RAW:
            if c == "$" and nxt == "{":
                stack.append((RAW, braces))
                state, braces = CODE, 0
                i += 2
                continue
            if c == '"':
                run = 0
                while i + run < n and src[i + run] == '"':
                    run += 1
                if run >= 3:
                    i += run
                    state = CODE
                    continue
                i += run
                continue
            i += 1
            continue
    if state == LINE:
        spans.append((start, n))
    if report_state:
        return spans, (state == CODE and not stack)
    return spans


def scan_xml(src):
    spans, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("<![CDATA[", i):
            end = src.find("]]>", i)
            i = n if end < 0 else end + 3
            continue
        if src.startswith("<!--", i):
            end = src.find("-->", i)
            end = n if end < 0 else end + 3
            spans.append((i, end))
            i = end
            continue
        i += 1
    return spans


def tidy(text, xml=False):
    lines = [ln.rstrip() for ln in text.split("\n")]
    out = []
    for ln in lines:
        blank = ln.strip() == ""
        if blank:
            if not out or out[-1].strip() == "":
                continue
            if not xml and out[-1].rstrip().endswith("{"):
                continue
        if not xml and ln.strip() in ("}", ")", "]", "},", "),", "],") and out and out[-1].strip() == "":
            out.pop()
        out.append(ln)
    while out and out[0].strip() == "":
        out.pop(0)
    while out and out[-1].strip() == "":
        out.pop()
    return "\n".join(out) + "\n"


def ends_clean(src, nested_blocks):
    """True when the file lexes to the end in plain code with nothing open."""
    spans, final = scan_kotlin(src, nested_blocks, report_state=True)
    return not spans and final


def strip(path):
    src = open(path, encoding="utf-8").read()
    if path.endswith(".xml"):
        spans, xml = scan_xml(src), True
    else:
        spans, xml = scan_kotlin(src, nested_blocks=path.endswith((".kt", ".kts"))), False
    if not spans:
        return 0
    out, last = [], 0
    for a, b in spans:
        out.append(src[last:a])
        out.append("\n" * src.count("\n", a, b))
        last = b
    out.append(src[last:])
    result = tidy("".join(out), xml)
    if not xml and not ends_clean(result, path.endswith((".kt", ".kts"))):
        raise SystemExit(f"{path}: refusing to write - the result does not lex cleanly")
    open(path, "w", encoding="utf-8").write(result)
    return len(spans)


if __name__ == "__main__":
    total_files = total_spans = 0
    for p in sys.argv[1:]:
        n = strip(p)
        if n:
            total_files += 1
            total_spans += n
    print(f"{total_spans} comments removed from {total_files} files")
