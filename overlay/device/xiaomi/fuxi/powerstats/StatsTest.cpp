// Copyright (C) 2026 Paranoid Android
// SPDX-License-Identifier: Apache-2.0
#include "Stats.h"
#include <gtest/gtest.h>

TEST(FuxiSleepStats, CounterUnitsAndEpoch) {
    fuxi::KernelStats data{0, 7, 192000000, 211200000, 96000000};
    const auto result = fuxi::convertStats(data, 19200000, 384000000, 18000);
    ASSERT_TRUE(result);
    EXPECT_EQ(5000, result->durationMs);
    EXPECT_EQ(7, result->entryCount);
    EXPECT_EQ(8000, result->lastEntryMs);
}
TEST(FuxiSleepStats, KernelAlreadyIncludesCurrentSleep) {
    fuxi::KernelStats data{0, 1, 19200000, 0, 19200000 * 4};
    const auto result = fuxi::convertStats(data, 19200000, 19200000 * 5, 5000);
    ASSERT_TRUE(result);
    EXPECT_EQ(4000, result->durationMs);
}
TEST(FuxiSleepStats, BeforeLinuxBootEntryAndNeverEntered) {
    fuxi::KernelStats data{0, 1, 1, 0, 100};
    EXPECT_EQ(0, fuxi::convertStats(data, 19200000, 1920000000, 1000)->lastEntryMs);
    data.count = 0;
    EXPECT_EQ(0, fuxi::convertStats(data, 19200000, 1920000000, 1000000)->lastEntryMs);
}
TEST(FuxiSleepStats, InvalidSamplesAndCounterFrequency) {
    fuxi::KernelStats data{0, 1, 500, 0, 100};
    EXPECT_FALSE(fuxi::convertStats(data, 0, 600, 1000));
    EXPECT_FALSE(fuxi::convertStats(data, 19200000, 499, 1000));
    EXPECT_FALSE(fuxi::convertStats(data, 19200000, 600, -1));
}
TEST(FuxiSleepStats, LargeAndFractionalTicksDoNotOverflow) {
    EXPECT_EQ(1000, *fuxi::ticksToMillis(19200001, 19200000));
    EXPECT_EQ(0, *fuxi::ticksToMillis(19199, 19200000));
    ASSERT_TRUE(fuxi::ticksToMillis(UINT64_MAX, 19200000));
    EXPECT_FALSE(fuxi::ticksToMillis(UINT64_MAX, 1));
}
