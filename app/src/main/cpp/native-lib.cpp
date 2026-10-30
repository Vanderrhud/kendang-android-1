#include <jni.h>
#include <oboe/Oboe.h>

// Entry point placeholder for kendang_audio native library
extern "C" JNIEXPORT jstring JNICALL
Java_com_vrkendang_MainActivity_stringFromJNI(
    JNIEnv* env,
    jobject /* this */) {
    return env->NewStringUTF("VR Kendang Audio Engine");
}
