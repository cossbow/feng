#ifndef FENG_C_HEADER_H
#define FENG_C_HEADER_H

#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <stdio.h>
#include <math.h>

// ===== base types =====
typedef uint8_t  Byte;
typedef int64_t  Int;
typedef int8_t   Int8;
typedef int16_t  Int16;
typedef int32_t  Int32;
typedef int64_t  Int64;
typedef uint64_t Uint;
typedef uint8_t  Uint8;
typedef uint16_t Uint16;
typedef uint32_t Uint32;
typedef uint64_t Uint64;
typedef double   Float;
typedef float    Float32;
typedef double   Float64;
typedef bool     Bool;

// ===== reference counting allocation =====
typedef struct Feng$Header {
    struct Feng$Header* next;   // debug: linked list
    atomic_int refcnt;
} Feng$Header;

#ifdef FENG_DEBUG_MEMORY
static Feng$Header* Feng$debug_list = NULL;
#include <stdio.h>
#endif

static inline void* Feng$alloc(int64_t size) {
    void* p = malloc(sizeof(Feng$Header) + size);
    if (!p) abort();
    Feng$Header* fh = (Feng$Header*) p;
    atomic_init(&fh->refcnt, 1);
#ifdef FENG_DEBUG_MEMORY
    fh->next = Feng$debug_list;
    Feng$debug_list = fh;
#endif
    void* o = ((uint8_t*) p) + sizeof(Feng$Header);
    memset(o, 0, size);
    return o;
}

static inline Feng$Header* Feng$headerOf(void* p) {
    return (Feng$Header*) (((uint8_t*) p) - sizeof(Feng$Header));
}

static inline void Feng$free(void* p) {
    if (!p) return;
#ifndef FENG_DEBUG_MEMORY
    free(Feng$headerOf(p));
#endif
}

static inline void* Feng$inc(void* p) {
    if (!p) return p;
    Feng$Header* fh = Feng$headerOf(p);
    int ref = atomic_fetch_add(&fh->refcnt, 1);
    if (ref < 1) abort();
    return p;
}

// return true if refcnt reaches 0 (caller should release)
static inline bool Feng$dec(void* p) {
    if (!p) return false;
    Feng$Header* fh = Feng$headerOf(p);
    int ref = atomic_fetch_sub(&fh->refcnt, 1) - 1;
    if (ref == 0) return true;
#ifdef FENG_DEBUG_MEMORY
    if (ref < 0) false;
#else
    if (ref < 0) abort();
#endif
    return false;
}

// ===== mappable cast =====
#define Feng$cast(ptr, type) ((type*)(void*)(ptr))

// ===== array built-in methods =====
// swap a.$values[i] and a.$values[j]
#define FENG$SWAP(a, i, j) do { \
    __typeof__((a).$values[0]) _t = (a).$values[i]; \
    (a).$values[i] = (a).$values[j]; \
    (a).$values[j] = _t; } while (0)
// move a.$values[i] to cover a.$values[j]; source slot is zeroed
#define FENG$MOVE(a, i, j) do { \
    (a).$values[j] = (a).$values[i]; \
    (a).$values[i] = (__typeof__((a).$values[0])){0}; } while (0)

// ===== math =====
static inline Int64 Feng$fastPow(Int64 a, Int64 b) {
    Int64 r = 1;
    while (b) {
        if (b & 1) r *= a;
        a *= a;
        b >>= 1;
    }
    return r;
}

static inline Float64 Feng$fastPowF(Float64 a, Int64 b) {
    Float64 r = 1.0;
    Int64 exp = b < 0 ? -b : b;
    while (exp) {
        if (exp & 1) r *= a;
        a *= a;
        exp >>= 1;
    }
    return b < 0 ? 1.0 / r : r;
}

// ===== RAII cleanup via __attribute__((cleanup)) =====
#define FENG$DEC(c) __attribute__((__cleanup__(c)))

// generic cleanup for strong references (*T)
static inline void Feng$cleanup_sref(void* p) {
    void** pp = (void**) p;
    if (*pp && Feng$dec(*pp)) Feng$free(*pp);
}

