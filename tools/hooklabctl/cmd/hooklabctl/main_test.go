package main

import (
	"bytes"
	"encoding/base64"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestCLI(t *testing.T) {
	d := t.TempDir()
	valid := filepath.Join(d, "valid.json")
	if e := os.WriteFile(valid, []byte(`{"schema":"hooklab.index.v1","revision":1,"modules":[]}`), 0600); e != nil {
		t.Fatal(e)
	}
	invalid := filepath.Join(d, "invalid.json")
	if e := os.WriteFile(invalid, []byte(`{}`), 0600); e != nil {
		t.Fatal(e)
	}
	link := filepath.Join(d, "link")
	if e := os.Symlink(valid, link); e != nil {
		t.Fatal(e)
	}
	for _, row := range []struct {
		args   []string
		code   int
		output string
	}{{[]string{"version"}, 0, "metadata-v1"}, {[]string{"inspect", valid}, 0, "VALID_METADATA_NOT_ACCEPTANCE"}, {[]string{"inspect", invalid}, 2, "METADATA_REFUSED"}, {[]string{"inspect", link}, 2, "METADATA_REFUSED"}, {[]string{"inspect", d}, 2, "METADATA_REFUSED"}, {[]string{"install", valid}, 2, "usage"}} {
		var out, err bytes.Buffer
		if code := run(row.args, &out, &err); code != row.code || !bytes.Contains(append(out.Bytes(), err.Bytes()...), []byte(row.output)) {
			t.Fatalf("wrong response %v %d", row.args, code)
		}
	}
}

func TestSharedVectors(t *testing.T) {
	b, e := os.ReadFile(filepath.Join("..", "..", "..", "..", "contracts", "vectors", "corpus.tsv"))
	if e != nil {
		t.Fatal(e)
	}
	lines := strings.Split(strings.TrimSpace(string(b)), "\n")
	if len(lines) != 33 {
		t.Fatal("vector roster", len(lines))
	}
	for _, line := range lines {
		parts := strings.Split(line, "\t")
		if len(parts) != 3 {
			t.Fatal("vector row")
		}
		t.Run(parts[0]+"/"+parts[1], func(t *testing.T) {
			raw, e := base64.StdEncoding.DecodeString(parts[2])
			if e != nil {
				t.Fatal(e)
			}
			_, e = Validate(raw)
			if (e == nil) != (parts[0] == "valid") {
				t.Fatalf("unexpected verdict: %v", e)
			}
		})
	}
}
func FuzzValidate(f *testing.F) {
	f.Add([]byte(`{"schema":"hooklab.index.v1","revision":1,"modules":[]}`))
	f.Add([]byte(`{"schema":"hooklab.index.v1","revision":1,"revision":2,"modules":[]}`))
	f.Fuzz(func(t *testing.T, b []byte) {
		a, e := Validate(b)
		c, d := Validate(b)
		if a != c || (e == nil) != (d == nil) {
			t.Fatal("nondeterministic validation")
		}
	})
}
