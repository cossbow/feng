#ifndef FENG_HEADER_H
#define FENG_HEADER_H

#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <list>
#include <new>

class DoubleFree : public std::exception {
};

#ifndef DOMAIN_CLASS
#define DOMAIN_CLASS 1
#endif

struct Feng$Type {
	uint32_t domain: 4;
	uint32_t id: 28;
};

// reference counting
struct Feng$Header {
	std::atomic_int refcnt;
#ifdef FENG_DEBUG_MEMORY
	bool released;
#endif
	Feng$Type type;
};

static Feng$Header *Feng$headerOf(void *p) {
	return (Feng$Header *) (((uint8_t *) p) - sizeof(Feng$Header));
}

static Feng$Type Feng$typeOf(void *p) {
	return Feng$headerOf(p)->type;
}

static void *Feng$toObject(Feng$Header *fh) {
	return (((uint8_t *) fh) + sizeof(Feng$Header));
}

#ifndef FENG_MAX_INHERIT_SIZE
#define FENG_MAX_INHERIT_SIZE 64
#endif

struct Feng$SortedTable {
	uint32_t size;
	uint32_t table[FENG_MAX_INHERIT_SIZE];

	bool exists(uint32_t value) const {
		for (int i = 0; i < size; ++i)
			if (table[i] == value)
				return true;
		return false;
	}

};

extern const Feng$SortedTable Feng$inheritTable[];

template<typename T, typename R>
R *Feng$assert(T *p, uint32_t targetTypeId) {
	Feng$Header *fh = Feng$headerOf(p);
	if (targetTypeId == fh->type.id) return (R *) p;
	if (Feng$inheritTable[fh->type.id].exists(targetTypeId)) {
		return (R *) p;
	}
	return nullptr;
}

template<typename T>
struct Feng$Pointer {
	Feng$Type type;
	T *t;

	T *get() {
		return t;
	}

	bool isNil() {
		return t == nullptr;
	}

	T *operator->() {
		return t;
	}
};

template<typename T>
static T *Feng$inc(T *p);

template<typename T>
static void Feng$dec(T *p);

template<typename T>
struct Feng$Refer {
	T *t = nullptr;

	~Feng$Refer() {
		Feng$dec(t);
	}

	void redirect(Feng$Refer<T> &&o) {
		redirect(o.t);
	}

	void redirect(Feng$Refer<T> &o) {
		redirect(o.t);
	}

	void redirect(T *o) {
		if (t == o) return;
		T *s = Feng$inc(o);
		Feng$dec(this->t);
		this->t = s;
	}

	Feng$Refer<T> share() {
		return {.t = Feng$inc(t)};
	}

	T *get() {
		return t;
	}

	bool isNil() {
		return t == nullptr;
	}

	T *operator->() {
		return t;
	}
};

template<typename T>
Feng$Pointer<T> Feng$borrow(T *t) {
	return {.type=Feng$typeOf(t), .t=t};
}

template<typename T>
Feng$Pointer<T> Feng$borrow(T &t) {
	return {.type=Feng$typeOf(&t), .t=&t};
}

template<typename T>
Feng$Pointer<T> Feng$borrow(Feng$Refer<T> &r) {
	return {.type=Feng$typeOf(r.t), .t=r.t};
}

template<typename T, typename R>
Feng$Pointer<R> Feng$cast(Feng$Pointer<T> &p) {
	return {.t=(R *) (void *) p.t, .type=p.type};
}

template<typename T, typename R>
Feng$Refer<R> Feng$cast(Feng$Refer<T> &r) {
	return {.t=Feng$inc((R *) (void *) r.t)};
}

template<typename T, typename R>
Feng$Refer<R> Feng$cast(Feng$Refer<T> &&r) {
	return {.t=Feng$inc((R *) (void *) r.t)};
}

template<typename T, typename R>
Feng$Pointer<R> *Feng$assert(Feng$Pointer<T> &p, uint32_t targetTypeId) {
	return {.t=Feng$inc(Feng$assert(p.t, targetTypeId)), .type=p.type};
}

template<typename T, typename R>
Feng$Refer<R> *Feng$assert(Feng$Refer<T> &p, uint32_t targetTypeId) {
	return {.t=Feng$inc(Feng$assert(p.t, targetTypeId)), .type=p.type};
}

template<typename T, typename R>
Feng$Refer<R> *Feng$assert(Feng$Refer<T> &&p, uint32_t targetTypeId) {
	return {.t=Feng$inc(Feng$assert(p.t, targetTypeId)), .type=p.type};
}

#ifdef FENG_DEBUG_MEMORY
static std::list<Feng$Header *> objects;
#endif

static void *Feng$new(int64_t size, uint32_t domain, uint32_t typeId) {
	void *p = malloc(sizeof(Feng$Header) + size);
	if (p == nullptr) throw std::bad_alloc();

	Feng$Header *fh = (Feng$Header *) p;
	fh->refcnt.store(1);
	fh->type = {domain, typeId};
#ifdef FENG_DEBUG_MEMORY
	fh->released = false;
	objects.push_back(fh);
#endif
	return Feng$toObject(fh);
}

#define FENG$NEW(type, domain, init)  ({    \
    type *_tmp = (type *) Feng$new(sizeof(type), domain, TypeId_##type); \
    *_tmp = init; \
    _tmp; \
})

template<typename T>
static void Feng$del(Feng$Header *fh, T *p) {
	// 最后一个 shared_ptr，可以销毁对象
	if constexpr (requires(T& t) { t.Feng$release(); } ) {
		p->Feng$release();
	}
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
static void Feng$dec(T *p) {
	if (p == nullptr) return;
	Feng$Header *fh = Feng$headerOf(p);
	int ref = fh->refcnt.fetch_sub(1, std::memory_order_acq_rel) - 1;
	if (ref == 0) {
		Feng$del(fh, p);
	} else if (ref < 0) {
#ifndef FENG_DEBUG_MEMORY
		throw DoubleFree();
#endif
	}
}


template<typename T>
struct Feng$FixedArray {
	Feng$Type type;
	uint32_t size;
	T elements[0];
};

template<typename T>
struct Feng$ReferArray {
	Feng$Type type;
	uint32_t size;
	T elements[0];
};


#endif //FENG_HEADER_H
