package main

import (
	"bytes"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/x509"
	"encoding/binary"
	"errors"
	"hash/adler32"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"testing"
)

var packManifest = []byte(`{"schema":"hooklab.module.manifest.v1","module_id":"probe","version":"1.0.0","sdk_min":1,"sdk_max":1,"api":102,"entrypoint":"com.mikeyphw.hooklab.modules.probe.ProbeEntrypoint","hooks":[{"id":"hooklab.probe.marker","package":"com.mikeyphw.hooklab.probetarget","min_version":1,"max_version":1}]}`)
var packProvenance = []byte(`{"schema":"hooklab.pack.provenance.v1","source_sha":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","source_tree":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","run_id":1,"run_attempt":1,"settings_schema":1,"channel":"test"}`)

// A bounded structural DEX fixture only. Real ART coverage is hosted separately.
func testDex(names ...string) []byte {
	n := len(names)
	so, to, co := 112, 112+4*n, 112+8*n
	b := make([]byte, 112+40*n)
	copy(b, []byte("dex\n035\x00"))
	put := func(off, v int) { binary.LittleEndian.PutUint32(b[off:off+4], uint32(v)) }
	put(36, 112)
	put(40, 0x12345678)
	put(56, n)
	put(60, so)
	put(64, n)
	put(68, to)
	put(96, n)
	put(100, co)
	for i, name := range names {
		put(so+4*i, len(b))
		put(to+4*i, i)
		put(co+32*i, i)
		b = append(b, byte(len(name)))
		b = append(b, []byte(name)...)
		b = append(b, 0)
	}
	put(32, len(b))
	h := sha1.Sum(b[32:])
	copy(b[12:32], h[:])
	binary.LittleEndian.PutUint32(b[8:12], adler32.Checksum(b[12:]))
	return b
}
func TestSignedPack(t *testing.T) {
	key, e := rsa.GenerateKey(rand.Reader, 3072)
	if e != nil {
		t.Fatal(e)
	}
	pub, e := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if e != nil {
		t.Fatal(e)
	}
	dex := testDex("Lcom/mikeyphw/hooklab/modules/probe/ProbeEntrypoint;")
	a, e := createPack(packManifest, packProvenance, dex, key)
	if e != nil {
		t.Fatal(e)
	}
	b, e := createPack(packManifest, packProvenance, dex, key)
	if e != nil || !bytes.Equal(a, b) || !packNamesSorted() {
		t.Fatal("not deterministic")
	}
	p, e := verifyPack(a, pub)
	if e != nil || !bytes.Equal(p[dexName], dex) {
		t.Fatal("verification failed", e)
	}
	for _, n := range packNames {
		t.Run(n, func(t *testing.T) {
			v, _ := decodePack(a)
			v[n] = bytes.Clone(v[n])
			v[n][len(v[n])/2] ^= 1
			bad, _ := encodePack(v)
			if _, e := verifyPack(bad, pub); e == nil {
				t.Fatal("tampering accepted")
			}
		})
	}
	for _, cut := range []int{0, 21, len(a) - 1} {
		if _, e := verifyPack(a[:cut], pub); e == nil {
			t.Fatal("truncation accepted")
		}
	}
	if _, e := verifyPack(append(bytes.Clone(a), 0), pub); e == nil {
		t.Fatal("trailing bytes accepted")
	}
	bad := bytes.Clone(a)
	bad[len(bad)-22-46+38] ^= 1
	if _, e := verifyPack(bad, pub); e == nil {
		t.Fatal("central directory mutation accepted")
	}
	for _, d := range [][]byte{testDex("Lcom/mikeyphw/hooklab/sdk/HookSdk;"), testDex("Lcom/mikeyphw/hooklab/modules/probe/Other;"), testDex("Lcom/mikeyphw/hooklab/modules/probe/ProbeEntrypoint;", "Lcom/mikeyphw/hooklab/modules/probe/ProbeEntrypoint;")} {
		if _, e := createPack(packManifest, packProvenance, d, key); e == nil {
			t.Fatal("class inventory accepted")
		}
	}
	if _, e := createPack(append(bytes.Clone(packManifest), []byte(` {}`)...), packProvenance, dex, key); e == nil {
		t.Fatal("trailing metadata accepted")
	}
	wrong, e := rsa.GenerateKey(rand.Reader, 3072)
	if e != nil {
		t.Fatal(e)
	}
	wrongPub, _ := x509.MarshalPKIXPublicKey(&wrong.PublicKey)
	if _, e := verifyPack(a, wrongPub); e == nil {
		t.Fatal("untrusted key accepted")
	}
}

