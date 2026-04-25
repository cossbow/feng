#ifndef FENG_HEADER_H
#define FENG_HEADER_H

#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <list>
#include <vector>

// define primitives
typedef uint8_t Byte;
typedef int64_t Int;
typedef int8_t Int8;
typedef int16_t Int16;
typedef int32_t Int32;
typedef int64_t Int64;
typedef uint64_t Uint;
typedef uint8_t Uint8;
typedef uint16_t Uint16;
typedef uint32_t Uint32;
typedef uint64_t Uint64;
typedef double Float;
typedef float Float32;
typedef double Float64;
typedef bool Bool;


class Feng$DoubleFree : public std::exception {
};

class Feng$OutOfBounds : public std::exception {
};

class Feng$Unreachable : public std::exception {
};

class Feng$NegativeInteger : public std::exception {
};

class Feng$NilPointer : public std::exception {
};

class Feng$UseAfterFree : public std::exception {
};

template<typename T>
requires std::integral<T>
static T Feng$checkIndex(T index, T bounds) {
	if (0 <= index && index < bounds) return index;
	throw Feng$OutOfBounds();
}

// 快速幂：计算 a^b
static Int64 Feng$fastPow(Int64 a, Int64 b) {
	Int64 result = 1;
	while (b > 0) {
		if (b & 1) {  // 如果b的二进制位为1
			result *= a;
		}
		a *= a;  // 底数平方
		b >>= 1; // 右移一位
	}
	return result;
}

// 快速幂：计算 a^b
static Float64 Feng$fastPow(Float64 a, Int64 b) {
	Float64 result = 1;
	while (b > 0) {
		if (b & 1) {  // 如果b的二进制位为1
			result *= a;
		}
		a *= a;  // 底数平方
		b >>= 1; // 右移一位
	}
	return result;
}

template<typename T>
static T *Feng$required(T *p) {
	if (p != nullptr) return p;
	throw Feng$NilPointer();
}

// The root of polymorphism and abstraction classes
class $Object {
public:

	$Object() = default;

	virtual ~$Object() = default;

	$Object &operator=(const $Object &) = default;

	auto operator<=>(const $Object &) const = default;
};

const uint16_t MAGIC = 0xA539;

// reference counting
struct Feng$Header {
	std::atomic_int refcnt;
};

template<typename T>
static Feng$Header *Feng$headerOf(T *t) {
	void *p;
	if constexpr (std::is_polymorphic_v<T>) {
		auto o = dynamic_cast<$Object *>(t);
		if (o != nullptr) p = o;
		else p = t;
	} else {
		p = t;
	}
	return (Feng$Header *) (((uint8_t *) p) - sizeof(Feng$Header));
}

static void *Feng$toInstance(Feng$Header *fh) {
	return (((uint8_t *) fh) + sizeof(Feng$Header));
}


#ifdef FENG_DEBUG_MEMORY
static std::list<Feng$Header *> objects;

#include <cstdio>

static void feng$debug(bool all) {
	printf("==== see memory stat ====\n");
	for (const auto &o: objects) {
		int c = o->refcnt.load();
		if (all || c) printf("ref=%d\n", c);
	}
	printf("==== end memory stat ====\n");
}

#endif

static void *Feng$alloc(int64_t size) {
	void *p = malloc(sizeof(Feng$Header) + size);
	if (p == nullptr) throw std::bad_alloc();

	Feng$Header *fh = (Feng$Header *) p;
	fh->refcnt.store(1);
#ifdef FENG_DEBUG_MEMORY
	objects.push_back(fh);
#endif
	void *o = Feng$toInstance(fh);
	memset(o, 0, size);
	return o;
}

template<typename T>
static void Feng$del(T *p) {
#ifndef FENG_DEBUG_MEMORY
	Feng$Header *fh = Feng$headerOf(p);
	free(fh);
#endif
}

template<typename T>
static T *Feng$inc(T *p) {
	if (p == nullptr) return p;
	Feng$Header *fh = Feng$headerOf(p);
	int ref = fh->refcnt.fetch_add(1, std::memory_order_relaxed);
	if (ref < 1) {
		throw Feng$UseAfterFree();
	}
	return p;
}

template<typename T>
static bool Feng$dec0(T *p) {
	if (p == nullptr) return false;
	Feng$Header *fh = Feng$headerOf(p);
	int ref = fh->refcnt.fetch_sub(1, std::memory_order_acq_rel) - 1;
	if (ref == 0) {
		return true;
	} else if (ref < 0) {
		throw Feng$DoubleFree();
	}
	return false;
}

