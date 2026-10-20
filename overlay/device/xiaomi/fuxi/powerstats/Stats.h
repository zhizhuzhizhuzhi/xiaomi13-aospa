// Copyright (C) 2026 Paranoid Android
// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <cstdint>
#include <limits>
#include <optional>

namespace fuxi {
// ABI of drivers/soc/qcom/subsystem_sleep_stats.c. The ioctl encodes a
// pointer size (8), not sizeof(KernelStats); the returned structure is 32 bytes.
struct KernelStats {
    uint32_t version;
    uint32_t count;
    uint64_t lastEntered;
    uint64_t lastExited;
    uint64_t accumulated;
};
static_assert(sizeof(KernelStats) == 32);

inline std::optional<int64_t> ticksToMillis(uint64_t ticks, uint64_t frequency) {
    if (frequency == 0) return std::nullopt;
    const __uint128_t ms = static_cast<__uint128_t>(ticks) * 1000 / frequency;
    if (ms > std::numeric_limits<int64_t>::max()) return std::nullopt;
    return static_cast<int64_t>(ms);
}

struct Residency {
    int64_t durationMs;
    int64_t entryCount;
    int64_t lastEntryMs;
};

inline std::optional<Residency> convertStats(const KernelStats& stats,
        uint64_t frequency, uint64_t counterNow, int64_t bootTimeMs) {
    if (bootTimeMs < 0 || stats.lastEntered > counterNow) return std::nullopt;
    const auto duration = ticksToMillis(stats.accumulated, frequency);
    const auto sinceEntry = ticksToMillis(counterNow - stats.lastEntered, frequency);
    if (!duration || !sinceEntry) return std::nullopt;
    // /dev/stats already includes the ongoing sleep interval. Adding it here
    // again would double-count residency. Map the architectural timer epoch to
    // CLOCK_BOOTTIME for the AIDL timestamp; firmware can enter a state before
    // Linux boots, in which case the first entry is at boot time zero.
    const int64_t entry = stats.count == 0 || *sinceEntry > bootTimeMs
            ? 0 : bootTimeMs - *sinceEntry;
    return Residency{*duration, stats.count, entry};
}
}  // namespace fuxi
