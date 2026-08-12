#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// POSIX-style declarations for cross-platform compatibility
int mkdir(const char *path);
int rmdir(const char *path);
int access(const char *path, int mode);
char *getcwd(char *buf, int size);
int chdir(const char *path);
void *memcpy(void *dest, const void *src, size_t n);
int putenv(const char *envstring);
