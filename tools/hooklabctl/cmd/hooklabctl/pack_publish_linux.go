//go:build linux

package main

import (
	"errors"
	"os"
	"runtime"
	"syscall"
	"unsafe"
)

// Android also satisfies the Linux build tag. Android application SELinux
// domains can deny hard links even in private storage. Use the kernel's atomic
// RENAME_NOREPLACE operation instead of a check-then-rename race.
// Linux UAPI syscall numbers (asm/unistd.h, per architecture); kept local so
// the signing tool retains its dependency-free offline build.
func renameNoReplaceNumber() uintptr {
	switch runtime.GOARCH {
	case "amd64":
		return 316
	case "386":
		return 353
	case "arm":
		return 382
	case "arm64", "riscv64", "loong64":
		return 276
	case "ppc64", "ppc64le":
		return 357
	case "s390x":
		return 347
	case "mips", "mipsle":
		return 4351
	case "mips64", "mips64le":
		return 5311
	default:
		return 0
	}
}

func publishPackNoReplace(source, destination string) error {
	number := renameNoReplaceNumber()
	if number == 0 {
		return errors.ErrUnsupported
	}
	old, err := syscall.BytePtrFromString(source)
	if err != nil {
		return err
	}
	next, err := syscall.BytePtrFromString(destination)
	if err != nil {
		return err
	}
	// AT_FDCWD=-100, RENAME_NOREPLACE=1. The destination must not exist at the
	// instant of publication, including dangling symlinks and competing writers.
	dirfd := -100
	_, _, errno := syscall.Syscall6(number, uintptr(dirfd), uintptr(unsafe.Pointer(old)), uintptr(dirfd), uintptr(unsafe.Pointer(next)), 1, 0)
	runtime.KeepAlive(old)
	runtime.KeepAlive(next)
	if errno == 0 {
		return nil
	}
	// An older kernel/filesystem can lack RENAME_NOREPLACE. A hard link is
	// still atomic and no-clobber on filesystems that permit it. Never fall
	// back to plain rename, partial destination writes or permission changes.
	if errno == syscall.ENOSYS || errno == syscall.EINVAL || errno == syscall.EOPNOTSUPP {
		return os.Link(source, destination)
	}
	return errno
}
