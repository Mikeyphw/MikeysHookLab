package main

import (
	"bytes"
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
)

const distributionSchema = "hooklab.distribution.index.v1"
const distributionEnvelope = "hooklab.distribution.envelope.v1"
const distributionDomain = "HOOKLAB-DISTRIBUTION-INDEX-V1\n"
const indexPayloadMax = 48000

var repoName = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_][A-Za-z0-9_.-]{0,99}$`)
var releaseCases = []string{"api29-compact", "api29-standard", "api36-360", "api36-390", "api36-393", "api36-600", "api36-840", "api36-compact", "api36-landscape", "api36-tablet-landscape"}
var releaseGates = []string{"source", "android", "ci24", "terminal", "design", "visual", "adaptive", "go"}

func positiveBound(m map[string]any, k string, max int64) bool { return n(m, k) > 0 && n(m, k) <= max }
func indexRows(m map[string]any) ([]any, error) {
	if !fields(m, "schema", "repository", "revision", "issued_at", "expires_at", "modules", "revoked") || s(m, "schema") != distributionSchema || (!repoName.MatchString(s(m, "repository")) || strings.HasSuffix(s(m, "repository"), "/.") || strings.HasSuffix(s(m, "repository"), "/..")) || n(m, "revision") < 1 || n(m, "issued_at") < 1 || n(m, "expires_at") <= n(m, "issued_at") || n(m, "expires_at")-n(m, "issued_at") > 31*86400 {
		return nil, invalid
	}
	revoked, ok := arr(m, "revoked")
	if !ok || len(revoked) > 128 {
		return nil, invalid
	}
	revocations := map[string]bool{}
	digests := map[string]bool{}
	for _, v := range revoked {
		h, ok := v.(string)
		if !ok || !hash(h, 64) || revocations[h] {
			return nil, invalid
		}
		revocations[h] = true
	}
	rows, ok := arr(m, "modules")
	if !ok || len(rows) > 24 {
		return nil, invalid
	}
	ids := map[string]bool{}
	assets := map[int64]bool{}
	for _, v := range rows {
		row, ok := v.(map[string]any)
		if !ok || !fields(row, "manifest", "provenance", "tag", "asset_id", "sha256", "size", "qualification_asset_id", "qualification_sha256", "qualification_size", "workflow_id", "input_sha256", "changelog") {
			return nil, invalid
		}
		mf, ok := row["manifest"].(map[string]any)
		if !ok || !indexManifest(mf) {
			return nil, invalid
		}
		id := s(mf, "module_id") + ":" + s(mf, "version")
		if ids[id] {
			return nil, invalid
		}
		ids[id] = true
		p, ok := row["provenance"].(map[string]any)
		if !ok {
			return nil, invalid
		}
		b, e := json.Marshal(p)
		if e != nil {
			return nil, invalid
		}
		if _, e = canonicalProvenance(b); e != nil || s(p, "channel") != "personal" || !positiveBound(p, "run_attempt", 2147483647) || !positiveBound(p, "settings_schema", 2147483647) {
			return nil, invalid
		}
		h := s(row, "sha256")
		if !hash(h, 64) || digests[h] || !hash(s(row, "qualification_sha256"), 64) || !hash(s(row, "input_sha256"), 64) || !positiveBound(row, "size", packMax) || !positiveBound(row, "qualification_size", MaxBytes) || n(row, "workflow_id") < 1 || s(row, "tag") != "hooklab-module-"+s(mf, "module_id")+"-v"+s(mf, "version") {
			return nil, invalid
		}
		digests[h] = true
		for _, k := range []string{"asset_id", "qualification_asset_id"} {
			a := n(row, k)
			if a < 1 || assets[a] {
				return nil, invalid
			}
			assets[a] = true
		}
		desc, ok := row["changelog"].(string)
		if !ok || len(desc) > 1000 {
			return nil, invalid
		}
		for _, c := range desc {
			if c != '\n' && (c < 32 || c > 126) {
				return nil, invalid
			}
		}
	}
	return rows, nil
}

// Metadata can advertise future SDK versions; signing a pack still uses the
// existing SDK-v1 pack verifier and may never manufacture an unsupported pack.
func indexManifest(m map[string]any) bool {
	if !fields(m, "schema", "module_id", "version", "sdk_min", "sdk_max", "api", "entrypoint", "hooks") || s(m, "schema") != "hooklab.module.manifest.v1" || !modID.MatchString(s(m, "module_id")) || !semver.MatchString(s(m, "version")) || !positiveBound(m, "sdk_min", 2147483647) || !positiveBound(m, "sdk_max", 2147483647) || n(m, "sdk_max") < n(m, "sdk_min") || n(m, "api") != 102 || s(m, "entrypoint") != "com.mikeyphw.hooklab.modules."+s(m, "module_id")+".Entrypoint" {
		return false
	}
	hooks, ok := arr(m, "hooks")
	if !ok || len(hooks) < 1 || len(hooks) > 32 {
		return false
	}
	seen := map[string]bool{}
	for _, item := range hooks {
		h, ok := item.(map[string]any)
		if !ok || !fields(h, "id", "package", "min_version", "max_version") || !ident.MatchString(s(h, "id")) || seen[s(h, "id")] || !pkg.MatchString(s(h, "package")) || n(h, "min_version") < 1 || n(h, "max_version") < n(h, "min_version") {
			return false
		}
		seen[s(h, "id")] = true
	}
	return true
}
func checkReleaseReceipt(b []byte, repo string, p map[string]any) error {
	q, e := strictPackObject(b)
	if e != nil || s(q, "schema") != "hooklab.qualification.receipt.v1" || s(q, "repository") != repo || s(q, "overall") != "PASS_TARGETED_HOSTED" || q["physical_device_qualified"] != false || q["release_qualified"] != false || q["visual_approved"] != false {
		return invalid
	}
	for _, k := range []string{"source_sha", "source_tree"} {
		if s(q, k) != s(p, k) {
			return invalid
		}
	}
	for _, k := range []string{"run_id", "run_attempt"} {
		if s(q, k) != strconv.FormatInt(n(p, k), 10) {
			return invalid
		}
	}
	gates, ok := q["gates"].(map[string]any)
	if !ok || len(gates) != 9 {
		return invalid
	}
	for _, k := range releaseGates {
		g, ok := gates[k].(map[string]any)
		if !ok || s(g, "verdict") != "PASS" || s(g, "job_result") != "success" {
			return invalid
		}
	}
	f, ok := gates["functional"].(map[string]any)
	if !ok || s(f, "verdict") != "NOT_APPLICABLE" || s(f, "job_result") != "skipped" {
		return invalid
	}
	cases, ok := arr(q, "adaptive_cases")
	if !ok || len(cases) != 10 {
		return invalid
	}
	seen := map[string]bool{}
	for _, v := range cases {
		c, ok := v.(string)
		if !ok || seen[c] {
			return invalid
		}
		seen[c] = true
	}
	for _, c := range releaseCases {
		if !seen[c] {
			return invalid
		}
	}
	return nil
}
func verifyIndexInputs(m map[string]any, rows []any, pub []byte, dir string) error {
	for _, v := range rows {
		row := v.(map[string]any)
		p := row["provenance"].(map[string]any)
		h := s(row, "sha256")
		b, e := packRead(filepath.Join(dir, h+".hookpack"), packMax)
		if e != nil || digestBytes(b) != h || int64(len(b)) != n(row, "size") {
			return invalid
		}
		parts, e := verifyPack(b, pub)
		if e != nil {
			return e
		}
		mfb, _ := json.Marshal(row["manifest"])
		prb, _ := json.Marshal(p)
		if !bytes.Equal(mfb, parts[manifestName]) || !bytes.Equal(prb, parts[provenanceName]) {
			return invalid
		}
		qh := s(row, "qualification_sha256")
		q, e := packRead(filepath.Join(dir, qh+".json"), MaxBytes)
		if e != nil || int64(len(q)) != n(row, "qualification_size") || digestBytes(q) != qh || checkReleaseReceipt(q, s(m, "repository"), p) != nil {
			return invalid
		}
	}
	return nil
}
func strictB64(s string, max int) ([]byte, error) {
	b, e := base64.StdEncoding.DecodeString(s)
	if e != nil || len(b) == 0 || len(b) > max || base64.StdEncoding.EncodeToString(b) != s {
		return nil, invalid
	}
	return b, nil
}
func indexVerify(b, pub []byte) (map[string]any, error) {
	key, e := packKey(pub)
	if e != nil {
		return nil, e
	}
	m, e := strictPackObject(b)
	if e != nil || !fields(m, "schema", "payload", "signature", "signer_sha256") || s(m, "schema") != distributionEnvelope || s(m, "signer_sha256") != digestBytes(pub) {
		return nil, invalid
	}
	payload, e := strictB64(s(m, "payload"), indexPayloadMax)
	if e != nil {
		return nil, e
	}
	signature, e := strictB64(s(m, "signature"), 384)
	if e != nil || len(signature) != 384 {
		return nil, invalid
	}
	h := sha256.Sum256(append([]byte(distributionDomain), payload...))
	if rsa.VerifyPKCS1v15(key, crypto.SHA256, h[:], signature) != nil {
		return nil, invalid
	}
	data, e := strictPackObject(payload)
	if e != nil {
		return nil, e
	}
	if _, e = indexRows(data); e != nil {
		return nil, e
	}
	return data, nil
}
func indexSign(payload []byte, key *rsa.PrivateKey, dir string) ([]byte, error) {
	if key == nil || key.Validate() != nil || key.N.BitLen() != 3072 || key.E != 65537 {
		return nil, invalid
	}
	m, e := strictPackObject(payload)
	if e != nil {
		return nil, e
	}
	rows, e := indexRows(m)
	if e != nil {
		return nil, e
	}
	pub, e := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if e != nil {
		return nil, e
	}
	if e = verifyIndexInputs(m, rows, pub, dir); e != nil {
		return nil, e
	}
	p, e := json.Marshal(m)
	if e != nil || len(p) > indexPayloadMax {
		return nil, invalid
	}
	h := sha256.Sum256(append([]byte(distributionDomain), p...))
	sig, e := rsa.SignPKCS1v15(nil, key, crypto.SHA256, h[:])
	if e != nil {
		return nil, e
	}
	b, e := json.Marshal(map[string]any{"schema": distributionEnvelope, "payload": base64.StdEncoding.EncodeToString(p), "signature": base64.StdEncoding.EncodeToString(sig), "signer_sha256": digestBytes(pub)})
	if e != nil || len(b) > MaxBytes {
		return nil, invalid
	}
	if _, e = indexVerify(b, pub); e != nil {
		return nil, e
	}
	return b, nil
}
func distributionCommand(args []string, stdout, stderr io.Writer) int {
	refuse := func(stage string) int { fmt.Fprintln(stderr, "DISTRIBUTION_REFUSED stage="+stage); return 2 }
	if len(args) == 0 {
		return refuse("arguments")
	}
	switch args[0] {
	case "sign": // sign <payload.json> <private.der> <verified-input-dir> <new-index.json>
		if len(args) != 5 {
			return refuse("arguments")
		}
		p, e := packRead(args[1], indexPayloadMax)
		if e != nil {
			return refuse("read_payload")
		}
		st, e := os.Lstat(args[2])
		if e != nil || !st.Mode().IsRegular() || st.Mode().Perm()&0077 != 0 {
			return refuse("private_key_permissions")
		}
		kb, e := packRead(args[2], 8192)
		if e != nil {
			return refuse("read_key")
		}
		defer func() {
			for i := range kb {
				kb[i] = 0
			}
		}()
		raw, e := x509.ParsePKCS8PrivateKey(kb)
		key, ok := raw.(*rsa.PrivateKey)
		if e != nil || !ok {
			return refuse("parse_key")
		}
		b, e := indexSign(p, key, args[3])
		if e != nil {
			return refuse("verify_inputs_or_sign")
		}
		f, e := os.CreateTemp(filepath.Dir(args[4]), ".index-*")
		if e != nil {
			return refuse("stage")
		}
		tmp := f.Name()
		defer os.Remove(tmp)
		if _, e = f.Write(b); e == nil {
			e = f.Sync()
		}
		c := f.Close()
		if e != nil || c != nil {
			return refuse("stage")
		}
		if e = publishPackNoReplace(tmp, args[4]); e != nil {
			return refuse("publish")
		}
		fmt.Fprintln(stdout, "DISTRIBUTION_INDEX_SIGNED", digestBytes(b))
		return 0
	case "verify", "inspect":
		if len(args) != 3 {
			return refuse("arguments")
		}
		b, e := packRead(args[1], MaxBytes)
		if e != nil {
			return refuse("read_index")
		}
		pub, e := packRead(args[2], 2048)
		if e != nil {
			return refuse("read_key")
		}
		m, e := indexVerify(b, pub)
		if e != nil {
			return refuse("verify_index")
		}
		if args[0] == "inspect" {
			if e = json.NewEncoder(stdout).Encode(m); e != nil {
				return refuse("write")
			}
		} else {
			fmt.Fprintln(stdout, "DISTRIBUTION_INDEX_VERIFIED_NOT_ACTIVATED", digestBytes(b))
		}
		return 0
	}
	return refuse("arguments")
}
