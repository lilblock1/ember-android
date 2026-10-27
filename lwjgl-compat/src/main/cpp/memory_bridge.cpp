#include <jni.h>
#include <map>
#include <mutex>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <algorithm>
#include <new>

namespace {
constexpr size_t maxBlock=256ULL*1024*1024, maxTotal=512ULL*1024*1024;
struct Block { void* pointer;size_t bytes;size_t physical; };
std::map<uintptr_t,Block> blocks;
std::mutex memoryMutex;
size_t total=0;
uintptr_t canonical(uintptr_t value) { return value & 0x00FFFFFFFFFFFFFFULL; } // ARM64 top-byte malloc tags are not address bits.
uintptr_t raw(jlong value) { return static_cast<uintptr_t>(value); }
void fail(JNIEnv* env,const char* type,const char* message) { env->ThrowNew(env->FindClass(type),message); }
bool size(JNIEnv* env,jlong requested,size_t& result) {
    if(requested<0){fail(env,"java/lang/IllegalArgumentException","Negative native size");return false;}
    if(static_cast<uint64_t>(requested)>maxBlock){fail(env,"java/lang/OutOfMemoryError","Native allocation exceeds block budget");return false;}
    result=static_cast<size_t>(requested);return true;
}
bool tagValid(uintptr_t supplied,uintptr_t owned) { return (supplied>>56)==0 || (supplied>>56)==(owned>>56); }
void* range(JNIEnv* env,jlong supplied,size_t bytes) {
    uintptr_t address=canonical(raw(supplied));auto next=blocks.upper_bound(address);
    if(next==blocks.begin()){fail(env,"java/lang/IllegalArgumentException","Native address is not owned");return nullptr;}
    auto entry=std::prev(next);size_t offset=address-entry->first;const Block& block=entry->second;
    if(offset>block.bytes||bytes>block.bytes-offset||!tagValid(raw(supplied),reinterpret_cast<uintptr_t>(block.pointer))){
        fail(env,"java/lang/IllegalArgumentException","Native access exceeds owned allocation");return nullptr;
    }
    return static_cast<unsigned char*>(block.pointer)+offset;
}
jlong allocate(JNIEnv* env,size_t requested,bool zero) {
    size_t physical=std::max<size_t>(requested,1);
    if(physical>maxTotal-total||blocks.size()>=131072){fail(env,"java/lang/OutOfMemoryError","Native allocation budget exhausted");return 0;}
    void* pointer=zero?std::calloc(1,physical):std::malloc(physical);
    if(!pointer)return 0;
    try { blocks.emplace(canonical(reinterpret_cast<uintptr_t>(pointer)),Block{pointer,requested,physical}); }
    catch(const std::bad_alloc&){std::free(pointer);fail(env,"java/lang/OutOfMemoryError","Native allocation registry exhausted");return 0;}
    total+=physical;return static_cast<jlong>(reinterpret_cast<uintptr_t>(pointer));
}
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_andes_lwjgl_NativeMemory_allocate(JNIEnv* env,jclass,jlong requested,jboolean zero){
    size_t bytes;if(!size(env,requested,bytes))return 0;std::lock_guard<std::mutex> lock(memoryMutex);return allocate(env,bytes,zero==JNI_TRUE);
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_andes_lwjgl_NativeMemory_reallocate(JNIEnv* env,jclass,jlong supplied,jlong requested){
    size_t bytes;if(!size(env,requested,bytes))return 0;std::lock_guard<std::mutex> lock(memoryMutex);
    if(supplied==0)return allocate(env,bytes,false);
    auto entry=blocks.find(canonical(raw(supplied)));
    if(entry==blocks.end()||!tagValid(raw(supplied),reinterpret_cast<uintptr_t>(entry->second.pointer))){fail(env,"java/lang/IllegalArgumentException","Reallocation requires an owned base address");return 0;}
    size_t physical=std::max<size_t>(bytes,1),previous=entry->second.physical;
    if(physical>maxTotal-(total-previous)){fail(env,"java/lang/OutOfMemoryError","Native reallocation exceeds budget");return 0;}
    void* pointer=std::realloc(entry->second.pointer,physical);if(!pointer)return 0;
    auto node=blocks.extract(entry);node.key()=canonical(reinterpret_cast<uintptr_t>(pointer));node.mapped()=Block{pointer,bytes,physical};blocks.insert(std::move(node));
    total=total-previous+physical;return static_cast<jlong>(reinterpret_cast<uintptr_t>(pointer));
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeMemory_free(JNIEnv* env,jclass,jlong supplied){
    if(supplied==0)return;std::lock_guard<std::mutex> lock(memoryMutex);auto entry=blocks.find(canonical(raw(supplied)));
    if(entry==blocks.end()||!tagValid(raw(supplied),reinterpret_cast<uintptr_t>(entry->second.pointer))){fail(env,"java/lang/IllegalArgumentException","Free requires an owned live base address");return;}
    std::free(entry->second.pointer);total-=entry->second.physical;blocks.erase(entry);
}
extern "C" JNIEXPORT jobject JNICALL Java_dev_andes_lwjgl_NativeMemory_view(JNIEnv* env,jclass,jlong supplied,jint bytes){
    if(bytes<0){fail(env,"java/lang/IllegalArgumentException","Negative view size");return nullptr;}
    std::lock_guard<std::mutex> lock(memoryMutex);void* pointer=range(env,supplied,static_cast<size_t>(bytes));return pointer?env->NewDirectByteBuffer(pointer,bytes):nullptr;
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_andes_lwjgl_NativeMemory_address(JNIEnv* env,jclass,jobject buffer,jlong bytes){
    if(bytes<0){fail(env,"java/lang/IllegalArgumentException","Invalid buffer extent");return 0;}
    void* pointer=env->GetDirectBufferAddress(buffer);
    if(!pointer){if(bytes==0)return 0;fail(env,"java/lang/IllegalArgumentException","Direct buffer required");return 0;}
    std::lock_guard<std::mutex> lock(memoryMutex);
    uintptr_t key=canonical(reinterpret_cast<uintptr_t>(pointer));auto next=blocks.upper_bound(key);
    if(bytes==0 && (next==blocks.begin()||std::prev(next)->first!=key))return 0;
    void* owned=range(env,static_cast<jlong>(reinterpret_cast<uintptr_t>(pointer)),static_cast<size_t>(bytes));return owned?static_cast<jlong>(reinterpret_cast<uintptr_t>(owned)):0;
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeMemory_copy(JNIEnv* env,jclass,jlong source,jlong target,jlong bytes){
    size_t count;if(!size(env,bytes,count)||count==0)return;std::lock_guard<std::mutex> lock(memoryMutex);
    void* from=range(env,source,count);if(!from)return;void* to=range(env,target,count);if(to)std::memmove(to,from,count);
}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeMemory_set(JNIEnv* env,jclass,jlong supplied,jint value,jlong bytes){
    size_t count;if(!size(env,bytes,count)||count==0)return;std::lock_guard<std::mutex> lock(memoryMutex);void* pointer=range(env,supplied,count);if(pointer)std::memset(pointer,value,count);
}
extern "C" JNIEXPORT jbyte JNICALL Java_dev_andes_lwjgl_NativeMemory_getByte(JNIEnv* env,jclass,jlong supplied){std::lock_guard<std::mutex> lock(memoryMutex);void* p=range(env,supplied,1);jbyte value=0;if(p)std::memcpy(&value,p,1);return value;}
extern "C" JNIEXPORT jint JNICALL Java_dev_andes_lwjgl_NativeMemory_getInt(JNIEnv* env,jclass,jlong supplied){std::lock_guard<std::mutex> lock(memoryMutex);void* p=range(env,supplied,4);jint value=0;if(p)std::memcpy(&value,p,4);return value;}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeMemory_putByte(JNIEnv* env,jclass,jlong supplied,jbyte value){std::lock_guard<std::mutex> lock(memoryMutex);void* p=range(env,supplied,1);if(p)std::memcpy(p,&value,1);}
extern "C" JNIEXPORT void JNICALL Java_dev_andes_lwjgl_NativeMemory_putInt(JNIEnv* env,jclass,jlong supplied,jint value){std::lock_guard<std::mutex> lock(memoryMutex);void* p=range(env,supplied,4);if(p)std::memcpy(p,&value,4);}
extern "C" JNIEXPORT jbyteArray JNICALL Java_dev_andes_lwjgl_NativeMemory_utf8(JNIEnv* env,jclass,jlong supplied){
    std::lock_guard<std::mutex> lock(memoryMutex);void* p=range(env,supplied,1);if(!p)return nullptr;
    auto entry=std::prev(blocks.upper_bound(canonical(raw(supplied))));size_t offset=canonical(raw(supplied))-entry->first;
    size_t maximum=std::min<size_t>(entry->second.bytes-offset,1024*1024);void* end=std::memchr(p,0,maximum);
    if(!end){fail(env,"java/lang/IllegalArgumentException","Native UTF-8 string lacks bounded terminator");return nullptr;}
    jsize bytes=static_cast<jsize>(static_cast<unsigned char*>(end)-static_cast<unsigned char*>(p));jbyteArray result=env->NewByteArray(bytes);
    if(result&&bytes)env->SetByteArrayRegion(result,0,bytes,static_cast<jbyte*>(p));return result;
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_andes_lwjgl_NativeMemory_allocatedBytes(JNIEnv*,jclass){std::lock_guard<std::mutex> lock(memoryMutex);return static_cast<jlong>(total);}
