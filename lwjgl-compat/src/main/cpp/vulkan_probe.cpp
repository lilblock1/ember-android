#include <jni.h>
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <dlfcn.h>
#include <array>
#include <cstring>
#include <vector>

namespace {
struct Driver {
    void* library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    VkInstance instance = VK_NULL_HANDLE;
    PFN_vkDestroyInstance destroy = nullptr;
    ~Driver() {
        if (instance && destroy) destroy(instance, nullptr);
        if (library) dlclose(library);
    }
};
void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
}
extern "C" JNIEXPORT jlongArray JNICALL Java_dev_andes_lwjgl_VulkanProbe_inspect(JNIEnv* env, jclass) {
    Driver driver;
    if (!driver.library) return env->NewLongArray(0);
    auto get = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(driver.library, "vkGetInstanceProcAddr"));
    if (!get) { fail(env, "Vulkan loader entry point unavailable"); return nullptr; }
    auto create = reinterpret_cast<PFN_vkCreateInstance>(get(nullptr, "vkCreateInstance"));
    VkApplicationInfo application{};
    application.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    application.pApplicationName = "Ember capability probe";
    application.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    info.pApplicationInfo = &application;
    if (!create || create(&info, nullptr, &driver.instance) != VK_SUCCESS) {
        fail(env, "Vulkan instance creation failed"); return nullptr;
    }
    driver.destroy = reinterpret_cast<PFN_vkDestroyInstance>(get(driver.instance, "vkDestroyInstance"));
    auto enumerate = reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(get(driver.instance, "vkEnumeratePhysicalDevices"));
    auto properties = reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(get(driver.instance, "vkGetPhysicalDeviceProperties"));
    auto features = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures>(get(driver.instance, "vkGetPhysicalDeviceFeatures"));
    auto extensions = reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(get(driver.instance, "vkEnumerateDeviceExtensionProperties"));
    if (!driver.destroy || !enumerate || !properties || !features || !extensions) {
        fail(env, "Vulkan inspection entry points unavailable"); return nullptr;
    }
    uint32_t count = 0;
    if (enumerate(driver.instance, &count, nullptr) != VK_SUCCESS || count > 16) {
        fail(env, "Vulkan device enumeration failed or exceeded limit"); return nullptr;
    }
    std::array<VkPhysicalDevice,16> devices{};
    if (count && enumerate(driver.instance, &count, devices.data()) != VK_SUCCESS) {
        fail(env, "Vulkan devices changed during enumeration"); return nullptr;
    }
    std::vector<jlong> values;
    const char* wanted[] = {"VK_EXT_transform_feedback", "VK_EXT_conditional_rendering", "VK_EXT_depth_clip_enable",
        "VK_EXT_robustness2", "VK_KHR_robustness2", "VK_EXT_custom_border_color", "VK_EXT_provoking_vertex"};
    for (uint32_t i=0; i<count; ++i) {
        VkPhysicalDeviceProperties p{}; VkPhysicalDeviceFeatures f{};
        properties(devices[i], &p); features(devices[i], &f);
        const VkBool32 selected[] = {f.logicOp, f.fillModeNonSolid, f.alphaToOne, f.shaderClipDistance,
            f.independentBlend, f.depthClamp, f.geometryShader, f.shaderTessellationAndGeometryPointSize};
        jlong bits = 0;
        for (unsigned j=0; j<8; ++j) if (selected[j]) bits |= 1LL << j;
        uint32_t extensionCount = 0;
        if (extensions(devices[i], nullptr, &extensionCount, nullptr) != VK_SUCCESS || extensionCount > 4096) {
            fail(env, "Vulkan extension list unavailable or exceeded limit"); return nullptr;
        }
        std::vector<VkExtensionProperties> available(extensionCount);
        if (extensionCount && extensions(devices[i], nullptr, &extensionCount, available.data()) != VK_SUCCESS) {
            fail(env, "Vulkan extension list changed during enumeration"); return nullptr;
        }
        jlong extensionBits = 0;
        for (unsigned j=0; j<7; ++j) for (uint32_t k=0; k<extensionCount; ++k)
            if (std::strcmp(wanted[j], available[k].extensionName)==0) extensionBits |= 1LL << j;
        values.insert(values.end(), {static_cast<jlong>(p.apiVersion), bits,
            static_cast<jlong>(p.limits.maxPerStageDescriptorSamplers), static_cast<jlong>(p.limits.maxPerStageDescriptorUniformBuffers),
            extensionBits, static_cast<jlong>(p.vendorID), static_cast<jlong>(p.deviceType)});
    }
    jlongArray result = env->NewLongArray(static_cast<jsize>(values.size()));
    if (result && !values.empty()) env->SetLongArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    return result;
}
