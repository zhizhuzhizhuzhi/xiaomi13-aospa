/*
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.leashtest;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.view.SurfaceControl;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LeashLifetimeInstrumentation extends Instrumentation {
    private static final int ROUNDS = 32;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    private SurfaceControl surface(String name, SurfaceControl parent) {
        return new SurfaceControl.Builder().setName(name).setContainerLayer()
                .setHidden(true).setParent(parent).build();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            SurfaceControl parent = surface("leash-regression-parent", null);
            try {
                SurfaceControl released = surface("leash-regression-old", parent);
                released.release();
                boolean reproduced = false;
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    try {
                        transaction.reparent(released, null);
                    } catch (NullPointerException expected) {
                        reproduced = true;
                    }
                }
                if (!reproduced) throw new AssertionError("Old released-wrapper crash not reproduced");
                result.putBoolean("old_path_null_pointer_reproduced", true);

                for (int i = 0; i < ROUNDS; i++) {
                    SurfaceControl leash = surface("leash-regression-prepared-" + i, parent);
                    try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                        // Same ownership boundary as animation finish: prepare, release, submit.
                        transaction.reparent(leash, null);
                        leash.release();
                        if (leash.isValid()) throw new AssertionError("Wrapper was not released");
                        CountDownLatch committed = new CountDownLatch(1);
                        transaction.addTransactionCommittedListener(Runnable::run, committed::countDown);
                        transaction.apply();
                        if (!committed.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("SurfaceFlinger did not commit round " + i);
                        }
                    } finally {
                        leash.release();
                    }
                }
            } finally {
                parent.release();
            }
            result.putInt("prepared_transactions_committed_after_wrapper_release", ROUNDS);
            result.putString("result", "PASS");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("result", "FAIL");
            result.putString("error", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