template<typename T>
static T *&Feng$dec(T *&p) {
	if (!Feng$dec0(p)) return p;
	if constexpr (std::is_polymorphic_v<T>) {
		auto o = dynamic_cast<$Object *>(p);
		if (o != nullptr) o->~$Object();
		else p->~T();
	} else {
		if (std::is_destructible_v<T>) {
			p->~T();
		}
	}
	Feng$del(p);
	return p;
}

template<typename T>
struct Feng$Refer {
	T *t;

	T *borrow() {
		return t;
	}
};

template<typename S, typename T>
static inline T *Feng$cast(S *s) {
	if constexpr (std::is_base_of_v<T, S>) {
		return static_cast<T *>(s);
	} else {
		return (T *) (void *) s;
	}
}

template<typename T>
struct Feng$SRefer {
	T *t;

	// [builtin] creator without init
	Feng$SRefer() : t(nullptr) {}

	explicit Feng$SRefer(std::nullptr_t) : t(nullptr) {}

	// [builtin] creator with init
	Feng$SRefer(T *t) : t(t) {}

	// var t1 = map(t0);
	Feng$SRefer(Feng$Refer<T> &&r) {
		t = Feng$inc(r.t);
	}

	// var t1 = t0;
	Feng$SRefer(Feng$SRefer<T> const &r) {
		t = Feng$inc(r.t);
	}

	// var t *T = new(T);
	// var t *T = make();
	Feng$SRefer(Feng$SRefer<T> &&r) noexcept {
		t = r.t;
		r.t = nullptr;
	}

	// var t *T = s;
	template<typename S>
	Feng$SRefer(Feng$SRefer<S> &s) {
		t = Feng$cast<S, T>(Feng$inc(s.t));
	}

	// var t *T = new(S);
	template<typename S>
	Feng$SRefer(Feng$SRefer<S> &&s) {
		t = Feng$cast<S, T>(Feng$inc(s.t));
	}

	~Feng$SRefer() {
		Feng$dec(t);
	}

	// var t1 &S = t0;
	T *borrow() const {
		return t;
	}

	// t = nil;
	void clear() {
		Feng$dec(t);
		t = nullptr;
	}

	// t == nil;
	bool absent() const {
		return t == nullptr;
	}

	// t.start();
	T *operator->() const {
		Feng$required(t);
		return t;
	}

	// *t
	T &operator*() const {
		Feng$required(t);
		return *t;
	}

	// t1 = map(t0);
	Feng$SRefer<T> &operator=(Feng$Refer<T> &&r) {
		Feng$dec(t) = Feng$inc(r.t);
		return *this;
	}

	// t1 = t0;
	Feng$SRefer<T> &operator=(Feng$SRefer<T> const &r) {
		if (this != &r)
			Feng$dec(t) = Feng$inc(r.t);
		return *this;
	}

	// t = new(T);
	Feng$SRefer<T> &operator=(Feng$SRefer<T> &&r) noexcept {
		if (this != &r) {
			Feng$dec(t) = r.t;
			r.t = nullptr;
		}
		return *this;
	}

	auto operator<=>(const Feng$SRefer<T> &) const = default;
};

template<class T>
static Feng$SRefer<T> Feng$newObject() {
	void *p = Feng$alloc(sizeof(T));
	return Feng$SRefer<T>{new(p) T()};
}

template<class T, typename ...Args>
static Feng$SRefer<T> Feng$newObjectInit(Args &&... args) {
	void *p = Feng$alloc(sizeof(T));
	return Feng$SRefer<T>{new(p) T(std::forward<Args>(args)...)};
}

template<class T>
static Feng$SRefer<T> Feng$newObjectCopy(T &t) {
	void *p = Feng$alloc(sizeof(T));
	return Feng$SRefer<T>{new(p) T(t)};
}

template<class T>
static Feng$SRefer<T> Feng$newObjectCopy(Feng$SRefer<T> &t) {
	void *p = Feng$alloc(sizeof(T));
	return Feng$SRefer<T>{new(p) T(*t)};
}

template<typename T>
static Feng$SRefer<T> Feng$newMem() {
	T *p = (T *) Feng$alloc(sizeof(T));
	*p = {};
	return Feng$SRefer<T>{p};
}

template<typename T>
static Feng$SRefer<T> Feng$newMem(const T &init) {
	T *p = (T *) Feng$alloc(sizeof(T));
	*p = init;
	return Feng$SRefer<T>{p};
}

