#ifndef FENG_MEM_H
#define FENG_MEM_H

#include "Primitive.h"
#include "Header.h"

class Mem : public Header {
public:
	uint64_t size;
	uint8_t buffer[];
};

#endif //FENG_MEM_H
