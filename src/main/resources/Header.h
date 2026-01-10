#ifndef FENG_HEADER_H
#define FENG_HEADER_H

#include <atomic>

#include "Primitive.h"

class Header {
public:
    std::atomic_int $refers;
    uint32_t $typeId;
public:
    void $init(uint32_t typeId);
    void $get();
    void $put();
};


#endif //FENG_HEADER_H
