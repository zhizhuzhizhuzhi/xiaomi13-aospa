/*
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.leashtest;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceControl;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LeashRaceInstrumentation extends Instrumentation {
    private static final int ORDERED = 64;
    private static final int CONCURRENT = 256;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    private SurfaceControl surface(String name, SurfaceControl parent) {
        return new SurfaceControl.Builder().setName(name).setContainerLayer()
                .setHidden(true).setParent(parent).build();
    }

    private void await(CountDownLatch signal) throws Exception {
        if (!signal.await(10, TimeUnit.SECONDS)) throw new AssertionError("Event timeout");
    }

    private CountDownLatch onCommit(SurfaceControl.Transaction transaction) {
        CountDownLatch committed = new CountDownLatch(1);
        transaction.addTransactionCommittedListener(Runnable::run, committed::countDown);
        return committed;
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        SurfaceControl parent = null;
        try {
            parent = surface("leash-race-parent", null);
            SurfaceControl control = surface("leash-race-old-control", parent);
            control.release();
            boolean oldFailed = false;
            try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                try { t.reparent(control, null); }
                catch (NullPointerException expected) { oldFailed = true; }
            }
            if (!oldFailed) throw new AssertionError("Old failure control did not reproduce");
            result.putBoolean("old_failure_control", true);

            int skipped = 0;
            for (int i = 0; i < ORDERED; i++) {
                SurfaceControl leash = surface("released-before-prepare-" + i, parent);
                leash.release();
                if (leash.isValid()) throw new AssertionError("Released wrapper is valid");
                // Same validity boundary as production; there is no operation to record.
                if (leash.isValid()) {
                    try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                        t.reparent(leash, null);
                    }
                } else { skipped++; }
            }
            result.putInt("released_before_prepare_skipped", skipped);

            for (int i = 0; i < ORDERED; i++) {
                SurfaceControl leash = surface("release-before-submit-" + i, parent);
                try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                    t.reparent(leash, null);
                    CountDownLatch committed = onCommit(t);
                    leash.release();
                    t.apply();
                    await(committed);
                } finally { leash.release(); }
            }
            result.putInt("release_before_submit_committed", ORDERED);

            for (int i = 0; i < ORDERED; i++) {
                SurfaceControl leash = surface("submit-before-release-" + i, parent);
                try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                    t.reparent(leash, null);
                    CountDownLatch committed = onCommit(t);
                    t.apply();
                    await(committed);
                    leash.release();
                } finally { leash.release(); }
            }
            result.putInt("submit_before_release_committed", ORDERED);

            int overlap = 0, releaseFirst = 0, applyFirst = 0;
            for (int i = 0; i < CONCURRENT; i++) {
                SurfaceControl leash = surface("parallel-release-submit-" + i, parent);
                try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                    t.reparent(leash, null);
                    CountDownLatch committed = onCommit(t);
                    CyclicBarrier start = new CyclicBarrier(3);
                    long[] times = new long[4];
                    Future<?> release = workers.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        times[0] = System.nanoTime(); leash.release(); times[1] = System.nanoTime();
                        return null;
                    });
                    Future<?> submit = workers.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        times[2] = System.nanoTime(); t.apply(); times[3] = System.nanoTime();
                        return null;
                    });
                    start.await(10, TimeUnit.SECONDS);
                    release.get(10, TimeUnit.SECONDS);
                    submit.get(10, TimeUnit.SECONDS);
                    await(committed);
                    if (Math.max(times[0], times[2]) < Math.min(times[1], times[3])) overlap++;
                    else if (times[1] <= times[2]) releaseFirst++;
                    else applyFirst++;
                } finally { leash.release(); }
            }
            result.putInt("concurrent_release_submit_committed", CONCURRENT);
            result.putInt("overlapping_operation_windows", overlap);
            result.putInt("release_finished_before_apply_started", releaseFirst);
            result.putInt("apply_finished_before_release_started", applyFirst);

            Handler main = new Handler(Looper.getMainLooper());
            for (int i = 0; i < ORDERED; i++) {
                SurfaceControl leash = surface("main-queue-release-cleanup-" + i, parent);
                CountDownLatch submitted = new CountDownLatch(1);
                AtomicReference<Throwable> error = new AtomicReference<>();
                try (SurfaceControl.Transaction t = new SurfaceControl.Transaction()) {
                    CountDownLatch committed = onCommit(t);
                    runOnMainSync(() -> {
                        try {
                            t.reparent(leash, null);
                            main.post(leash::release);
                            main.post(() -> {
                                try { t.apply(); }
                                catch (Throwable failure) { error.set(failure); }
                                finally { submitted.countDown(); }
                            });
                        } catch (Throwable failure) { error.set(failure); submitted.countDown(); }
                    });
                    await(submitted);
                    if (error.get() != null) throw new AssertionError(error.get());
                    await(committed);
                } finally { leash.release(); }
            }
            result.putInt("main_queue_release_before_cleanup_committed", ORDERED);
            result.putInt("total_cases", ORDERED * 4 + CONCURRENT);
            result.putInt("total_commits", ORDERED * 3 + CONCURRENT);
            result.putString("result", "PASS");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            result.putString("error", android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            workers.shutdownNow();
            if (parent != null) parent.release();
        }
    }
}
