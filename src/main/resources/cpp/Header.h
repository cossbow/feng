#ifndef FENG_HEADER_H
#define FENG_HEADER_H

#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <list>
#include <new>

class DoubleFree : public std::exception {
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

extern const Feng$ClassRelation Feng$classRelations[];

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


template<typename E>
struct Feng$ArrayRefer {
	E *start;
	int64_t size;
};


#endif //FENG_HEADER_H
