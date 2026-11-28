package main

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"regexp"
	"strconv"
	"unicode/utf8"
)

const MaxBytes = 65536

var invalid = errors.New("invalid module metadata contract")
var ident = regexp.MustCompile(`^[a-z][a-z0-9_.-]{0,127}$`)
var modID = regexp.MustCompile(`^[a-z][a-z0-9_]{0,63}$`)
var pkg = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$`)
var semver = regexp.MustCompile(`^(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})$`)
var intPattern = regexp.MustCompile(`^-?(0|[1-9][0-9]*)$`)

// encoding/json replaces invalid UTF-16 escape pairs. Reject those explicitly
// before tokenization, while leaving valid U+FFFD itself acceptable.
func validEscapes(b []byte) bool {
	inString := false
	for i := 0; i < len(b); i++ {
		if b[i] == '"' {
			inString = !inString
			continue
		}
		if !inString || b[i] != '\\' {
			continue
		}
		i++
		if i >= len(b) {
			return false
		}
		if b[i] != 'u' {
			continue
		}
		if i+4 >= len(b) {
			return false
		}
		v, e := strconv.ParseUint(string(b[i+1:i+5]), 16, 16)
		if e != nil {
			return false
		}
		i += 4
		if v >= 0xd800 && v <= 0xdbff {
			if i+6 >= len(b) || b[i+1] != '\\' || b[i+2] != 'u' {
				return false
			}
			w, e := strconv.ParseUint(string(b[i+3:i+7]), 16, 16)
			if e != nil || w < 0xdc00 || w > 0xdfff {
				return false
			}
			i += 6
		} else if v >= 0xdc00 && v <= 0xdfff {
			return false
		}
	}
	return true
}
func read(d *json.Decoder, depth int, nodes *int) (any, error) {
	*nodes++
	if depth > 16 || *nodes > 4096 {
		return nil, invalid
	}
	t, e := d.Token()
	if e != nil {
		return nil, invalid
	}
	switch v := t.(type) {
	case json.Delim:
		switch v {
		case '{':
			m := map[string]any{}
			for d.More() {
				k, e := d.Token()
				if e != nil {
					return nil, invalid
				}
				key, ok := k.(string)
				if !ok {
					return nil, invalid
				}
				if _, ok = m[key]; ok {
					return nil, invalid
				}
				item, e := read(d, depth+1, nodes)
				if e != nil {
					return nil, e
				}
				m[key] = item
			}
			end, e := d.Token()
			if e != nil || end != json.Delim('}') {
				return nil, invalid
			}
			return m, nil
		case '[':
			a := []any{}
			for d.More() {
				item, e := read(d, depth+1, nodes)
				if e != nil {
					return nil, e
				}
				a = append(a, item)
			}
			end, e := d.Token()
			if e != nil || end != json.Delim(']') {
				return nil, invalid
			}
			return a, nil
		default:
			return nil, invalid
		}
	case json.Number:
		if !intPattern.MatchString(string(v)) {
			return nil, invalid
		}
		n, e := strconv.ParseInt(string(v), 10, 64)
		if e != nil {
			return nil, invalid
		}
		return n, nil
	case string, bool, nil:
		return v, nil
	default:
		return nil, invalid
	}
}
func fields(o map[string]any, keys ...string) bool {
	if len(o) != len(keys) {
		return false
	}
	for _, k := range keys {
		if _, ok := o[k]; !ok {
			return false
		}
	}
	return true
}
func s(o map[string]any, k string) string { v, _ := o[k].(string); return v }
func n(o map[string]any, k string) int64  { v, _ := o[k].(int64); return v }
func hash(s string, l int) bool {
	if len(s) != l {
		return false
	}
	for _, c := range s {
		if !(c >= 'a' && c <= 'f' || c >= '0' && c <= '9') {
			return false
		}
	}
	return true
}
func arr(o map[string]any, k string) ([]any, bool) { v, ok := o[k].([]any); return v, ok }
func manifest(o map[string]any) bool {
	if !fields(o, "schema", "module_id", "version", "sdk_min", "sdk_max", "api", "entrypoint", "hooks") || !modID.MatchString(s(o, "module_id")) || !semver.MatchString(s(o, "version")) || n(o, "sdk_min") != 1 || n(o, "sdk_max") < 1 || n(o, "sdk_max") > 2147483647 || n(o, "api") != 102 {
		return false
	}
	pattern := `^com\.mikeyphw\.hooklab\.modules\.` + s(o, "module_id") + `\.[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*$`
	if !regexp.MustCompile(pattern).MatchString(s(o, "entrypoint")) {
		return false
	}
	a, ok := arr(o, "hooks")
	if !ok || len(a) == 0 {
		return false
	}
	seen := map[string]bool{}
	for _, raw := range a {
		h, ok := raw.(map[string]any)
		if !ok || !fields(h, "id", "package", "min_version", "max_version") || !ident.MatchString(s(h, "id")) || !pkg.MatchString(s(h, "package")) || n(h, "min_version") < 1 || n(h, "max_version") < n(h, "min_version") || seen[s(h, "id")] {
			return false
		}
		seen[s(h, "id")] = true
	}
	return true
}
func settings(o map[string]any) bool {
	if !fields(o, "schema", "module_id", "generation", "flags") || !ident.MatchString(s(o, "module_id")) || !hash(s(o, "generation"), 64) {
		return false
	}
	m, ok := o["flags"].(map[string]any)
	if !ok {
		return false
	}
	for k, v := range m {
		_, ok := v.(bool)
		if !ok || !ident.MatchString(k) {
			return false
		}
	}
	return true
}
func index(o map[string]any) bool {
	if !fields(o, "schema", "revision", "modules") || n(o, "revision") < 1 {
		return false
	}
	a, ok := arr(o, "modules")
	if !ok {
		return false
	}
	seen := map[string]bool{}
	for _, raw := range a {
		m, ok := raw.(map[string]any)
		if !ok || !fields(m, "module_id", "version", "sha256", "size") || !ident.MatchString(s(m, "module_id")) || !semver.MatchString(s(m, "version")) || !hash(s(m, "sha256"), 64) || n(m, "size") < 1 {
			return false
		}
		id := s(m, "module_id") + ":" + s(m, "version")
		if seen[id] {
			return false
		}
		seen[id] = true
	}
	return true
}
func receipt(o map[string]any) bool {
	if !fields(o, "schema", "module_id", "version", "source_sha", "source_tree", "sha256", "state") || !ident.MatchString(s(o, "module_id")) || !semver.MatchString(s(o, "version")) || !hash(s(o, "source_sha"), 40) || !hash(s(o, "source_tree"), 40) || !hash(s(o, "sha256"), 64) {
		return false
	}
	switch s(o, "state") {
	case "STAGED", "VERIFIED", "READY", "ACTIVE", "FAILED", "ROLLED_BACK":
		return true
	}
	return false
}
func Validate(b []byte) (string, error) {
	if len(b) > MaxBytes || !utf8.Valid(b) || !validEscapes(b) {
		return "", invalid
	}
	d := json.NewDecoder(bytes.NewReader(b))
	d.UseNumber()
	nodes := 0
	v, e := read(d, 0, &nodes)
	if e != nil {
		return "", e
	}
	if _, e = d.Token(); e != io.EOF {
		return "", invalid
	}
	o, ok := v.(map[string]any)
	if !ok {
		return "", invalid
	}
	schema := s(o, "schema")
	var good bool
	switch schema {
	case "hooklab.module.manifest.v1":
		good = manifest(o)
	case "hooklab.settings.v1":
		good = settings(o)
	case "hooklab.index.v1":
		good = index(o)
	case "hooklab.module.receipt.v1":
		good = receipt(o)
	}
	if !good {
		return "", fmt.Errorf("%w", invalid)
	}
	return schema, nil
}

func run(args []string, stdout, stderr io.Writer) int {
	if len(args) > 0 && args[0] == "index" {
		return distributionCommand(args[1:], stdout, stderr)
	}
	if len(args) > 0 && args[0] == "module" {
		return packCommand(args[1:], stdout, stderr)
	}
	if len(args) == 1 && args[0] == "version" {
		fmt.Fprintln(stdout, "hooklabctl metadata-v1")
		return 0
	}
	if len(args) != 2 || args[0] != "inspect" {
		fmt.Fprintln(stderr, "usage: hooklabctl version | inspect <metadata.json> | index sign|verify|inspect | module pack <manifest> <provenance> <dex> <private-key.der> <new.hookpack> [--ui-dex <dex>] [--ui-resources <json>] | module verify|inspect <pack> <trusted-public-key.der>")
		return 2
	}
	before, e := os.Lstat(args[1])
	if e != nil || !before.Mode().IsRegular() || before.Size() > MaxBytes {
		fmt.Fprintln(stderr, "METADATA_REFUSED")
		return 2
	}
	f, e := os.Open(args[1])
	if e != nil {
		fmt.Fprintln(stderr, "METADATA_REFUSED")
		return 2
	}
	defer f.Close()
	stat, e := f.Stat()
	if e != nil || !stat.Mode().IsRegular() || !os.SameFile(before, stat) || stat.Size() > MaxBytes {
		fmt.Fprintln(stderr, "METADATA_REFUSED")
		return 2
	}
	b, e := io.ReadAll(io.LimitReader(f, MaxBytes+1))
	if e != nil {
		fmt.Fprintln(stderr, "METADATA_REFUSED")
		return 2
	}
	schema, e := Validate(b)
	if e != nil {
		fmt.Fprintln(stderr, "METADATA_REFUSED")
		return 2
	}
	fmt.Fprintln(stdout, "VALID_METADATA_NOT_ACCEPTANCE", schema)
	return 0
}
func main() { os.Exit(run(os.Args[1:], os.Stdout, os.Stderr)) }
