#include "StockLightFov.h"
#include "SensorInterface.h"

#include <android-base/properties.h>
#include <dlfcn.h>
#include <log/log.h>
#include <new>

namespace android {

// ABI dimensions of the original OS4 LightFovOptimizationSensor implementation.
static_assert(sizeof(Sensor) == 0x70);
static_assert(sizeof(SensorInterface) == 0x10);
static_assert(sizeof(VirtualSensor) == 0x90);
static_assert(sizeof(sensor_t) == 0x68);

std::shared_ptr<SensorInterface> createStockLightFov(const sensor_t* sensors, size_t count,
                                                   bool rear) {
    if (base::GetProperty("ro.product.vendor.device", "") != "pudding") return nullptr;

    const char* property = rear ? "ro.miui.support.back.fov" : "ro.miui.support.light.fov";
    if (!base::GetBoolProperty(property, false)) return nullptr;

    // Retain the library for the lifetime of its virtual sensor objects.
    // Resolve the platform sensor ABI and compatibility hooks before activation.
    static void* library = dlopen("/system_ext/lib64/libmisensorserviceimpl.so",
                                  RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) {
        ALOGE("Stock FOV library load failed: %s", dlerror());
        return nullptr;
    }
    const char* symbol = rear
            ? "_ZN7android30LightBackFovOptimizationSensorC1EPK8sensor_tm"
            : "_ZN7android26LightFovOptimizationSensorC1EPK8sensor_tm";
    using Constructor = void (*)(void*, const sensor_t*, size_t);
    auto construct = reinterpret_cast<Constructor>(dlsym(library, symbol));
    if (construct == nullptr) {
        ALOGE("Stock FOV constructor missing: %s", dlerror());
        return nullptr;
    }
    constexpr size_t kStockFovObjectSize = 0x5d0;
    void* storage = ::operator new(kStockFovObjectSize);
    construct(storage, sensors, count);
    auto sensor = std::shared_ptr<SensorInterface>(static_cast<SensorInterface*>(storage));
    ALOGI("Stock %s FOV ready: type=%d handle=%d", rear ? "rear" : "front",
          sensor->getSensor().getType(), sensor->getSensor().getHandle());
    return sensor;
}

} // namespace android
