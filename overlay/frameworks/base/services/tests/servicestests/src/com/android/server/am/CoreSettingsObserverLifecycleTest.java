/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.am;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.android.server.am.ActivityManagerService.Injector;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.companion.virtual.IVirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.appop.AppOpsService;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Observer lifecycle tests using a real manager and mocked Binder, without a physical device association. */
@SmallTest
public class CoreSettingsObserverLifecycleTest {
    @Rule
    public ServiceThreadRule mServiceThreadRule = new ServiceThreadRule();

    @Mock private Context mContext;
    @Mock private Resources mResources;
    private CoreSettingsObserver mCoreSettingsObserver;

    @BeforeClass
    public static void loadNativeLibrary() {
        System.loadLibrary("servicestestjni");
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.READ_DEVICE_CONFIG);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        FakeSettingsProvider.clearSettingsProvider();
        Context original = getInstrumentation().getTargetContext();
        when(mContext.getApplicationInfo()).thenReturn(original.getApplicationInfo());
        MockContentResolver resolver = new MockContentResolver(mContext);
        resolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mContext.getContentResolver()).thenReturn(resolver);
        when(mContext.getCacheDir()).thenReturn(original.getCacheDir());
        when(mContext.getAttributionSource()).thenReturn(original.getAttributionSource());
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getXml(anyInt())).thenAnswer(
                invocation -> original.getResources().getXml(invocation.getArgument(0)));
        when(mResources.getStringArray(anyInt())).thenReturn(new String[0]);
        when(mResources.getIntArray(anyInt())).thenReturn(new int[0]);
        TypedArray array = mock(TypedArray.class);
        when(array.length()).thenReturn(1);
        when(mResources.obtainTypedArray(anyInt())).thenReturn(array);
        ActivityManagerService ams = spy(new ActivityManagerService(new TestInjector(mContext),
                mServiceThreadRule.getThread()));
        doReturn(new int[] {UserHandle.USER_SYSTEM}).when(ams).getRunningUserIds();
        mCoreSettingsObserver = CoreSettingsObserver.create(ams);
    }

    @AfterClass
    public static void tearDownOnce() {
        try {
            FakeSettingsProvider.clearSettingsProvider();
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testVirtualDevices_beforeServicesReady_doesNotQueryService() {
        when(mResources.getBoolean(R.bool.config_enableVirtualDeviceManager)).thenReturn(true);
        assertThat(mCoreSettingsObserver.getVirtualDeviceIds().size()).isEqualTo(0);
        verify(mContext, never()).getSystemServiceName(VirtualDeviceManager.class);
    }

    @Test
    public void testVirtualDevices_disabled_doesNotQueryService() {
        mCoreSettingsObserver.onSystemServicesReady();
        assertThat(mCoreSettingsObserver.getVirtualDeviceIds().size()).isEqualTo(0);
        verify(mContext, never()).getSystemServiceName(VirtualDeviceManager.class);
    }

    @Test
    public void testVirtualDevices_enabled_connectsOnceWhenReady() throws Exception {
        when(mResources.getBoolean(R.bool.config_enableVirtualDeviceManager)).thenReturn(true);
        IVirtualDeviceManager service = mock(IVirtualDeviceManager.class);
        when(service.getVirtualDevices()).thenReturn(java.util.List.of());
        VirtualDeviceManager manager = new VirtualDeviceManager(service, mContext);
        when(mContext.getSystemServiceName(VirtualDeviceManager.class))
                .thenReturn(Context.VIRTUAL_DEVICE_SERVICE);
        when(mContext.getSystemService(Context.VIRTUAL_DEVICE_SERVICE)).thenReturn(manager);
        mCoreSettingsObserver.onSystemServicesReady();
        assertThat(mCoreSettingsObserver.getVirtualDeviceIds().size()).isEqualTo(0);
        verify(mContext).getSystemServiceName(VirtualDeviceManager.class);
    }

    @Test(expected = NullPointerException.class)
    public void testVirtualDevices_enabledButNotPublished_fails() {
        when(mResources.getBoolean(R.bool.config_enableVirtualDeviceManager)).thenReturn(true);
        mCoreSettingsObserver.onSystemServicesReady();
    }

    private class TestInjector extends Injector {
        TestInjector(Context context) {
            super(context);
        }

        @Override
        public AppOpsService getAppOpsService(Handler handler) {
            return null;
        }

        @Override
        public Handler getUiHandler(ActivityManagerService service) {
            return mServiceThreadRule.getThread().getThreadHandler();
        }
    }
}
