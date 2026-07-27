#ifdef FENG_MAIN_HAS_ARGS
#ifndef FENG_TYPEDEF_ArraySRef_ArraySRef_Byte
#define FENG_TYPEDEF_ArraySRef_ArraySRef_Byte
typedef struct { Feng$ArraySRef_Byte* $values; Int64 $length; } Feng$ArraySRef_ArraySRef_Byte;
#endif
#ifndef FENG_FUNC_cleanup_arr_ArraySRef_Byte
#define FENG_FUNC_cleanup_arr_ArraySRef_Byte
static inline void Feng$cleanup_arr_ArraySRef_Byte(Feng$ArraySRef_ArraySRef_Byte *p) {
	if (p->$values && Feng$dec(p->$values)) {
		for (Int64 i0 = 0; i0 < p->$length; i0++) {
			Feng$cleanup_arr_Byte(&p->$values[i0]);
		}
		Feng$free(p->$values);
	}
}
#endif
#endif


int main(int argc, char **argv) {
	{
#ifdef FENG_MAIN_HAS_ARGS
		Feng$ArraySRef_ArraySRef_Byte list FENG$DEC(Feng$cleanup_arr_ArraySRef_Byte);
		list.$values = Feng$alloc(argc*sizeof(void*));
		list.$length = argc;
		for (int i = 0; i < argc; ++i) {
			int64_t len = (int64_t) strlen(argv[i]);
			Byte *values = Feng$alloc(len*sizeof(Byte));
			memcpy(values, argv[i], len);
			list.$values[i].$values = values;
			list.$values[i].$length = len;
		}
		$main((Feng$ArrayPRef_ArraySRef_Byte){list.$values, list.$length});
#else
		(void)argc; (void)argv;
		$main();
#endif
	}
#ifdef FENG_DEBUG_MEMORY
	feng$debug(false);
#endif
	return 0;
}