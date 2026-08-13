#include <pthread.h>
#include <stdlib.h>


// ===== Mutex =====

void* mutexCreate() {
    pthread_mutex_t* m = (pthread_mutex_t*)malloc(sizeof(pthread_mutex_t));
    if (!m) return 0;
    pthread_mutex_init(m, NULL);
    return (void*)m;
}

void mutexLock(void* handle) {
    pthread_mutex_t* m = (pthread_mutex_t*)handle;
    if (m) pthread_mutex_lock(m);
}

int mutexTryLock(void* handle) {
    pthread_mutex_t* m = (pthread_mutex_t*)handle;
    if (!m) return 0;
    return pthread_mutex_trylock(m) == 0 ? 1 : 0;
}

void mutexUnlock(void* handle) {
    pthread_mutex_t* m = (pthread_mutex_t*)handle;
    if (m) pthread_mutex_unlock(m);
}

void mutexDestroy(void* handle) {
    pthread_mutex_t* m = (pthread_mutex_t*)handle;
    if (m) {
        pthread_mutex_destroy(m);
        free(m);
    }
}

// ===== Condition Variable =====

void* condCreate() {
    pthread_cond_t* c = (pthread_cond_t*)malloc(sizeof(pthread_cond_t));
    if (!c) return 0;
    pthread_cond_init(c, NULL);
    return (void*)c;
}

void condWait(void* c, void* m) {
    pthread_cond_t* pc = (pthread_cond_t*)c;
    pthread_mutex_t* pm = (pthread_mutex_t*)m;
    if (pc && pm) pthread_cond_wait(pc, pm);
}

void condSignal(void* handle) {
    pthread_cond_t* c = (pthread_cond_t*)handle;
    if (c) pthread_cond_signal(c);
}

void condBroadcast(void* handle) {
    pthread_cond_t* c = (pthread_cond_t*)handle;
    if (c) pthread_cond_broadcast(c);
}

void condDestroy(void* handle) {
    pthread_cond_t* c = (pthread_cond_t*)handle;
    if (c) {
        pthread_cond_destroy(c);
        free(c);
    }
}
