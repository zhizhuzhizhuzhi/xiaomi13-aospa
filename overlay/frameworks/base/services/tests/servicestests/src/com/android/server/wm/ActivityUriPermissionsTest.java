/*
 * Copyright (C) 2026 The Paranoid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.server.wm;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import com.android.server.uri.CallerUriPermissionSnapshot;
import com.android.server.uri.GrantUri;
import com.android.server.uri.NeededUriGrants;
import com.android.server.uri.UriGrantsManagerInternal;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@EnableFlags(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
public class ActivityUriPermissionsTest {
    @Rule public final SetFlagsRule mFlags = new SetFlagsRule();
    private static final int CALLER = 10123;
    private static final Uri PHOTO = Uri.parse("content://media/external/images/media/1");

    private CallerUriPermissionSnapshot capture(UriGrantsManagerInternal manager) {
        when(manager.checkUriPermission(any(), eq(CALLER),
                eq(FLAG_GRANT_READ_URI_PERMISSION), eq(true))).thenReturn(true);
        return CallerUriPermissionSnapshot.capture(new Intent().setData(PHOTO), CALLER, manager);
    }

    @Test public void noStatePreservesNullGrantSemantics() {
        assertNull(ActivityUriPermissions.create(null, null));
        assertNull(ActivityUriPermissions.grantsOf(null));
        assertNull(ActivityUriPermissions.callerOf(null));
    }

    @Test public void callerAccessDoesNotCreateRecipientGrants() {
        CallerUriPermissionSnapshot caller = capture(mock(UriGrantsManagerInternal.class));
        ActivityUriPermissions state = ActivityUriPermissions.create(null, caller);
        assertNull(state.grants);
        assertSame(caller, state.caller);
    }

    @Test public void mergingRecipientGrantsDoesNotInvalidateCaller() {
        CallerUriPermissionSnapshot caller = capture(mock(UriGrantsManagerInternal.class));
        NeededUriGrants grants = new NeededUriGrants("receiver", 10456, 0);
        ActivityUriPermissions state = ActivityUriPermissions.create(grants, caller);
        grants.merge(new NeededUriGrants("receiver", 10456, 0));
        assertSame(grants, state.grants);
        assertTrue(state.caller.hasPermission(new GrantUri(0, PHOTO, 0),
                FLAG_GRANT_READ_URI_PERMISSION));
    }

    @Test public void callerStateLookupUsesOnlyPrecapturedPermissions() {
        UriGrantsManagerInternal manager = mock(UriGrantsManagerInternal.class);
        CallerUriPermissionSnapshot caller = capture(manager);
        reset(manager);
        IBinder token = mock(IBinder.class);
        ActivityCallerState state = new ActivityCallerState(null);
        state.computeCallerInfo(token, new Intent().setData(PHOTO), CALLER, "caller", false, caller);
        assertTrue(state.checkContentUriPermission(token, new GrantUri(0, PHOTO, 0),
                FLAG_GRANT_READ_URI_PERMISSION));
        verifyNoInteractions(manager);
    }

    @Test public void requestCopyAndRecycleDoNotLeakDispatchState() {
        ActivityStarter.Request request = new ActivityStarter.Request();
        request.uriPermissions = ActivityUriPermissions.create(null,
                capture(mock(UriGrantsManagerInternal.class)));
        ActivityStarter.Request copy = new ActivityStarter.Request();
        copy.set(request);
        assertSame(request.uriPermissions, copy.uriPermissions);
        copy.reset();
        assertNull(copy.uriPermissions);
        assertNotNull(request.uriPermissions);
    }
}
