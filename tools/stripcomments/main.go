package main

import (
	"bytes"
	"flag"
	"fmt"
	"go/format"
	"go/scanner"
	"go/token"
	"os"
	"strconv"
	"strings"
)

func directive(lit string) bool {
	for _, p := range []string{"//go:", "// +build", "//export ", "//line ", "//nolint"} {
		if strings.HasPrefix(lit, p) {
			return true
		}
	}
	return false
}

func main() {
	lines := flag.String("lines", "", "comma list of line numbers or a-b ranges; empty = every comment")
	flag.Parse()
	path := flag.Arg(0)
	src, err := os.ReadFile(path)
	if err != nil {
		panic(err)
	}
	only := map[int]bool{}
	for _, part := range strings.Split(*lines, ",") {
		if part == "" {
			continue
		}
		if a, b, ok := strings.Cut(part, "-"); ok {
			x, _ := strconv.Atoi(a)
			y, _ := strconv.Atoi(b)
			for i := x; i <= y; i++ {
				only[i] = true
			}
		} else {
			x, _ := strconv.Atoi(part)
			only[x] = true
		}
	}

	fset := token.NewFileSet()
	file := fset.AddFile(path, -1, len(src))
	var s scanner.Scanner
	s.Init(file, src, nil, scanner.ScanComments)
	type span struct{ start, end int }
	var cut []span
	type pending struct {
		pos token.Pos
		lit string
	}
	var toks []struct {
		pos token.Pos
		tok token.Token
		lit string
	}
	for {
		pos, tok, lit := s.Scan()
		if tok == token.EOF {
			break
		}
		toks = append(toks, struct {
			pos token.Pos
			tok token.Token
			lit string
		}{pos, tok, lit})
	}
	preamble := map[token.Pos]bool{}
	for i, t := range toks {
		if t.tok == token.IMPORT && i+1 < len(toks) && toks[i+1].tok == token.STRING && toks[i+1].lit == "\"C\"" {
			for j := i - 1; j >= 0 && toks[j].tok == token.COMMENT; j-- {
				preamble[toks[j].pos] = true
			}
		}
	}
	_ = pending{}
	for _, t := range toks {
		pos, tok, lit := t.pos, t.tok, t.lit
		if tok != token.COMMENT || directive(lit) || preamble[pos] {
			continue
		}
		p := fset.Position(pos)
		if *lines != "" && !only[p.Line] {
			continue
		}
		start := p.Offset
		end := start + len(lit)
		ls := bytes.LastIndexByte(src[:start], '\n') + 1
		le := bytes.IndexByte(src[end:], '\n')
		if le < 0 {
			le = len(src)
		} else {
			le += end
		}
		before := strings.TrimSpace(string(src[ls:start]))
		after := strings.TrimSpace(string(src[end:le]))
		switch {
		case before == "" && after == "":
			if le < len(src) {
				le++
			}
			cut = append(cut, span{ls, le})
		case before != "":
			ws := start
			for ws > ls && (src[ws-1] == ' ' || src[ws-1] == '\t') {
				ws--
			}
			cut = append(cut, span{ws, end})
		default:
			cut = append(cut, span{start, end})
		}
	}
	var out bytes.Buffer
	last := 0
	for _, c := range cut {
		if c.start < last {
			continue
		}
		out.Write(src[last:c.start])
		last = c.end
	}
	out.Write(src[last:])
	formatted, err := format.Source(out.Bytes())
	if err != nil {
		fmt.Fprintf(os.Stderr, "%s: result does not parse: %v\n", path, err)
		os.Exit(1)
	}
	if err := os.WriteFile(path, formatted, 0o644); err != nil {
		panic(err)
	}
	fmt.Printf("%-60s removed %d comments\n", path, len(cut))
}
