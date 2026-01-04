#ifndef FENG_OBJECT_H
#define FENG_OBJECT_H

typedef char int8;
typedef unsigned char uint8;
typedef short int16;
typedef unsigned short uint16;
typedef int int32;
typedef unsigned uint32;
typedef long long int64;
typedef unsigned long long uint64;


class Header {
private:
    uint32 $typeId;
public:
    void $init(uint32 typeId);
    void $get();
    void $put();
};

class Object : private Header {
};


#endif //FENG_OBJECT_H