// Exercise the real bounded parser and canonical re-encoder, without Android tooling.
func FuzzDecodePack(f *testing.F) {
	parts := make(map[string][]byte)
	for _, name := range packNames {
		parts[name] = []byte("fixture")
	}
	seed, err := encodePack(parts)
	if err != nil {
		f.Fatal(err)
	}
	f.Add(seed)
	f.Add([]byte{})
	f.Add(seed[:len(seed)-1])
	f.Fuzz(func(t *testing.T, data []byte) {
		decoded, err := decodePack(data)
		if err != nil {
			return
		}
		rebuilt, err := encodePack(decoded)
		if err != nil || !bytes.Equal(data, rebuilt) {
			t.Fatal("noncanonical archive admitted")
		}
	})
}

// Exercise the production publication boundary without relying on filesystem
// support for hard links. A destination (including a dangling symlink) must
// never be overwritten, even when two publishers race.
func TestPublishPackNoReplace(t *testing.T) {
	dir := t.TempDir()
	stage := func(name, value string) string {
		p := filepath.Join(dir, name)
		if err := os.WriteFile(p, []byte(value), 0600); err != nil {
			t.Fatal(err)
		}
		return p
	}
	dest := filepath.Join(dir, "output.hookpack")
	one, two := stage("one", "one"), stage("two", "two")
	var wg sync.WaitGroup
	results := make(chan error, 2)
	for _, source := range []string{one, two} {
		wg.Add(1)
		go func(s string) { defer wg.Done(); results <- publishPackNoReplace(s, dest) }(source)
	}
	wg.Wait()
	close(results)
	successes, conflicts := 0, 0
	for e := range results {
		if e == nil {
			successes++
		} else if errors.Is(e, os.ErrExist) {
			conflicts++
		} else {
			t.Fatal(e)
		}
	}
	if successes != 1 || conflicts != 1 {
		t.Fatalf("publication race: %d successful, %d conflicts", successes, conflicts)
	}
	got, e := os.ReadFile(dest)
	if e != nil || (string(got) != "one" && string(got) != "two") {
		t.Fatal("invalid published bytes", e)
	}
	third := stage("three", "three")
	if e := publishPackNoReplace(third, dest); !errors.Is(e, os.ErrExist) {
		t.Fatal("existing destination not refused", e)
	}
	if b, e := os.ReadFile(dest); e != nil || !bytes.Equal(got, b) {
		t.Fatal("existing destination changed", e)
	}
	if b, e := os.ReadFile(third); e != nil || string(b) != "three" {
		t.Fatal("failed publication lost its stage", e)
	}
	if runtime.GOOS != "windows" {
		dangling := filepath.Join(dir, "dangling")
		if e := os.Symlink("absent", dangling); e != nil {
			t.Fatal(e)
		}
		if e := publishPackNoReplace(third, dangling); !errors.Is(e, os.ErrExist) {
			t.Fatal("dangling symlink not refused", e)
		}
		if target, e := os.Readlink(dangling); e != nil || target != "absent" {
			t.Fatal("symlink replaced", e)
		}
	}
}

func TestPackCommandSafeRefusal(t *testing.T) {
	var out, errout bytes.Buffer
	sensitive := filepath.Join(t.TempDir(), "secret-path-must-not-be-printed")
	code := packCommand([]string{"pack", sensitive, "p", "d", "k", "o"}, &out, &errout)
	if code != 2 || out.Len() != 0 || !strings.Contains(errout.String(), "HOOKPACK_REFUSED stage=read_manifest category=") || strings.Contains(errout.String(), sensitive) {
		t.Fatal("unexpected safe refusal", code, errout.String())
	}
}
