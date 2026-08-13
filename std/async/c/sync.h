
// Mutex — opaque handle
void* mutexCreate();
void  mutexLock(void* m);
int   mutexTryLock(void* m);
void  mutexUnlock(void* m);
void  mutexDestroy(void* m);

// Condition variable — opaque handle
void* condCreate();
void  condWait(void* c, void* m);
void  condSignal(void* c);
void  condBroadcast(void* c);
void  condDestroy(void* c);

