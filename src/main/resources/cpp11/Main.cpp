int main(int argc, char **argv) {
	{
#ifdef FENG_MAIN_HAS_ARGS
		auto list = Feng$newArray<Feng$ArraySRefer<Byte>>(argc);
		for (int i = 0; i < argc; ++i) {
			int64_t len = (int64_t) strlen(argv[i]);
			auto a = Feng$newArray<Byte>(len);
			memcpy(a.$values, argv[i], len);
			list[i] = a;
		}
		$main(list);
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