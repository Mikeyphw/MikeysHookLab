# MCC-R01-F01 — PR24 hosted CI source-contract alignment

## Verified failure

PR #24, head `dd7a75f4ec2548ab7fd67ec3b4194f0d78a45b15`, required Android CI run `45310534400`, job `138164852607`, failed before Android compilation at `scripts/check_contract.py:197`. The old source guard requires literal `data/databases`, `data/files`, `data/app_flutter`. Production MCC-R01 now safely resolves `$root/databases`, `$root/files`, `$root/app_flutter` under `checkedDataDir(verifiedDir)`. The failure is a stale assertion, not the documented scanner logic.

## Remediation

- Replace the stale source assertion (and reconcile the authoritative roadmap) with the exact new scoped root scanner tokens. Preserve root/path/account checks, no recursive search, no symlink acceptance and manual write verification.
- Run `scripts/check_mcc_r01.py` from hosted source contract before Gradle. Its 11 scenarios cover primary, aliases, missing directories, wrong user, ambiguous files, invalid names, symlinks and invalid formats. Running source-only tests locally is permitted; local Gradle is forbidden.
- No production scanner behavior is changed. No root, UI, version, permissions or clipboard mutation occurs in this fix.

## Independent acceptance

Devtool native check/apply/commit/session on exact PR24 head; existing PR24 fast-forward update only with explicit approval; exact new PR head required hosted CI, CI24 if applicable, Android/LSPosed/root behavior on a device, and release signer acceptance remain independent. A successful source-only guard is not full device acceptance. On unknown remote result inspect GitHub and native receipts before retry; never reapply.