template<typename T>
static Feng$SRefer<T> Feng$newMem(T &&init) {
	T *p = (T *) Feng$alloc(sizeof(T));
	*p = init;
	return Feng$SRefer<T>{p};
}

template<typename T>
struct Feng$PRefer {
	T *t;

	Feng$PRefer() : t(nullptr) {}

	Feng$PRefer(T *t) : t(t) {}

	template<typename S>
	Feng$PRefer(S *s) : t(Feng$cast<S, T>(s)) {}

	Feng$PRefer(T &t) : t(&t) {}

	Feng$PRefer(T &&t) : t(&t) {}

	template<typename S>
	Feng$PRefer(S &s) : t(Feng$cast<S, T>(&s)) {}

	Feng$PRefer(Feng$SRefer<T> &r) : t(r.t) {}

	template<typename S>
	Feng$PRefer(Feng$SRefer<S> &r) : t(Feng$cast<S, T>(r.t)) {}

	Feng$PRefer(Feng$PRefer<T> &r) : t(r.t) {}

	template<typename S>
	Feng$PRefer(Feng$PRefer<S> &r) : t(Feng$cast<S, T>(r.t)) {}

	Feng$PRefer(Feng$Refer<T> &&r) : t(r.t) {}

	// t == nil;
	bool absent() const {
		return t == nullptr;
	}

	// t.start();
	T *operator->() const {
		Feng$required(t);
		return t;
	}

	// *t
	T &operator*() const {
		Feng$required(t);
		return *t;
	}
};

template<class S, class T>
static Feng$Refer<T> Feng$assert(const Feng$SRefer<S> &p) {
	return Feng$Refer<T>{dynamic_cast<T *>(p.t)};
}

template<class S, class T>
static Feng$Refer<T> Feng$assert(Feng$SRefer<S> &&p) {
	return Feng$Refer<T>{dynamic_cast<T *>(p.t)};
}

template<class S, class T>
static Feng$PRefer<T> Feng$assert(const Feng$PRefer<S> &p) {
	return Feng$PRefer<T>{dynamic_cast<T *>(p.t)};
}


/**
 * 数组
 * @tparam E 元素类型
 * @tparam L 长度常量
 */
template<typename E, int64_t L>
struct Feng$Array {
	E values[L] = {};

	Feng$Array() = default;

	Feng$Array(Feng$Array<E, L> &a) {
		for (int i = 0; i < L; ++i) {
			values[i] = std::forward<E>(a.values[i]);
		}
	}

	Feng$Array(Feng$Array<E, L> &&a) noexcept {
		for (int i = 0; i < L; ++i) {
			values[i] = std::forward<E>(a.values[i]);
		}
	}

	template<typename... Args>
	Feng$Array(Args &&... args) {
		static_assert(sizeof...(Args) <= L, "out of bounds");
		int i = 0;
		((values[i++] = std::forward<E>(args)), ...);
	}

	Feng$Array<E, L> &operator=(Feng$Array<E, L> const &a) noexcept {
		for (int i = 0; i < L; ++i) {
			values[i] = a.values[i];
		}
		return *this;
	}

	Feng$Array<E, L> &operator=(Feng$Array<E, L> &&a) noexcept {
		for (int i = 0; i < L; ++i) {
			values[i] = std::forward<E>(a.values[i]);
		}
		return *this;
	}

	template<typename... Args>
	Feng$Array<E, L> &operator=(Args &&... args) {
		int i = 0;
		((values[i++] = std::forward<E>(args)), ...);
		return *this;
	}

	E &operator[](int64_t index) {
		if (index < 0 || index >= L)
			throw Feng$OutOfBounds();
		return values[index];
	}

	auto operator<=>(const Feng$Array<E, L> &) const = default;
};

/**
 * 数组索引
 * @tparam E 元素类型
 */
template<typename E>
struct Feng$ArrayRefer {
	E *start;
	int64_t len;

	Feng$ArrayRefer() : start(nullptr), len(0) {}

	Feng$ArrayRefer(E *start, int64_t len) : start(start), len(len) {}

	// t == nil;
	bool absent() const {
		return start == nullptr;
	}

	E &operator[](int64_t index) const {
		Feng$required(start);
		if (index < 0 || index >= this->len)
			throw Feng$OutOfBounds();
		return this->start[index];
	}

	bool operator==(const Feng$ArrayRefer<E> &o) const {
		return this->start == o.start;
	}

	bool operator!=(const Feng$ArrayRefer<E> &o) const {
		return this->start != o.start;
	}

