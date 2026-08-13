#include <pthread.h>
#include <stdlib.h>
#include "Header.h"

// ===== Thread =====

typedef struct {
    void (*run)(void*);
    void* arg;
    pthread_t pt;
} ThreadTask;

static void* threadEntry(void* p) {
    ThreadTask* task = (ThreadTask*)p;
    void *arg = task->arg;
    task->run(arg);
    Feng$cleanup_sref(&arg);
    free(task);
    return NULL;
}

int threadCreate(void* run, void* arg) {
    ThreadTask* task = malloc(sizeof(ThreadTask));
    if (!task) {
        free(task);
        abort();
        return 12;
    }
    task->run = run;
    task->arg = Feng$inc(arg);

    int rc = pthread_create(&task->pt, NULL, threadEntry, task);
    if (rc != 0) {
        free(task);
        return rc;
    }
    return 0;
}
