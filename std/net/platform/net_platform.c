#ifdef _WIN32
  #include <winsock2.h>
#else
  #include <unistd.h>
#endif

int netInit(void) {
#ifdef _WIN32
    WSADATA wsa;
    return WSAStartup(MAKEWORD(2, 2), &wsa);
#else
    return 0;
#endif
}

int netCloseSock(int fd) {
#ifdef _WIN32
    return closesocket(fd);
#else
    return close(fd);
#endif
}
