// platform abstraction (see platform/ sub-module)


// socket
int socket(int domain, int type, int protocol);
int setsockopt(int sockfd, int level, int optname, const void *optval, int optlen);
int bind(int sockfd, const void *addr, int addrlen);
int listen(int sockfd, int backlog);
int accept(int sockfd, void *addr, int *addrlen);
int connect(int sockfd, const void *addr, int addrlen);

// I/O
int recv(int sockfd, void *buf, int len, int flags);
int send(int sockfd, const void *buf, int len, int flags);
int recvfrom(int sockfd, void *buf, int len, int flags, void *src_addr, int *addrlen);
int sendto(int sockfd, const void *buf, int len, int flags, const void *dest_addr, int addrlen);

// close
int close(int fd);

// byte order
unsigned short htons(unsigned short hostshort);
unsigned long htonl(unsigned long hostlong);
unsigned short ntohs(unsigned short netshort);
unsigned long ntohl(unsigned long netlong);

// address conversion
int inet_pton(int af, const char *src, void *dst);
const char *inet_ntop(int af, const void *src, char *dst, int size);
