#ifndef FENG_HEADER_H
#define FENG_HEADER_H

#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <list>
#include <new>

class DoubleFree : public std::exception {
};

class OutOfBounds : public std::exception {
};

class NegativeInteger : public std::exception {
};

class NilPointer : public std::exception {
};

template<typename T>
static T *Feng$required(T *p) {
	if (p != nullptr) return p;
	throw NilPointer();
}


struct Feng$Type {
	uint32_t classId;
};

class Object {
public:
	uint32_t feng$classId;

	Object &Feng$share() {
		return *this;
	}

	Object &Feng$release() {
		return *this;
	}
};


// reference counting
struct Feng$Header {
	std::atomic_int refcnt;
#ifdef FENG_DEBUG_MEMORY
	bool released;
	bool isObject;
#endif
};

static Feng$Header *Feng$headerOf(void *p) {
	return (Feng$Header *) (((uint8_t *) p) - sizeof(Feng$Header));
}

static void *Feng$toInstance(Feng$Header *fh) {
	return (((uint8_t *) fh) + sizeof(Feng$Header));
}

#ifndef FENG_MAX_ENUM_NAME_LEN
#define FENG_MAX_ENUM_NAME_LEN 64
#endif
struct Feng$Enum {
	int32_t value;
	char name[FENG_MAX_ENUM_NAME_LEN];
};

#ifndef FENG_MAX_CLASS_NUM
#define FENG_MAX_CLASS_NUM 1
#endif
#ifndef FENG_MAX_INHERIT_SIZE
#define FENG_MAX_INHERIT_SIZE 1
#endif
#ifndef FENG_MAX_IMPLS_SIZE
#define FENG_MAX_IMPLS_SIZE 1
#endif


struct Feng$ClassRelation {
	uint16_t inheritsSize;
	uint16_t implsSize;
	uint32_t inherits[FENG_MAX_INHERIT_SIZE];
	uint32_t impls[FENG_MAX_IMPLS_SIZE];

	bool findAncestor(uint32_t value) const {
		for (int i = 0; i < inheritsSize; ++i)
			if (inherits[i] == value)
				return true;
		return false;
	}

	bool findImpl(uint32_t value) const {
		for (int i = 0; i < implsSize; ++i)
			if (impls[i] == value)
				return true;
		return false;
	}
};

extern const Feng$ClassRelation Feng$classRelations[FENG_MAX_CLASS_NUM];

template<class T>
static T *Feng$inherit(void *p, uint32_t classId) {
	Object *o = (Object *) p;
	int id = o->feng$classId;
	if (id == classId) return (T *) p;
	if (Feng$classRelations[id].findAncestor(classId)) {
		return (T *) p;
	}
	return nullptr;
}

template<class T>
static T *Feng$impl(void *p, uint32_t interfaceId) {
	Object *o = (Object *) p;
	if (Feng$classRelations[o->feng$classId].findImpl(interfaceId)) {
		return (T *) p;
	}
	return nullptr;
}


#ifdef FENG_DEBUG_MEMORY
static std::list<Feng$Header *> objects;
#endif

static void *Feng$alloc(int64_t size, bool isObject) {
	void *p = malloc(sizeof(Feng$Header) + size);
	if (p == nullptr) throw std::bad_alloc();

	Feng$Header *fh = (Feng$Header *) p;
	fh->refcnt.store(1);
#ifdef FENG_DEBUG_MEMORY
	fh->released = false;
	fh->isObject = isObject;
	objects.push_back(fh);
#endif
	return Feng$toInstance(fh);
}

template<class T>
static T *Feng$newObject(int32_t classId, T &&init) {
	T *p = (T *) Feng$alloc(sizeof(T), true);
	*p = init;
	((Object *) p)->feng$classId = classId;
	return p;
}

template<typename T>
static T *Feng$newMem(T &&init) {
	T *p = (T *) Feng$alloc(sizeof(T), false);
	*p = init;
	return p;
}

