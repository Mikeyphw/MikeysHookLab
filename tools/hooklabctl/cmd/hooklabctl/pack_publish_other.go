//go:build !linux

package main

import "os"

// Preserve atomic exclusive publication on other supported platforms.
func publishPackNoReplace(source, destination string) error { return os.Link(source, destination) }
