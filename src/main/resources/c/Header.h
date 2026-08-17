#ifndef FENG_C_HEADER_H
#define FENG_C_HEADER_H

#include <stdatomic.h>
#include <stdint.h>
#include <stddef.h>
#include <stdalign.h>
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
// refcnt is plain int — Linux-kernel style:
//   - sync types:  atomic ops via (atomic_int*)&refcnt cast
//   - non-sync:    plain ++/-- access
typedef struct Feng$Header {
#ifdef FENG_DEBUG_MEMORY
    struct Feng$Header* next;   // leak-check linked list (populated only under FENG_DEBUG_MEMORY)
    void* site;                 // allocation site (return address; recorded under FENG_DEBUG_MEMORY)
    int64_t size;               // requested data size (recorded under FENG_DEBUG_MEMORY)
#endif
    _Alignas(max_align_t)
    int refcnt;
} Feng$Header;

#ifdef FENG_DEBUG_MEMORY
// ===== leak checker (single shared list + report, defined in builtin.c) =====
extern Feng$Header* Feng$debug_list;
extern void feng$debug(bool all);
#endif

static inline void* Feng$alloc(int64_t size) {
    void* p = malloc(sizeof(Feng$Header) + size);
    if (!p) abort();
    Feng$Header* fh = (Feng$Header*) p;
    fh->refcnt = 1;
#ifdef FENG_DEBUG_MEMORY
    fh->next = Feng$debug_list;
    Feng$debug_list = fh;
    fh->site = __builtin_return_address(0);
    fh->size = size;
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

// atomic inc/dec — cast plain int* to atomic_int* (Linux kernel style)
static inline void* Feng$inc(void* p) {
    if (!p) return p;
    int* prc = &Feng$headerOf(p)->refcnt;
    int ref = atomic_fetch_add((atomic_int*)prc, 1);
    if (ref < 1) abort();
    return p;
}

// return true if refcnt reaches 0 (caller should release)
static inline bool Feng$dec(void* p) {
    if (!p) return false;
    int* prc = &Feng$headerOf(p)->refcnt;
    int ref = atomic_fetch_sub((atomic_int*)prc, 1) - 1;
    if (ref == 0) return true;
#ifdef FENG_DEBUG_MEMORY
    if (ref < 0) return false;  // allow over-release so the leak report still prints
#else
    if (ref < 0) abort();
#endif
    return false;
}

// ===== non-atomic reference counting (non-sync types, single-threaded) =====
static inline void* Feng$inc_ns(void* p) {
    if (!p) return p;
    int ref = ++Feng$headerOf(p)->refcnt;
    if (ref <= 1) abort();
    return p;
}

static inline bool Feng$dec_ns(void* p) {
    if (!p) return false;
    int ref = --Feng$headerOf(p)->refcnt;
    if (ref == 0) return true;
#ifdef FENG_DEBUG_MEMORY
    if (ref < 0) return false;  // allow over-release so the leak report still prints
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

// 析构派发实体与释放入口（定义见 Feng$objMeta 之后）。此处先前置声明，
// 因 cleanup_sref / store_sl / cleanup_sfield 定义早于 Feng$Meta / Feng$objMeta。
static inline void Feng$vDestroy(void* p);
static inline void Feng$release(void** slot);
static inline void Feng$release_ns(void** slot);

// generic cleanup for strong references (*T) — adapter to Feng$release
static inline void Feng$cleanup_sref(void* p) {
    Feng$release((void**) p);
}

// non-atomic cleanup for non-sync types — adapter to Feng$release_ns
static inline void Feng$cleanup_sref_ns(void* p) {
    Feng$release_ns((void**) p);
}

// plain boxed-value cleanup: dec → free (NO destructor dispatch).
// Used for boxed primitives / structs / enums (new(int) etc.), which have no $meta.
static inline void Feng$cleanup_free(void* p) {
    void** pp = (void**) p;
    if (*pp && Feng$dec(*pp)) Feng$free(*pp);
}
static inline void Feng$cleanup_free_ns(void* p) {
    void** pp = (void**) p;
    if (*pp && Feng$dec_ns(*pp)) Feng$free(*pp);
}

// ===== sync field spinlock (bit 0 of the pointer itself) =====
// malloc-aligned pointers have bit 0 == 0 — we steal it as a spinlock.
// This saves the 8-byte overhead of a separate atomic_flag per field
// (C++ std::atomic<shared_ptr> style).  CAS on the full uintptr_t
// ensures lock acquisition and pointer load/stores are atomic.
//
// bit 0 == 1 → locked;  ptr = raw & ~(uintptr_t)1

// Spinlock-protected load for sync var-field reads.
//   p = obj->field;  →  p = Feng$load_sl(&obj->field);
static inline void* Feng$load_sl(void** f) {
    uintptr_t raw, locked;
    do {
        raw = atomic_load((atomic_uintptr_t*)f);
        while (raw & 1) {
            raw = atomic_load((atomic_uintptr_t*)f);
        }
        locked = raw | 1;
    } while (!atomic_compare_exchange_weak((atomic_uintptr_t*)f, &raw, locked));
    // lock acquired; read ptr & inc refcnt
    void* p = (void*)(raw & ~(uintptr_t)1);
    if (p) {
        int* prc = &Feng$headerOf(p)->refcnt;
        atomic_fetch_add((atomic_int*)prc, 1);
    }
    // unlock — restore original raw value (bit 0 clear again)
    atomic_store((atomic_uintptr_t*)f, raw);
    return p;
}

// Spinlock-protected store for sync var-field assignment.
//   obj->field = src;  →  Feng$store_sl(&obj->field, src);
static inline void Feng$store_sl(void** f, void* src) {
    uintptr_t raw, locked;
    do {
        raw = atomic_load((atomic_uintptr_t*)f);
        while (raw & 1) {
            raw = atomic_load((atomic_uintptr_t*)f);
        }
        locked = raw | 1;
    } while (!atomic_compare_exchange_weak((atomic_uintptr_t*)f, &raw, locked));
    // lock acquired
    void* old = (void*)(raw & ~(uintptr_t)1);
    // inc src
    if (src) {
        int* prc = &Feng$headerOf(src)->refcnt;
        atomic_fetch_add((atomic_int*)prc, 1);
    }
    // dec old & free if zero (dispatch virtual destructor before free)
    if (old && Feng$dec(old)) { Feng$vDestroy(old); Feng$free(old); }
    // store & unlock in one atomic write (bit 0 always 0 for stored src)
    atomic_store((atomic_uintptr_t*)f, (uintptr_t)src);
}

// cleanup for sync var fields — for destructors only (refcnt==0, exclusive).
// The pointer may have bit 0 set by a racing load/store interrupted
// by the final dec reaching 0, so mask it defensively.
static inline void Feng$cleanup_sfield(void** f) {
    uintptr_t raw = atomic_load((atomic_uintptr_t*)f);
    void* p = (void*)(raw & ~(uintptr_t)1);
    if (p && Feng$dec(p)) { Feng$vDestroy(p); Feng$free(p); }
    *f = NULL;
}

// cleanup for sync var fields of FINAL classes — same defensive bit-0 mask,
// but dispatch through an explicit static destructor (final classes have no $meta,
// so Feng$vDestroy would read garbage). destroy must be Feng$destroy_X.
static inline void Feng$cleanup_sfield_final(void** f, void (*destroy)(void*)) {
    uintptr_t raw = atomic_load((atomic_uintptr_t*)f);
    void* p = (void*)(raw & ~(uintptr_t)1);
    if (p && Feng$dec(p)) { destroy(p); Feng$free(p); }
    *f = NULL;
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

// ===== destructor dispatch (Issue 1 root fix) =====
// 非 final 虚派发实体；final 类不经过它（无 $meta），由 codegen 静态调 Feng$destroy_X。
static inline void Feng$vDestroy(void* p) {
    Feng$objMeta(p)->destroy(p);
}

// 非 final 强引用释放入口（原子/非原子），唯一定义点：
//   dec 归零 → 派发析构（虚派发到最派生类）→ free
static inline void Feng$release(void** slot) {
    if (*slot && Feng$dec(*slot)) { Feng$vDestroy(*slot); Feng$free(*slot); }
}
static inline void Feng$release_ns(void** slot) {
    if (*slot && Feng$dec_ns(*slot)) { Feng$vDestroy(*slot); Feng$free(*slot); }
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

typedef struct $AssertException {
    const Feng$Meta* $meta;
    Uint64 fn;
    Uint32 line;
} $AssertException;

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
static inline void Feng$required(void* p, Uint64 fn, Uint32 line) {
    if (!p) Feng$throwNullPointer(fn, line);
}

static inline int64_t Feng$checkIndex(int64_t i, int64_t bound, Uint64 fn, Uint32 line) {
    if (0 <= i && i < bound) return i;
    Feng$throwIndexOutOfBounds(fn, line);
    return -1;
}

#endif // FENG_C_HEADER_H
