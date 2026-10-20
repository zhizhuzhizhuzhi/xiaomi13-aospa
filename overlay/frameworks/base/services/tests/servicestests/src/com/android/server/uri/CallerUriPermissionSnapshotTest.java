/*
 * Copyright (C) 2026 The Paranoid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.server.uri;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;

@SmallTest
@RunWith(JUnit4.class)
@EnableFlags(android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS)
public class CallerUriPermissionSnapshotTest {
    private static final int CALLER = 10123;
    private static final int TARGET = 10456;
    private static final Uri PHOTO = Uri.parse("content://media/external/images/media/1");
    private UriGrantsManagerInternal mManager;
    @Rule public final SetFlagsRule mFlags = new SetFlagsRule();

    @Before
    public void setUp() {
        mManager = mock(UriGrantsManagerInternal.class);
        when(mManager.checkUriPermission(any(), eq(CALLER),
                eq(FLAG_GRANT_READ_URI_PERMISSION), eq(true))).thenReturn(true);
    }

    private CallerUriPermissionSnapshot capture(Intent intent) {
        return CallerUriPermissionSnapshot.capture(intent, CALLER, mManager);
    }

    private static GrantUri grant(Uri uri) {
        return new GrantUri(0, uri, 0);
    }

    @Test
    public void readAndWriteAreSampledIndependently() {
        CallerUriPermissionSnapshot snapshot = capture(new Intent().setData(PHOTO));
        assertTrue(snapshot.hasPermission(grant(PHOTO),
                FLAG_GRANT_READ_URI_PERMISSION));
        assertFalse(snapshot.hasPermission(grant(PHOTO),
                FLAG_GRANT_WRITE_URI_PERMISSION));
    }

    @Test
    public void deniedAccessIsNotInvented() {
        reset(mManager);
        CallerUriPermissionSnapshot snapshot = capture(new Intent().setData(PHOTO));
        assertFalse(snapshot.hasPermission(grant(PHOTO),
                FLAG_GRANT_READ_URI_PERMISSION));
        assertFalse(snapshot.hasPermission(grant(PHOTO),
                FLAG_GRANT_WRITE_URI_PERMISSION));
    }

    @Test
    public void unknownCallerDoesNotBorrowSystemIdentity() {
        assertNull(CallerUriPermissionSnapshot.capture(new Intent().setData(PHOTO), -1, mManager));
        verifyNoInteractions(mManager);
    }

    @Test
    public void accessIsSampledOnceAndBoundToSenderUid() {
        CallerUriPermissionSnapshot snapshot = capture(new Intent().setData(PHOTO));
        reset(mManager);
        assertTrue(snapshot.isForUid(CALLER));
        assertFalse(snapshot.isForUid(TARGET));
        assertTrue(snapshot.hasPermission(grant(PHOTO),
                FLAG_GRANT_READ_URI_PERMISSION));
        verifyNoInteractions(mManager);
    }

    @Test
    public void embeddedUserIdIsPreserved() {
        Uri secondary = Uri.parse("content://10@media/external/images/media/1");
        CallerUriPermissionSnapshot snapshot = capture(new Intent().setData(secondary));
        GrantUri expected = new GrantUri(10, PHOTO, 0);
        verify(mManager).checkUriPermission(eq(expected), eq(CALLER),
                eq(FLAG_GRANT_READ_URI_PERMISSION), eq(true));
        assertTrue(snapshot.hasPermission(expected,
                FLAG_GRANT_READ_URI_PERMISSION));
        assertFalse(snapshot.hasPermission(grant(PHOTO),
                FLAG_GRANT_READ_URI_PERMISSION));
    }

    @Test
    public void dataStreamAndNestedClipUrisAreSampled() {
        Uri stream = Uri.parse("content://media/external/images/media/2");
        Uri nested = Uri.parse("content://media/external/images/media/3");
        Intent intent = new Intent().setData(PHOTO);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,
                new ArrayList<>(Arrays.asList(stream)));
        ClipData clip = ClipData.newRawUri("photo", PHOTO);
        clip.addItem(new ClipData.Item(new Intent().setData(nested)));
        intent.setClipData(clip);
        CallerUriPermissionSnapshot snapshot = capture(intent);
        for (Uri uri : Arrays.asList(PHOTO, stream, nested)) {
            assertTrue(snapshot.hasPermission(grant(uri),
                    FLAG_GRANT_READ_URI_PERMISSION));
        }
        verify(mManager, times(6)).checkUriPermission(any(), eq(CALLER), anyInt(), eq(true));
    }

    @Test
    public void nonContentUrisDoNotCreateMetadata() {
        assertNull(capture(new Intent().setData(Uri.parse("https://example.com"))));
        assertNull(capture(null));
        verifyNoInteractions(mManager);
    }

    @Test
    public void independentDispatchesDoNotMutateOldSnapshot() {
        CallerUriPermissionSnapshot first = capture(new Intent().setData(PHOTO));
        reset(mManager);
        CallerUriPermissionSnapshot second = capture(new Intent().setData(PHOTO));
        assertTrue(first.hasPermission(grant(PHOTO), FLAG_GRANT_READ_URI_PERMISSION));
        assertFalse(second.hasPermission(grant(PHOTO), FLAG_GRANT_READ_URI_PERMISSION));
    }
}
