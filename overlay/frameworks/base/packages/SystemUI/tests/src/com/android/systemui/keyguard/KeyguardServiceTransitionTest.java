/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.keyguard;

import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_SLEEP;
import static android.view.WindowManager.TRANSIT_TO_BACK;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import android.os.Binder;
import android.os.IBinder;
import android.testing.TestableLooper.RunWithLooper;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.SurfaceControl;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.TransitionInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@SmallTest
@RunWith(AndroidJUnit4.class)
@RunWithLooper
public class KeyguardServiceTransitionTest extends SysuiTestCase {
    private final KeyguardViewMediator mMediator = mock(KeyguardViewMediator.class);
    private final IRemoteAnimationRunner mRunner = mock(IRemoteAnimationRunner.class);
    private final ArrayList<IRemoteAnimationFinishedCallback> mRunnerFinished = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        doAnswer(invocation -> {
            mRunnerFinished.add(invocation.getArgument(4));
            return null;
        }).when(mRunner).onAnimationStart(anyInt(), any(), any(), any(), any());
    }

    @Test
    public void exitCancellation_waitsForRunnerCleanup() throws Exception {
        IRemoteTransition transition = KeyguardService.wrap(mMediator, mRunner,
                false /* finishOnCancel */);
        IBinder token = new Binder();
        IRemoteTransitionFinishedCallback finished = start(transition, token);

        cancelWithSleep(transition, token);

        verify(mRunner).onAnimationCancelled();
        verifyNoInteractions(finished);

        // The main-thread cancellation handler has now stopped the animators and cleared targets.
        mRunnerFinished.get(0).onAnimationFinished();

        verify(finished, times(1)).onTransitionFinished(isNull(), isNull());
    }

    @Test
    public void otherLegacyRunnerCancellation_finishesDirectly() throws Exception {
        IRemoteTransition transition = KeyguardService.wrap(mMediator, mRunner);
        IBinder token = new Binder();
        IRemoteTransitionFinishedCallback finished = start(transition, token);

        cancelWithSleep(transition, token);

        verify(mRunner).onAnimationCancelled();
        verify(finished, times(1)).onTransitionFinished(isNull(), isNull());
    }

    @Test
    public void exitCancellation_repeatedCompletionFinishesOnlyOnce() throws Exception {
        IRemoteTransition transition = KeyguardService.wrap(mMediator, mRunner,
                false /* finishOnCancel */);
        IBinder token = new Binder();
        IRemoteTransitionFinishedCallback finished = start(transition, token);
        cancelWithSleep(transition, token);

        mRunnerFinished.get(0).onAnimationFinished();
        mRunnerFinished.get(0).onAnimationFinished();

        verify(finished, times(1)).onTransitionFinished(isNull(), isNull());
    }

    @Test
    public void exitNormalCompletion_finishesWithoutCancellation() throws Exception {
        IRemoteTransition transition = KeyguardService.wrap(mMediator, mRunner,
                false /* finishOnCancel */);
        IRemoteTransitionFinishedCallback finished = start(transition, new Binder());

        mRunnerFinished.get(0).onAnimationFinished();

        verify(finished, times(1)).onTransitionFinished(isNull(), isNull());
    }

    @Test
    public void exitCancellation_canBeFollowedByAnotherTransition() throws Exception {
        IRemoteTransition transition = KeyguardService.wrap(mMediator, mRunner,
                false /* finishOnCancel */);
        IBinder first = new Binder();
        IRemoteTransitionFinishedCallback firstFinished = start(transition, first);
        cancelWithSleep(transition, first);
        mRunnerFinished.get(0).onAnimationFinished();

        IRemoteTransitionFinishedCallback secondFinished = start(transition, new Binder());
        assertEquals(2, mRunnerFinished.size());
        mRunnerFinished.get(0).onAnimationFinished();
        verifyNoInteractions(secondFinished);
        mRunnerFinished.get(1).onAnimationFinished();

        verify(firstFinished, times(1)).onTransitionFinished(isNull(), isNull());
        verify(secondFinished, times(1)).onTransitionFinished(isNull(), isNull());
    }

    private IRemoteTransitionFinishedCallback start(IRemoteTransition transition, IBinder token)
            throws Exception {
        IRemoteTransitionFinishedCallback finished = mock(IRemoteTransitionFinishedCallback.class);
        // Empty targets isolate runner/transition ownership from native SurfaceControl creation.
        transition.startAnimation(token,
                new TransitionInfo(TRANSIT_TO_BACK, TRANSIT_FLAG_KEYGUARD_GOING_AWAY),
                mock(SurfaceControl.Transaction.class), finished);
        return finished;
    }

    private void cancelWithSleep(IRemoteTransition transition, IBinder token) throws Exception {
        transition.mergeAnimation(new Binder(), new TransitionInfo(TRANSIT_SLEEP, 0),
                mock(SurfaceControl.Transaction.class), token,
                mock(IRemoteTransitionFinishedCallback.class));
    }
}
