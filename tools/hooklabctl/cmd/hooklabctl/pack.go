package main

// The v1 ZIP profile deliberately uses only STORED entries, fixed headers and
// exact signed byte inventories. The Android verifier is independently implemented.
import (
	"bytes"
	"crypto"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"hash/adler32"
	"hash/crc32"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"unicode"
	"unicode/utf8"
)

const packMax = 9 * 1024 * 1024
const dexMax = 8 * 1024 * 1024
const manifestName = "manifest.json"
const dexName = "runtime/classes.dex"
const provenanceName = "provenance.json"
const digestName = "META-INF/digests.txt"
const signatureName = "META-INF/signature.bin"
const uiDexName = "ui/classes.dex"
const uiResourcesName = "ui/resources.json"
const uiDexMax = 1024 * 1024

var packNames = []string{digestName, signatureName, manifestName, provenanceName, dexName}
var packRefused = errors.New("hookpack refused")

func digestBytes(b []byte) string { h := sha256.Sum256(b); return hex.EncodeToString(h[:]) }
func packRead(path string, limit int64) ([]byte, error) {
	st, e := os.Lstat(path)
	if e != nil || !st.Mode().IsRegular() || st.Size() < 1 || st.Size() > limit {
		return nil, packRefused
	}
	f, e := os.Open(path)
	if e != nil {
		return nil, packRefused
	}
	defer f.Close()
	actual, e := f.Stat()
	if e != nil || !os.SameFile(st, actual) || !actual.Mode().IsRegular() {
		return nil, packRefused
	}
	b, e := io.ReadAll(io.LimitReader(f, limit+1))
	if e != nil || int64(len(b)) != actual.Size() || int64(len(b)) > limit {
		return nil, packRefused
	}
	return b, nil
}
func packKey(b []byte) (*rsa.PublicKey, error) {
	key, e := x509.ParsePKIXPublicKey(b)
	if e != nil {
		return nil, packRefused
	}
	r, ok := key.(*rsa.PublicKey)
	if !ok || r.N.BitLen() != 3072 || r.E != 65537 {
		return nil, packRefused
	}
	canonical, e := x509.MarshalPKIXPublicKey(r)
	if e != nil || !bytes.Equal(canonical, b) {
		return nil, packRefused
	}
	return r, nil
}
func strictPackObject(b []byte) (map[string]any, error) {
	if len(b) > MaxBytes || !utf8.Valid(b) || !validEscapes(b) || !json.Valid(b) {
		return nil, packRefused
	}
	d := json.NewDecoder(bytes.NewReader(b))
	d.UseNumber()
	nodes := 0
	v, e := read(d, 0, &nodes)
	if e != nil {
		return nil, packRefused
	}
	if _, e = d.Token(); e != io.EOF {
		return nil, packRefused
	}
	o, ok := v.(map[string]any)
	if !ok {
		return nil, packRefused
	}
	return o, nil
}
func canonicalManifest(b []byte) ([]byte, error) {
	schema, e := Validate(b)
	if e != nil || schema != "hooklab.module.manifest.v1" {
		return nil, packRefused
	}
	o, e := strictPackObject(b)
	if e != nil {
		return nil, e
	}
	return json.Marshal(o)
}
func canonicalProvenance(b []byte) ([]byte, error) {
	o, e := strictPackObject(b)
	if e != nil || !fields(o, "schema", "source_sha", "source_tree", "run_id", "run_attempt", "settings_schema", "channel") || s(o, "schema") != "hooklab.pack.provenance.v1" || !hash(s(o, "source_sha"), 40) || !hash(s(o, "source_tree"), 40) || n(o, "run_id") < 1 || n(o, "run_attempt") < 1 || n(o, "settings_schema") < 1 || (s(o, "channel") != "personal" && s(o, "channel") != "test") {
		return nil, packRefused
	}
	return json.Marshal(o)
}

