#pragma once

#include <hardware/sensors.h>
#include <memory>

namespace android {
class SensorInterface;
std::shared_ptr<SensorInterface> createStockLightFov(const sensor_t* sensors, size_t count,
                                                   bool rear);
}