// ===== phantom array reference =====
typedef struct {
    void* $values;
    Int64 $length;
} Feng$ArrayPRef;

// ===== enum meta =====
typedef struct {
    Int $value;
    Feng$ArrayPRef $name;
} Feng$Enum;

// ===== OOP: metadata & virtual dispatch =====
typedef struct Feng$Meta Feng$Meta;

typedef struct Feng$IfaceEntry {
    const Feng$Meta* type;
    Int32            offset;
} Feng$IfaceEntry;

struct Feng$Meta {
    Int32                   instance_size;
    const Feng$Meta*        super;
    Int32                   iface_count;
    const Feng$IfaceEntry*  ifaces;
    void (*destroy)(void* self);
};

// RTTI: check if meta matches target type (walk super chain, pointer compare)
static inline bool Feng$is_kind(const Feng$Meta* meta, const Feng$Meta* target) {
    for (; meta; meta = meta->super)
        if (meta == target) return true;
    return false;
}

// find interface vtable offset within metadata
static inline void* Feng$iface_vtable(const Feng$Meta* meta, const Feng$Meta* iface) {
    for (Int32 i = 0; i < meta->iface_count; i++)
        if (meta->ifaces[i].type == iface)
            return (char*)meta + meta->ifaces[i].offset;
    return NULL;
}

// ===== exception handling via setjmp/longjmp =====
#include <setjmp.h>

typedef struct Feng$ExFrame {
    jmp_buf               buf;
    struct Feng$ExFrame*  prev;
    void*                 exception;
    int                   state;       // 0=normal, 1=caught, 2=unhandled
} Feng$ExFrame;

extern _Thread_local Feng$ExFrame* Feng$ex_top;

// throw an exception (NORETURN), takes ownership of ex (strong ref)
_Noreturn void Feng$throw(void* ex);

// get the object meta pointer (first field of every feng object)
static inline const struct Feng$Meta* Feng$objMeta(void* p) {
    return *(const struct Feng$Meta**)p;
}

// runtime exception types
typedef struct $Exception {
    const Feng$Meta* $meta;
    Uint64 fn;
    Uint32 line;
} $Exception;

typedef struct $NilException {
    const Feng$Meta* $meta;
    Uint64 fn;
    Uint32 line;
} $NilException;

typedef struct $OutOfBoundsException {
    const Feng$Meta* $meta;
    Uint64 fn;
    Uint32 line;
} $OutOfBoundsException;

// exception throw helpers (carry fn/line for stack trace)
void Feng$throwNullPointer(Uint64 fn, Uint32 line);
void Feng$throwIndexOutOfBounds(Uint64 fn, Uint32 line);

// Base trace implementation — called by generated throw-statement code;
// subclasses may override $Exception$trace for additional behavior.
static inline void $Exception$trace(void* self, Uint64 fn, Uint32 line) {
    ((struct $Exception*)self)->fn = fn;
    ((struct $Exception*)self)->line = line;
}

// ===== runtime checks =====
// Note: Feng$required and Feng$checkIndex are kept as macros so that
// the caller site's __LINE__ and label address can be captured.
// They should be called with (ptr, &&_feng_fn_label, __LINE__) from generated code.
static inline void Feng$required(void* p, Uint64 fn, Uint32 line) {
    if (!p) Feng$throwNullPointer(fn, line);
}

static inline int64_t Feng$checkIndex(int64_t i, int64_t bound, Uint64 fn, Uint32 line) {
    if (0 <= i && i < bound) return i;
    Feng$throwIndexOutOfBounds(fn, line);
    return -1;
}

// ===== debug =====
#ifdef FENG_DEBUG_MEMORY
static void feng$debug(bool all) {
    printf("==== see memory stat ====\n");
    for (Feng$Header* h = Feng$debug_list; h; h = h->next) {
        int c = atomic_load(&h->refcnt);
        if (all || c) printf("ref=%d\n", c);
    }
    printf("==== end memory stat ====\n");
}
#endif

#endif // FENG_C_HEADER_H