static void Feng$del(Feng$Header *fh) {
#ifdef FENG_DEBUG_MEMORY
	fh->released = true;
#else
	free(fh);
#endif
}

template<typename T>
static T *Feng$inc(T *p) {
	if (p == nullptr) return p;
	Feng$Header *fh = Feng$headerOf(p);
	fh->refcnt.fetch_add(1, std::memory_order_relaxed);
	return p;
}

template<typename T>
static T *&Feng$dec(T *&p) {
	if (p == nullptr) return p;
	Feng$Header *fh = Feng$headerOf(p);
	int ref = fh->refcnt.fetch_sub(1, std::memory_order_acq_rel) - 1;
	if (ref == 0) {
		if constexpr (requires { p->Feng$release(); }) {
			p->Feng$release();
		}
		Feng$del(fh);
		return p;
	} else if (ref < 0) {
#ifndef FENG_DEBUG_MEMORY
		throw DoubleFree();
#else
		return p;
#endif
	}
	return p;
}

template <typename E, int64_t L>
struct Feng$Array {
    E values[L];

    E& operator[](int64_t index) {
        if (index < 0 || index >= L)
            throw OutOfBounds();
        return values[index];
    }
};

template <typename E>
struct Feng$ArrayRefer {
    E* start;
    int64_t len;

    E& operator[](int64_t index) {
        if (index < 0 || index >= len)
            throw OutOfBounds();
        return start[index];
    }
};

template <typename E>
static Feng$ArrayRefer<E> Feng$newArray(int64_t len) {
    if (len < 0)
        throw NegativeInteger();
    E* p = (E*)Feng$alloc(sizeof(E) * len, false);
    memset(p, 0, sizeof(E) * len);
    return {.start = p, .len = len};
}

template <typename E, int64_t L>
static Feng$ArrayRefer<E> Feng$newArray(int64_t len, Feng$Array<E, L>&& init) {
    if (len < L) throw OutOfBounds();
    Feng$ArrayRefer<E> ar = Feng$newArray<E>(len);
    for (int i = 0; i < L; ++i) {
        ar[i] = init[i];
    }
    return ar;
}

template <typename E>
static Feng$ArrayRefer<E>& Feng$incAR(Feng$ArrayRefer<E> &ar) {
    Feng$inc(ar.start);
    return ar;
}

template <typename E>
static Feng$ArrayRefer<E>&& Feng$incAR(Feng$ArrayRefer<E>&& ar) {
    Feng$inc(ar.start);
    return ar;
}

template <typename E>
static Feng$ArrayRefer<E>& Feng$decAR(Feng$ArrayRefer<E>& ar) {
    Feng$dec(ar.start);
    return ar;
}

template <typename S, typename R>
static Feng$ArrayRefer<R> Feng$mapA2A(Feng$ArrayRefer<S> s) {
    int64_t len = (sizeof(S) * s.len) / sizeof(R);
    return (Feng$ArrayRefer<R>){.start = (R*)s.start, .len = len};
}

template <typename S, typename R>
static Feng$ArrayRefer<R> Feng$mapU2A(S *s) {
    int64_t len = sizeof(S) / sizeof(R);
    return (Feng$ArrayRefer<R>){.start = (R*)s, .len = len};
}

template <typename S, typename R>
static R* Feng$mapA2U(Feng$ArrayRefer<S>& s) {
    if (sizeof(R) > (sizeof(S) * s.len)) {
        throw OutOfBounds();
    }
    return (R *) s.start;
}

template <typename E>
static Feng$ArrayRefer<E> Feng$refer(E *start, int64_t len) {
    return {.start = start, .len = len};
}

template <typename S, typename R>
static Feng$ArrayRefer<R> Feng$refer(S* start, int64_t len) {
    int64_t l = sizeof(S) * len / sizeof(R);
    return {.start = (R*) start, .len = l};
}

#endif //FENG_HEADER_H
