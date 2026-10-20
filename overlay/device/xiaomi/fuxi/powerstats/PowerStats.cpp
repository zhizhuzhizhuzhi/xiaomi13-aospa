// Copyright (C) 2026 Paranoid Android
// SPDX-License-Identifier: Apache-2.0
#include "Stats.h"

#include <aidl/android/hardware/power/stats/BnPowerStats.h>
#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/binder_ibinder.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

#include <array>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <numeric>
#include <vector>

using namespace aidl::android::hardware::power::stats;
using ndk::ScopedAStatus;

namespace {
struct Source { const char* name; unsigned int command; };
constexpr std::array<Source, 12> kSources{{
    {"apss", 0}, {"modem", 1}, {"adsp", 3}, {"adsp_island", 4},
    {"cdsp", 5}, {"slpi", 6}, {"gpu", 7}, {"display", 8},
    {"slpi_island", 9}, {"aosd", 10}, {"cxsd", 11}, {"ddr", 12},
}};
uint64_t counterNow() {
    uint64_t value;
    asm volatile("isb; mrs %0, cntvct_el0" : "=r"(value));
    return value;
}
uint64_t counterFrequency() {
    uint64_t value;
    asm volatile("mrs %0, cntfrq_el0" : "=r"(value));
    return value;
}
ScopedAStatus noChannels(const std::vector<int32_t>& ids) {
    return ids.empty() ? ScopedAStatus::ok()
            : ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
}

class PowerStats final : public BnPowerStats {
  public:
    PowerStats() : mFd(open("/dev/stats", O_RDONLY | O_CLOEXEC)),
                   mFrequency(counterFrequency()) {
        CHECK(mFd.ok()) << "Cannot open /dev/stats: " << strerror(errno);
        CHECK(mFrequency > 0) << "Invalid architectural counter frequency";
        // Probe actual kernel providers once. Keep this inventory stable even
        // if a subsystem subsequently goes offline.
        for (const auto& source : kSources) {
            fuxi::KernelStats data{};
            if (readSource(source, &data)) mSources.push_back(source);
            else LOG(INFO) << "Sleep statistics unavailable for " << source.name
                           << ": " << strerror(errno);
        }
        CHECK(!mSources.empty()) << "No real sleep statistics providers";
    }

    ScopedAStatus getPowerEntityInfo(std::vector<PowerEntity>* out) override {
        out->clear();
        for (size_t i = 0; i < mSources.size(); ++i) {
            PowerEntity entity;
            entity.id = i;
            entity.name = mSources[i].name;
            State state;
            state.id = 0;
            state.name = "sleep";
            entity.states.push_back(state);
            out->push_back(entity);
        }
        return ScopedAStatus::ok();
    }

    ScopedAStatus getStateResidency(const std::vector<int32_t>& requested,
            std::vector<StateResidencyResult>* out) override {
        out->clear();
        std::vector<int32_t> ids = requested;
        if (ids.empty()) {
            ids.resize(mSources.size());
            std::iota(ids.begin(), ids.end(), 0);
        }
        for (const int32_t id : ids) {
            if (id < 0 || static_cast<size_t>(id) >= mSources.size()) {
                return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_ARGUMENT);
            }
        }
        std::vector<StateResidencyResult> results;
        for (const int32_t id : ids) {
            fuxi::KernelStats data{};
            if (!readSource(mSources[id], &data)) {
                PLOG(ERROR) << "Reading sleep statistics for " << mSources[id].name;
                return ScopedAStatus::fromExceptionCodeWithMessage(
                        EX_ILLEGAL_STATE, "Kernel sleep statistics read failed");
            }
            // Sample the counter before BOOTTIME so rounding cannot place the
            // entry timestamp after the time at which the data is returned.
            const auto counter = counterNow();
            timespec now{};
            if (clock_gettime(CLOCK_BOOTTIME, &now) != 0) {
                return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
            }
            const auto parsed = fuxi::convertStats(data, mFrequency, counter,
                    now.tv_sec * 1000LL + now.tv_nsec / 1000000);
            if (!parsed) return ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
            StateResidency state;
            state.id = 0;
            state.totalTimeInStateMs = parsed->durationMs;
            state.totalStateEntryCount = parsed->entryCount;
            state.lastEntryTimestampMs = parsed->lastEntryMs;
            StateResidencyResult result;
            result.id = id;
            result.stateResidencyData.push_back(state);
            results.push_back(result);
        }
        *out = std::move(results);
        return ScopedAStatus::ok();
    }

    // No calibrated rail energy meters/consumers are available through this
    // driver. Publish no channels rather than fabricated energy measurements.
    ScopedAStatus getEnergyConsumerInfo(std::vector<EnergyConsumer>* out) override {
        out->clear(); return ScopedAStatus::ok();
    }
    ScopedAStatus getEnergyConsumed(const std::vector<int32_t>& ids,
            std::vector<EnergyConsumerResult>* out) override {
        out->clear(); return noChannels(ids);
    }
    ScopedAStatus getEnergyMeterInfo(std::vector<Channel>* out) override {
        out->clear(); return ScopedAStatus::ok();
    }
    ScopedAStatus readEnergyMeter(const std::vector<int32_t>& ids,
            std::vector<EnergyMeasurement>* out) override {
        out->clear(); return noChannels(ids);
    }

    binder_status_t dump(int fd, const char**, uint32_t) override {
        const uid_t uid = AIBinder_getCallingUid();
        if (uid != 0 && uid != 1000 && uid != 2000) return STATUS_PERMISSION_DENIED;
        std::vector<StateResidencyResult> results;
        auto status = getStateResidency({}, &results);
        if (!status.isOk()) return STATUS_FAILED_TRANSACTION;
        dprintf(fd, "Source: /dev/stats; counter_hz=%llu\n",
                static_cast<unsigned long long>(mFrequency));
        for (const auto& result : results) {
            const auto& state = result.stateResidencyData[0];
            dprintf(fd, "%d %s count=%lld total_ms=%lld last_entry_ms=%lld\n", result.id,
                    mSources[result.id].name, static_cast<long long>(state.totalStateEntryCount),
                    static_cast<long long>(state.totalTimeInStateMs),
                    static_cast<long long>(state.lastEntryTimestampMs));
        }
        return STATUS_OK;
    }
  private:
    bool readSource(const Source& source, fuxi::KernelStats* data) const {
        return ioctl(mFd.get(), _IOC(_IOC_READ, 0x9d, source.command, sizeof(void*)), data) == 0;
    }
    android::base::unique_fd mFd;
    const uint64_t mFrequency;
    std::vector<Source> mSources;
};
}  // namespace

int main(int argc, char** argv) {
    android::base::InitLogging(argv, android::base::LogdLogger(android::base::SYSTEM));
    (void)argc;
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    const auto service = ndk::SharedRefBase::make<PowerStats>();
    const std::string name = std::string(IPowerStats::descriptor) + "/default";
    CHECK_EQ(AServiceManager_addService(service->asBinder().get(), name.c_str()), STATUS_OK);
    ABinderProcess_joinThreadPool();
    return 1;
}