// D8 interface desugaring emits -IA/-CC class names; keep package segments strict.
var dexDescriptor = regexp.MustCompile(`^L(?:[A-Za-z0-9_$]+/)+[A-Za-z0-9_$-]+;$`)

func validPackDex(b, manifest []byte) bool { return validPackDexRole(b, manifest, false) }
func validPackDexRole(b, manifest []byte, ui bool) bool {
	if len(b) < 112 || len(b) > dexMax || !bytes.Equal(b[:4], []byte("dex\n")) || b[7] != 0 {
		return false
	}
	switch string(b[4:7]) {
	case "035", "037", "038", "039":
	default:
		return false
	}
	u := func(off int) uint32 { return binary.LittleEndian.Uint32(b[off : off+4]) }
	if u(32) != uint32(len(b)) || u(36) != 112 || u(40) != 0x12345678 {
		return false
	}
	signature := sha1.Sum(b[32:])
	if !bytes.Equal(signature[:], b[12:32]) || adler32.Checksum(b[12:]) != u(8) {
		return false
	}
	table := func(count, offset, width int) (int, int, bool) {
		n, o := uint64(u(count)), uint64(u(offset))
		if n == 0 || n > 65536 || o < 112 || o%4 != 0 || o+n*uint64(width) > uint64(len(b)) {
			return 0, 0, false
		}
		return int(n), int(o), true
	}
	sc, so, ok := table(56, 60, 4)
	if !ok {
		return false
	}
	tc, to, ok := table(64, 68, 4)
	if !ok {
		return false
	}
	cc, co, ok := table(96, 100, 32)
	if !ok {
		return false
	}
	desc := func(off uint32) (string, bool) {
		if off < 112 || uint64(off) >= uint64(len(b)) {
			return "", false
		}
		pos, n, shift := int(off), 0, 0
		for {
			if pos >= len(b) || shift > 21 {
				return "", false
			}
			v := int(b[pos])
			pos++
			n |= (v & 127) << shift
			if v&128 == 0 {
				break
			}
			shift += 7
		}
		if n < 1 || n > 256 || pos+n >= len(b) || b[pos+n] != 0 {
			return "", false
		}
		v := string(b[pos : pos+n])
		return v, dexDescriptor.MatchString(v)
	}
	m, e := strictPackObject(manifest)
	if e != nil {
		return false
	}
	prefix := "Lcom/mikeyphw/hooklab/modules/" + s(m, "module_id") + "/"
	entry := "L" + strings.ReplaceAll(s(m, "entrypoint"), ".", "/") + ";"
	if ui {
		prefix = "Lcom/mikeyphw/hooklab/moduleui/" + s(m, "module_id") + "/"
		entry = prefix + "Provider;"
	}
	names := map[string]bool{}
	for i := 0; i < cc; i++ {
		t := u(co + 32*i)
		if uint64(t) >= uint64(tc) {
			return false
		}
		st := u(to + 4*int(t))
		if uint64(st) >= uint64(sc) {
			return false
		}
		name, ok := desc(u(so + 4*int(st)))
		if !ok || !strings.HasPrefix(name, prefix) || names[name] {
			return false
		}
		names[name] = true
	}
	return names[entry]
}
func signedInventory(parts map[string][]byte, signer string) []byte {
	var out bytes.Buffer
	fmt.Fprintf(&out, "HOOKLAB-PACK-V1\nkey-sha256 %s\n", signer)
	for _, name := range []string{manifestName, provenanceName, dexName, uiDexName, uiResourcesName} {
		if _, ok := parts[name]; !ok {
			continue
		}
		v := parts[name]
		fmt.Fprintf(&out, "%s %d %s\n", digestBytes(v), len(v), name)
	}
	return out.Bytes()
}
func le16(b *bytes.Buffer, v uint16) {
	var a [2]byte
	binary.LittleEndian.PutUint16(a[:], v)
	b.Write(a[:])
}
func le32(b *bytes.Buffer, v uint32) {
	var a [4]byte
	binary.LittleEndian.PutUint32(a[:], v)
	b.Write(a[:])
}
func packLimit(n string) int {
	if n == dexName {
		return dexMax
	}
	if n == uiDexName {
		return uiDexMax
	}
	if n == signatureName {
		return 384
	}
	return MaxBytes
}
func inventoryNames(parts map[string][]byte) ([]string, error) {
	names := append([]string(nil), packNames...)
	for _, name := range []string{uiDexName, uiResourcesName} {
		if _, ok := parts[name]; ok {
			names = append(names, name)
		}
	}
	if len(parts) != len(names) {
		return nil, packRefused
	}
	for _, name := range names {
		if _, ok := parts[name]; !ok {
			return nil, packRefused
		}
	}
	return names, nil
}
func encodePack(parts map[string][]byte) ([]byte, error) {
	names, e := inventoryNames(parts)
	if e != nil {
		return nil, e
	}
	var b, c bytes.Buffer
	for _, n := range names {
		data, ok := parts[n]
		if !ok || len(data) == 0 || len(data) > packLimit(n) {
			return nil, packRefused
		}
		offset, crc, length := uint32(b.Len()), crc32.ChecksumIEEE(data), uint32(len(data))
		le32(&b, 0x04034b50)
		for _, v := range []uint16{20, 0, 0, 0, 33} {
			le16(&b, v)
		}
		for _, v := range []uint32{crc, length, length} {
			le32(&b, v)
		}
		le16(&b, uint16(len(n)))
		le16(&b, 0)
		b.WriteString(n)
		b.Write(data)
		le32(&c, 0x02014b50)
		for _, v := range []uint16{0x0314, 20, 0, 0, 0, 33} {
			le16(&c, v)
		}
		for _, v := range []uint32{crc, length, length} {
			le32(&c, v)
		}
		le16(&c, uint16(len(n)))
		for i := 0; i < 4; i++ {
			le16(&c, 0)
		}
		le32(&c, 0100644<<16)
		le32(&c, offset)
		c.WriteString(n)
	}
	offset, sz := uint32(b.Len()), uint32(c.Len())
	b.Write(c.Bytes())
	le32(&b, 0x06054b50)
	for _, v := range []uint16{0, 0, uint16(len(names)), uint16(len(names))} {
		le16(&b, v)
	}
	le32(&b, sz)
	le32(&b, offset)
	le16(&b, 0)
	if b.Len() > packMax {
		return nil, packRefused
	}
	return b.Bytes(), nil
}

