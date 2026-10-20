/*
 * Copyright (C) 2026 The Paranoid Android Project
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
package com.android.systemui.statusbar.pipeline.ims.data.repository

import android.telephony.SubscriptionManager
import android.telephony.ims.ImsException
import android.telephony.ims.ImsManager
import android.telephony.ims.ImsMmTelManager
import android.telephony.ims.ImsStateCallback
import android.telephony.ims.RegistrationManager.RegistrationCallback
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.util.mockito.whenever
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class ImsRepositoryImplTest : SysuiTestCase() {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(dispatcher)
    @Mock private lateinit var imsManager: ImsManager
    @Mock private lateinit var mmTelManager: ImsMmTelManager
    @Mock private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var stateCallback: ImsStateCallback
    private lateinit var repository: ImsRepositoryImpl

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(imsManager.getImsMmTelManager(SUB_ID)).thenReturn(mmTelManager)
        doAnswer {
            stateCallback = it.getArgument(1)
            null
        }.`when`(mmTelManager).registerImsStateCallback(
            any(Executor::class.java), any(ImsStateCallback::class.java)
        )
        repository = ImsRepositoryImpl(
            SUB_ID, imsManager, subscriptionManager, dispatcher, scope.backgroundScope
        )
    }

    @Test
    fun unsupportedSubscriptionDuringAvailable_doesNotEscapeCallback() = scope.runTest {
        doThrow(ImsException("subscription removed", ImsException.CODE_ERROR_UNSUPPORTED_OPERATION))
            .`when`(mmTelManager).registerImsRegistrationCallback(
                any(Executor::class.java), any(RegistrationCallback::class.java)
            )
        repository.imsState.launchIn(backgroundScope)
        runCurrent()

        stateCallback.onAvailable()
        runCurrent()

        verify(mmTelManager).unregisterImsStateCallback(stateCallback)
        verify(mmTelManager, never()).registerMmTelCapabilityCallback(
            any(Executor::class.java), any(ImsMmTelManager.CapabilityCallback::class.java)
        )
    }

    @Test
    fun capabilityRegistrationFails_cleansUpPartialRegistration() = scope.runTest {
        doThrow(ImsException("subscription removed", ImsException.CODE_ERROR_UNSUPPORTED_OPERATION))
            .`when`(mmTelManager).registerMmTelCapabilityCallback(
                any(Executor::class.java), any(ImsMmTelManager.CapabilityCallback::class.java)
            )
        repository.imsState.launchIn(backgroundScope)
        runCurrent()

        stateCallback.onAvailable()
        runCurrent()

        verify(mmTelManager).unregisterImsRegistrationCallback(any(RegistrationCallback::class.java))
        verify(mmTelManager).unregisterImsStateCallback(stateCallback)
    }

    @Test
    fun repeatedAvailable_doesNotRegisterTwice() = scope.runTest {
        repository.imsState.launchIn(backgroundScope)
        runCurrent()

        stateCallback.onAvailable()
        stateCallback.onAvailable()

        verify(mmTelManager, times(1)).registerImsRegistrationCallback(
            any(Executor::class.java), any(RegistrationCallback::class.java)
        )
        verify(mmTelManager, times(1)).registerMmTelCapabilityCallback(
            any(Executor::class.java), any(ImsMmTelManager.CapabilityCallback::class.java)
        )
    }

    @Test
    fun lateAvailableAfterCancellation_doesNotRegister() = scope.runTest {
        val job = repository.imsState.launchIn(backgroundScope)
        runCurrent()
        job.cancel()
        runCurrent()

        stateCallback.onAvailable()

        verify(mmTelManager, never()).registerImsRegistrationCallback(
            any(Executor::class.java), any(RegistrationCallback::class.java)
        )
    }

    private companion object {
        const val SUB_ID = 2
    }
}