	auto operator<=>(const Feng$ArrayRefer<E> &o) const {
		if (this->start < o.start) return std::strong_ordering::less;
		if (this->start > o.start) return std::strong_ordering::greater;
		return std::strong_ordering::equal;
	}

};

// 数组引用：强引用
template<typename E>
struct Feng$ArraySRefer : public Feng$ArrayRefer<E> {

	Feng$ArraySRefer() {}

	Feng$ArraySRefer(std::nullptr_t) : Feng$ArrayRefer<E>() {}

	Feng$ArraySRefer(E *start, int64_t len) {
		this->start = start;
		this->len = len;
	}

	Feng$ArraySRefer(const Feng$ArrayRefer<E> &r) {
		this->start = Feng$inc(r.start);
		this->len = r.len;
	}

	Feng$ArraySRefer(Feng$ArrayRefer<E> &&r) {
		this->start = Feng$inc(r.start);
		this->len = r.len;
		r.start = nullptr;
		r.len = 0;
	}

	Feng$ArraySRefer(const Feng$ArraySRefer<E> &r) {
		this->start = Feng$inc(r.start);
		this->len = r.len;
	}

	Feng$ArraySRefer(Feng$ArraySRefer<E> &&r) noexcept {
		this->start = r.start;
		this->len = r.len;
		r.start = nullptr;
		r.len = 0;
	}

	~Feng$ArraySRefer() {
		dec();
	}

	Feng$ArraySRefer<E> &operator=(std::nullptr_t) {
		if (this->start == nullptr) return *this;
		dec();
		this->start = nullptr;
		this->len = 0;
		return *this;
	}

	Feng$ArraySRefer<E> &operator=(const Feng$ArraySRefer<E> &r) {
		if (this == &r) return *this;
		dec();
		this->start = Feng$inc(r.start);
		this->len = r.len;
		return *this;
	}

	Feng$ArraySRefer<E> &operator=(Feng$ArraySRefer<E> &&r) noexcept {
		if (this == &r) return *this;
		dec();
		this->start = r.start;
		this->len = r.len;
		r.start = nullptr;
		r.len = 0;
		return *this;
	}

	auto operator<=>(const Feng$ArraySRefer<E> &) const = default;

private:
	void dec() {
		if (!Feng$dec0(this->start)) return;
		if (std::is_destructible_v<E>) {
			for (int i = 0; i < this->len; ++i) {
				this->start[i].~E();
			}
		}
		Feng$del(this->start);
	}
};

// 数组引用：虚引用
template<typename E>
struct Feng$ArrayPRefer : public Feng$ArrayRefer<E> {

	Feng$ArrayPRefer() : Feng$ArrayRefer<E>() {}

	Feng$ArrayPRefer(std::nullptr_t) : Feng$ArrayRefer<E>() {}

	Feng$ArrayPRefer(E *start, int64_t len) : Feng$ArrayRefer<E>(start, len) {}

	Feng$ArrayPRefer(Feng$ArrayRefer<E> &&r) : Feng$ArrayRefer<E>(r) {}

	Feng$ArrayPRefer(Feng$ArraySRefer<E> &r) : Feng$ArrayRefer<E>(r) {}

	Feng$ArrayPRefer(Feng$ArrayPRefer<E> &r) : Feng$ArrayRefer<E>(r) {}

	auto operator<=>(const Feng$ArrayPRefer<E> &) const = default;
};

template<typename E, typename T>
concept BaseArrayRefer = std::is_base_of_v<Feng$ArrayRefer<E>, T>;

// 创建数组实例
template<typename E>
static Feng$ArraySRefer<E> Feng$newArray(int64_t len) {
	if (len < 0) throw Feng$NegativeInteger();
	void *p = Feng$alloc(sizeof(E) * len);
	memset(p, 0, sizeof(E) * len);
	E *e = new(p)E[len];
	return {e, len};
}

// 创建数组实例
template<typename E, typename ...Args>
static Feng$ArraySRefer<E> Feng$newArrayInit(int64_t len, Args &&... args) {
	int64_t num = sizeof...(Args);
	if (len < num) throw Feng$OutOfBounds();
	auto a = Feng$newArray<E>(len);
	int i = 0;
	((a[i++] = std::forward<E>(args)), ...);
	return a;
}

// 创建数组实例
template<typename E, int64_t L0>
static Feng$ArraySRefer<E> Feng$newArrayCopy(int64_t len, Feng$Array<E, L0> &init) {
	if (len < L0) throw Feng$OutOfBounds();
	auto a = Feng$newArray<E>(len);
	for (int i = 0; i < L0; ++i) {
		a[i] = init[i];
	}
	return a;
}

