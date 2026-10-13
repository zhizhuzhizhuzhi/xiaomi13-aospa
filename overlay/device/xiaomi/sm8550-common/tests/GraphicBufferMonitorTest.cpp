// SPDX-License-Identifier: Apache-2.0
#include <gtest/gtest.h>
#include <ui/GraphicBuffer.h>
#include <utils/StrongPointer.h>
#include <malloc.h>
#include <atomic>
#include <thread>
#include <vector>
using android::GraphicBuffer;
using android::sp;
TEST(FuxiVendorBufferMonitor, IndependentAndStableAcrossAnotherBufferDestruction) {
    auto first = sp<GraphicBuffer>::make();
    auto second = sp<GraphicBuffer>::make();
    auto* firstMonitor = &first->getDependencyMonitor();
    auto* secondMonitor = &second->getDependencyMonitor();
    EXPECT_NE(firstMonitor, secondMonitor);
    EXPECT_EQ(firstMonitor, &first->getDependencyMonitor());
    first.clear();
    EXPECT_EQ(secondMonitor, &second->getDependencyMonitor());
}
TEST(FuxiVendorBufferMonitor, AliveDuringDeathCallback) {
    struct Context {
        GraphicBuffer* buffer;
        android::DependencyMonitor* monitor;
        bool called = false;
    };
    auto buffer = sp<GraphicBuffer>::make();
    Context ctx{buffer.get(), &buffer->getDependencyMonitor(), false};
    buffer->addDeathCallback([](void* opaque, uint64_t) {
        auto* ctx = static_cast<Context*>(opaque);
        EXPECT_EQ(ctx->monitor, &ctx->buffer->getDependencyMonitor());
        ctx->called = true;
    }, &ctx);
    buffer.clear();
    EXPECT_TRUE(ctx.called);
}
TEST(FuxiVendorBufferMonitor, ConcurrentLookupWithStrongOwnership) {
    auto shared = sp<GraphicBuffer>::make();
    auto* expected = &shared->getDependencyMonitor();
    std::atomic<unsigned> mismatches{0};
    std::vector<std::thread> threads;
    for (int i = 0; i < 8; ++i) {
        threads.emplace_back([shared, expected, &mismatches] {
            for (int j = 0; j < 2000; ++j) {
                if (&shared->getDependencyMonitor() != expected) ++mismatches;
                auto own = sp<GraphicBuffer>::make();
                if (&own->getDependencyMonitor() == expected) ++mismatches;
            }
        });
    }
    for (auto& thread : threads) thread.join();
    EXPECT_EQ(0u, mismatches.load());
}
TEST(FuxiVendorBufferMonitor, RepeatedDestructionDoesNotAccumulateMonitors) {
    auto createAndDrop = [](int count) {
        for (int i = 0; i < count; ++i) {
            auto buffer = sp<GraphicBuffer>::make();
            buffer->getDependencyMonitor().setToken(std::string(128, 'x'));
        }
    };
    createAndDrop(1000);
    const auto before = mallinfo().uordblks;
    createAndDrop(50000);
    const auto after = mallinfo().uordblks;
    printf("live heap before=%zu after=%zu GraphicBuffer size=%zu\n",
           static_cast<size_t>(before), static_cast<size_t>(after), sizeof(GraphicBuffer));
    EXPECT_LE(static_cast<size_t>(after), static_cast<size_t>(before) + 2 * 1024 * 1024);
}