// Independently read the bounded local inventory; rebuilding and comparing the
// complete archive enforces exact central-directory/local identity and modes.
func decodePack(b []byte) (map[string][]byte, error) {
	if len(b) < 22 || len(b) > packMax {
		return nil, packRefused
	}
	parts := map[string][]byte{}
	pos := 0
	names := append([]string(nil), packNames...)
	// The end record is untrusted until the complete archive is reconstructed.
	count := int(binary.LittleEndian.Uint16(b[len(b)-12 : len(b)-10]))
	if count < len(names) || count > len(names)+2 {
		return nil, packRefused
	}
	for i := 0; i < count; i++ {
		n := ""
		if i < len(names) {
			n = names[i]
		} else {
			if len(b)-pos < 30 {
				return nil, packRefused
			}
			ln := int(binary.LittleEndian.Uint16(b[pos+26 : pos+28]))
			if ln > len(b)-pos-30 {
				return nil, packRefused
			}
			n = string(b[pos+30 : pos+30+ln])
			if n != uiDexName && n != uiResourcesName {
				return nil, packRefused
			}
			if _, ok := parts[n]; ok {
				return nil, packRefused
			}
		}
		if len(b)-pos < 30 {
			return nil, packRefused
		}
		h := b[pos : pos+30]
		if binary.LittleEndian.Uint32(h) != 0x04034b50 {
			return nil, packRefused
		}
		ln := int(binary.LittleEndian.Uint16(h[26:28]))
		sz := uint64(binary.LittleEndian.Uint32(h[18:22]))
		if ln != len(n) || sz < 1 || sz > uint64(packLimit(n)) || len(b)-pos-30 < ln {
			return nil, packRefused
		}
		pos += 30
		if string(b[pos:pos+ln]) != n {
			return nil, packRefused
		}
		pos += ln
		if uint64(len(b)-pos) < sz {
			return nil, packRefused
		}
		parts[n] = b[pos : pos+int(sz)]
		pos += int(sz)
	}
	canonical, e := encodePack(parts)
	if e != nil || !bytes.Equal(canonical, b) {
		return nil, packRefused
	}
	return parts, nil
}
func createPack(manifest, provenance, dex []byte, key *rsa.PrivateKey) ([]byte, error) {
	return createPackWithUi(manifest, provenance, dex, key, nil, nil)
}
func createPackWithUi(manifest, provenance, dex []byte, key *rsa.PrivateKey, uiDex, uiResources []byte) ([]byte, error) {
	m, e := canonicalManifest(manifest)
	if e != nil {
		return nil, e
	}
	p, e := canonicalProvenance(provenance)
	if e != nil || !validPackDex(dex, m) {
		return nil, packRefused
	}
	if key == nil || key.Validate() != nil {
		return nil, packRefused
	}
	pub, e := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if e != nil {
		return nil, packRefused
	}
	if _, e = packKey(pub); e != nil {
		return nil, e
	}
	parts := map[string][]byte{manifestName: m, provenanceName: p, dexName: dex}
	if uiDex != nil {
		if len(uiDex) > uiDexMax || !validPackDexRole(uiDex, m, true) {
			return nil, packRefused
		}
		parts[uiDexName] = uiDex
	}
	if uiResources != nil {
		if !validUiResources(uiResources, m) {
			return nil, packRefused
		}
		parts[uiResourcesName] = uiResources
	}
	inv := signedInventory(parts, digestBytes(pub))
	h := sha256.Sum256(inv)
	sig, e := rsa.SignPKCS1v15(nil, key, crypto.SHA256, h[:])
	if e != nil {
		return nil, packRefused
	}
	parts[digestName] = inv
	parts[signatureName] = sig
	return encodePack(parts)
}
func verifyPack(b, pub []byte) (map[string][]byte, error) {
	r, e := packKey(pub)
	if e != nil {
		return nil, e
	}
	parts, e := decodePack(b)
	if e != nil {
		return nil, e
	}
	inv := signedInventory(parts, digestBytes(pub))
	if !bytes.Equal(inv, parts[digestName]) {
		return nil, packRefused
	}
	h := sha256.Sum256(inv)
	if len(parts[signatureName]) != 384 || rsa.VerifyPKCS1v15(r, crypto.SHA256, h[:], parts[signatureName]) != nil {
		return nil, packRefused
	}
	m, e := canonicalManifest(parts[manifestName])
	if e != nil || !bytes.Equal(m, parts[manifestName]) {
		return nil, packRefused
	}
	p, e := canonicalProvenance(parts[provenanceName])
	if e != nil || !bytes.Equal(p, parts[provenanceName]) || !validPackDex(parts[dexName], parts[manifestName]) {
		return nil, packRefused
	}
	if ui, ok := parts[uiDexName]; ok && !validPackDexRole(ui, m, true) {
		return nil, packRefused
	}
	if resources, ok := parts[uiResourcesName]; ok && !validUiResources(resources, m) {
		return nil, packRefused
	}
	return parts, nil
}
func validUiResources(raw, manifest []byte) bool {
	root, e := strictPackObject(raw)
	m, me := strictPackObject(manifest)
	if e != nil || me != nil || !fields(root, "schema", "module_id", "locales", "assets") || s(root, "schema") != "hooklab.module.ui-resources.v1" || s(root, "module_id") != s(m, "module_id") {
		return false
	}
	locales, ok := root["locales"].(map[string]any)
	assets, ak := root["assets"].(map[string]any)
	if !ok || !ak || len(locales) > 16 || len(assets) > 16 {
		return false
	}
	en, ok := locales["en"].(map[string]any)
	if !ok {
		return false
	}
	name := regexp.MustCompile(`^[a-z][a-z0-9_.-]{0,63}$`)
	lang := regexp.MustCompile(`^[a-z]{2,3}(-[A-Z]{2})?$`)
	safeName := func(v string) bool { return name.MatchString(v) && !strings.Contains(v, "..") }
	textOK := func(v string) bool {
		if !utf8.ValidString(v) {
			return false
		}
		for _, r := range v {
			if unicode.IsControl(r) && r != '\n' {
				return false
			}
		}
		return true
	}
	for tag, value := range locales {
		rows, ok := value.(map[string]any)
		if !ok || !lang.MatchString(tag) || len(rows) > 64 {
			return false
		}
		for k, v := range rows {
			label, ok := v.(string)
			if _, exists := en[k]; !exists || !ok || !safeName(k) || len(label) == 0 || len(label) > 512 || !textOK(label) {
				return false
			}
		}
	}
	for k, value := range assets {
		row, ok := value.(map[string]any)
		if !ok || !safeName(k) || !fields(row, "type", "data") {
			return false
		}
		encoded := s(row, "data")
		data, e := base64.StdEncoding.DecodeString(encoded)
		if e != nil || len(data) == 0 || len(data) > 16384 || base64.StdEncoding.EncodeToString(data) != encoded {
			return false
		}
		switch s(row, "type") {
		case "text/plain":
			if !textOK(string(data)) {
				return false
			}
		case "image/png":
			if len(data) < 33 || !bytes.Equal(data[:16], []byte{137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82}) {
				return false
			}
			w, h := binary.BigEndian.Uint32(data[16:20]), binary.BigEndian.Uint32(data[20:24])
			if w == 0 || h == 0 || w > 512 || h > 512 {
				return false
			}
		default:
			return false
		}
	}
	return true
}
func packCommand(args []string, stdout, stderr io.Writer) int {
	fail := func(stage string, err error) int {
		// Deliberately do not print filenames, key material or raw OS errors.
		category := "invalid_or_unavailable"
		switch {
		case errors.Is(err, os.ErrExist):
			category = "already_exists"
		case errors.Is(err, os.ErrPermission):
			category = "permission_denied"
		case errors.Is(err, os.ErrNotExist):
			category = "not_found"
		case errors.Is(err, errors.ErrUnsupported):
			category = "unsupported"
		}
		fmt.Fprintf(stderr, "HOOKPACK_REFUSED stage=%s category=%s\n", stage, category)
		return 2
	}
	if len(args) == 0 {
		return fail("arguments", nil)
	}
	switch args[0] {
	case "pack":
		if len(args) < 6 || len(args) > 10 || (len(args)-6)%2 != 0 {
			return fail("arguments", nil)
		}
		m, e := packRead(args[1], MaxBytes)
		if e != nil {
			return fail("read_manifest", e)
		}
		p, e := packRead(args[2], MaxBytes)
		if e != nil {
			return fail("read_provenance", e)
		}
		d, e := packRead(args[3], dexMax)
		if e != nil {
			return fail("read_dex", e)
		}
		k, e := packRead(args[4], 8192)
		if e != nil {
			return fail("read_private_key", e)
		}
		defer func() {
			for i := range k {
				k[i] = 0
			}
		}()
		st, e := os.Lstat(args[4])
		if e != nil {
			return fail("private_key_permissions", e)
		}
		if !st.Mode().IsRegular() || st.Mode().Perm()&0077 != 0 {
			return fail("private_key_permissions", os.ErrPermission)
		}
		parsed, e := x509.ParsePKCS8PrivateKey(k)
		if e != nil {
			return fail("parse_private_key", e)
		}
		key, ok := parsed.(*rsa.PrivateKey)
		if !ok {
			return fail("private_key_type", nil)
		}
		canonical, e := canonicalManifest(m)
		if e != nil {
			return fail("validate_manifest", e)
		}
		if _, e = canonicalProvenance(p); e != nil {
			return fail("validate_provenance", e)
		}
		if !validPackDex(d, canonical) {
			return fail("validate_dex_profile", packRefused)
		}
		if key.Validate() != nil || key.N.BitLen() != 3072 || key.E != 65537 {
			return fail("validate_private_key", packRefused)
		}
		var uiDex, uiResources []byte
		for i := 6; i < len(args); i += 2 {
			switch args[i] {
			case "--ui-dex":
				if uiDex != nil {
					return fail("arguments", nil)
				}
				uiDex, e = packRead(args[i+1], uiDexMax)
			case "--ui-resources":
				if uiResources != nil {
					return fail("arguments", nil)
				}
				uiResources, e = packRead(args[i+1], MaxBytes)
			default:
				return fail("arguments", nil)
			}
			if e != nil {
				return fail("read_ui", e)
			}
		}
		b, e := createPackWithUi(m, p, d, key, uiDex, uiResources)
		if e != nil {
			return fail("sign_pack", e)
		}
		pub, e := x509.MarshalPKIXPublicKey(&key.PublicKey)
		if e != nil {
			return fail("derive_public_key", e)
		}
		if _, e = verifyPack(b, pub); e != nil {
			return fail("verify_signed_pack", e)
		}
		// Stage privately, sync, and publish atomically without replacing any destination.
		f, e := os.CreateTemp(filepath.Dir(args[5]), ".hookpack-*")
		if e != nil {
			return fail("create_stage", e)
		}
		tmp := f.Name()
		defer os.Remove(tmp)
		if _, e = f.Write(b); e != nil {
			f.Close()
			return fail("write_stage", e)
		}
		if e = f.Sync(); e != nil {
			f.Close()
			return fail("sync_stage", e)
		}
		if e = f.Close(); e != nil {
			return fail("close_stage", e)
		}
		if e = publishPackNoReplace(tmp, args[5]); e != nil {
			return fail("publish", e)
		}
		fmt.Fprintln(stdout, "HOOKPACK_SIGNED", digestBytes(b))
		return 0
	case "verify", "inspect":
		if len(args) != 3 {
			return fail("arguments", nil)
		}
		b, e := packRead(args[1], packMax)
		if e != nil {
			return fail("read_archive", e)
		}
		pub, e := packRead(args[2], 2048)
		if e != nil {
			return fail("read_public_key", e)
		}
		parts, e := verifyPack(b, pub)
		if e != nil {
			return fail("verify_archive", e)
		}
		if args[0] == "inspect" {
			o := map[string]any{"archive_sha256": digestBytes(b), "signer_sha256": digestBytes(pub), "manifest": json.RawMessage(parts[manifestName]), "provenance": json.RawMessage(parts[provenanceName])}
			if ui, ok := parts[uiDexName]; ok {
				o["ui_dex_sha256"] = digestBytes(ui)
			}
			if ui, ok := parts[uiResourcesName]; ok {
				o["ui_resources_sha256"] = digestBytes(ui)
			}
			enc := json.NewEncoder(stdout)
			if enc.Encode(o) != nil {
				return fail("encode_inspection", nil)
			}
		} else {
			fmt.Fprintln(stdout, "HOOKPACK_SIGNATURE_VERIFIED_NOT_ACTIVATED", digestBytes(b))
		}
		return 0
	default:
		return fail("arguments", nil)
	}
}

// Keep the canonical name roster independently checked by tests.
func packNamesSorted() bool { return sort.StringsAreSorted(packNames) }
