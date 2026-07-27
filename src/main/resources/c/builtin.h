#ifndef FENG_BUILTINS_H
#define FENG_BUILTINS_H

#include "Header.h"

// ===== array typedefs for built-in interface signatures =====
// (guards match the generator's per-type array typedefs)
#ifndef FENG_TYPEDEF_ArraySRef_Byte
#define FENG_TYPEDEF_ArraySRef_Byte
typedef struct { Byte* $values; Int64 $length; } Feng$ArraySRef_Byte;
#endif
#ifndef FENG_TYPEDEF_ArrayPRef_Byte
#define FENG_TYPEDEF_ArrayPRef_Byte
typedef struct { Byte* $values; Int64 $length; } Feng$ArrayPRef_Byte;
#endif

// ===== Object: the root class =====
typedef struct $Object {
    const Feng$Meta* $meta;
} $Object;

extern const Feng$Meta Feng$meta_$Object;

static inline void* Feng$newObject(int64_t size, const Feng$Meta* meta) {
    void* _p = Feng$alloc(size);
    (($Object*)_p)->$meta = meta;
    return _p;
}

// ===== built-in interfaces =====

#ifndef FENG_STRUCT_Feng_Meta_Writer
#define FENG_STRUCT_Feng_Meta_Writer
typedef struct Feng$Meta_$Writer {
    Feng$Meta base;
    Int (*$write)(void* self, Feng$ArrayPRef_Byte, Int, Int);
} Feng$Meta_$Writer;
#endif
extern const Feng$Meta_$Writer Feng$meta_$Writer;

#ifndef FENG_STRUCT_Feng_Meta_Writable
#define FENG_STRUCT_Feng_Meta_Writable
typedef struct Feng$Meta_$Writable {
    Feng$Meta base;
    Int (*$write)(void* self, void*);
} Feng$Meta_$Writable;
#endif
extern const Feng$Meta_$Writable Feng$meta_$Writable;

#ifndef FENG_STRUCT_Feng_Meta_Reader
#define FENG_STRUCT_Feng_Meta_Reader
typedef struct Feng$Meta_$Reader {
    Feng$Meta base;
    Int (*$read)(void* self, Feng$ArrayPRef_Byte);
} Feng$Meta_$Reader;
#endif
extern const Feng$Meta_$Reader Feng$meta_$Reader;

// ===== built-in function prototypes =====

Int $intToStr(Int n, Feng$ArrayPRef_Byte buf);
Int $floatToStr(Float64 n, Feng$ArrayPRef_Byte buf);

#endif
