#ifdef _WIN32
  #include <winsock2.h>
#else
  #include <unistd.h>
  #include <fcntl.h>
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

int netSetNonBlock(int fd) {
#ifdef _WIN32
    u_long mode = 1;
    return ioctlsocket(fd, FIONBIO, &mode);
#else
    return fcntl(fd, F_SETFL, fcntl(fd, F_GETFL, 0) | O_NONBLOCK);
#endif
}
