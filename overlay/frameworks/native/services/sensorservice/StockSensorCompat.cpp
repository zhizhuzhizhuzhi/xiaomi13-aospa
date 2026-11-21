#include <binder/AppOpsManager.h>
#include <utils/String16.h>
#include <optional>
#include <sys/types.h>

// Bridge the original library's AppOps ABI to the platform device-aware API.
extern "C" int32_t stockCheckOpRaw(android::AppOpsManager* manager, int32_t op, int32_t uid,
                                  const android::String16& package,
                                  const std::optional<android::String16>& tag)
        asm("_ZN7android13AppOpsManager10checkOpRawEiiRKNS_8String16ERKNSt3__18optionalIS1_EE");
extern "C" int32_t stockCheckOpRaw(android::AppOpsManager* manager, int32_t op, int32_t uid,
                                  const android::String16& package,
                                  const std::optional<android::String16>& tag) {
    return manager->checkOperationForDevice(op, uid, package, tag, 0);
}

// Xiaomi accounting ABI hooks. FOV integration uses AOSP SensorService's
// existing sensor activation and battery accounting path.
extern "C" void stockTareStart(void*, uid_t, int, int)
        asm("_ZN7android14BatteryService25noteStartSensorByTareImplEjii");
extern "C" void stockTareStart(void*, uid_t, int, int) {}
extern "C" void stockTareStop(void*, uid_t, int, int)
        asm("_ZN7android14BatteryService24noteStopSensorByTareImplEjii");
extern "C" void stockTareStop(void*, uid_t, int, int) {}
extern "C" void stockSsruStart(void*, uid_t, int)
        asm("_ZN7android14BatteryService26noteStartSensorForSsruImplEji");
extern "C" void stockSsruStart(void*, uid_t, int) {}
extern "C" void stockSsruStop(void*, uid_t, int)
        asm("_ZN7android14BatteryService25noteStopSensorForSsruImplEji");
extern "C" void stockSsruStop(void*, uid_t, int) {}
