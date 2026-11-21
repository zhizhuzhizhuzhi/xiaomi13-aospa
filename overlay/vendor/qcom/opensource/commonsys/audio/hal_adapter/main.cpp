/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#include <android/binder_process.h>
#include <stdlib.h>

extern "C" void registerInterface();

int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    registerInterface();
    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;  // should not reach
}