template<typename E, typename A>
requires BaseArrayRefer<E, A>
static Feng$ArraySRefer<E> Feng$newArrayCopy(int64_t len, A &init) {
	if (len < init.len) throw Feng$OutOfBounds();
	auto a = Feng$newArray<E>(len);
	for (int i = 0; i < init.len; ++i) {
		a[i] = init[i];
	}
	return a;
}

// mappable数组转换，例如：[*]int32 -> [*]int8
template<typename S, typename A, typename T>
requires BaseArrayRefer<S, A>
static Feng$ArrayRefer<T> Feng$mapA2A(A &s) {
	int64_t len = (sizeof(S) * s.len) / sizeof(T);
	return {(T *) s.start, len};
}

template<typename S, int64_t L, typename R>
static Feng$ArrayPRefer<R> Feng$mapA2A(Feng$Array<S, L> &s) {
	int64_t l = sizeof(S) * L / sizeof(R);
	return {(R *) s.values, l};
}

// mappable数组转换，例如： *int32 -> [*]int8
template<typename S, typename T>
static Feng$ArrayRefer<T> Feng$mapU2A(S *s) {
	int64_t len = sizeof(S) / sizeof(T);
	return {(T *) s, len};
}

template<typename S, typename T>
static Feng$ArrayRefer<T> Feng$mapU2A(Feng$SRefer<S> &s) {
	int64_t len = sizeof(S) / sizeof(T);
	return {(T *) s.t, len};
}

template<typename S, typename T>
static Feng$ArrayRefer<T> Feng$mapU2A(Feng$SRefer<S> &&s) {
	int64_t len = sizeof(S) / sizeof(T);
	return {(T *) s.t, len};
}

template<typename S, typename T>
static Feng$ArrayRefer<T> Feng$mapU2A(Feng$PRefer<S> &s) {
	int64_t len = sizeof(S) / sizeof(T);
	return {(T *) s.t, len};
}

template<typename S, typename T>
static Feng$ArrayRefer<T> Feng$mapU2A(Feng$PRefer<S> &&s) {
	int64_t len = sizeof(S) / sizeof(T);
	return {(T *) s.t, len};
}

// mappable数组转换，例如：[*]int8 -> *int32
template<typename E, typename A, typename U>
requires BaseArrayRefer<E, A>
static Feng$Refer<U> Feng$mapA2U(A &s) {
	if (sizeof(U) > (sizeof(E) * s.len))
		throw Feng$OutOfBounds();
	return {(U *) s.start};
}


template<typename T, int64_t L>
struct Feng$GlobalArray {
	Feng$Header $header;
	Feng$Array<T, L> array;

	Feng$ArraySRefer<T> sr() {
		return {Feng$inc(array.values), L};
	}

	Feng$ArrayPRefer<T> pr() {
		return {array.values, L};
	}
};

// enum type struct
struct Feng$Enum {
	Int $value;
	Feng$ArrayPRefer<Byte> $name;
};


// function prototype
template<typename Signature>
class Feng$Prototype;

template<typename Ret, typename... Args>
class Feng$Prototype<Ret(Args...)> {
private:
	using Feng$CppFun = Ret(*)(Args...);
	Feng$CppFun fp;

public:
	Feng$Prototype() : fp(nullptr) {}

	Feng$Prototype(Feng$CppFun fp) : fp(fp) {}

	Ret operator()(Args &&... args) const {
		Feng$required(fp);
		return fp(std::forward<Args>(args)...);
	}

	// 使用模板化的调用操作符，支持参数隐式转换
	template<typename... CallArgs>
	Ret operator()(CallArgs &&... args) const {
		Feng$required(fp);
		// 将参数转换为期望的类型
		return fp(static_cast<Args>(std::forward<CallArgs>(args))...);
	}

	Feng$Prototype &operator=(Feng$CppFun fp) {
		fp = fp;
		return *this;
	}

	explicit operator bool() const {
		return fp != nullptr;
	}

	Feng$CppFun target() const {
		return fp;
	}

	bool operator==(const Feng$Prototype &o) const {
		return fp == o.fp;
	}

	bool operator!=(const Feng$Prototype &o) const {
		return fp != o.fp;
	}

	auto operator<=>(const Feng$Prototype &o) const {
		// 函数指针的比较返回 bool，需要转换为 ordering
		if ((void *) fp < (void *) o.fp) return std::strong_ordering::less;
		if ((void *) fp > (void *) o.fp) return std::strong_ordering::greater;
		return std::strong_ordering::equal;
	}

};


#endif //FENG_HEADER_H
