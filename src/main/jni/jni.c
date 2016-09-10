#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <sys/types.h>
#include <inttypes.h>
#include <stdlib.h>
#include <openssl/aes.h>
#include <unistd.h>
#include "utils.h"
#include "image.h"

int registerNativeTgNetFunctions(JavaVM *vm, JNIEnv *env);
int gifvideoOnJNILoad(JavaVM *vm, JNIEnv *env);

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv *env = 0;
    srand(time(NULL));
    
	if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
		return -1;
	}
    
    if (imageOnJNILoad(vm, reserved, env) == -1) {
        return -1;
    }
    
    if (gifvideoOnJNILoad(vm, env) == -1) {
        return -1;
    }

    if (registerNativeTgNetFunctions(vm, env) != JNI_TRUE) {
        return -1;
    }
    
	return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {

}
