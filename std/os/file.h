void *fopen(const char *pathname, const char *mode);
int fclose(void *stream);
int fread(void *buf, int size, int count, void *stream);
int fwrite(const void *buf, int size, int count, void *stream);
int fseek(void *stream, int offset, int whence);
int ftell(void *stream);
int feof(void *stream);
int ferror(void *stream);
void *__acrt_iob_func(unsigned int index);