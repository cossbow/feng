#include <stdint.h>
#include <stdio.h>
#include <string.h>

// stdio functions used by File class — kept in std/os directly
// because they are simple C library calls with no platform differences.
// Platform-specific functions (feng_stdin, mkdir, getenv, etc.)
// are in std/os/platform instead.
