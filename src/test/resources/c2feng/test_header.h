// test_header.h — C2Feng test header
struct Point {
    int x;
    int y;
};

enum Color {
    RED,
    GREEN,
    BLUE = 5,
};

int open(const char *path, int flags);
void close(int fd);
