/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.uri;

import android.content.ClipData;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.BadParcelableException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import java.util.ArrayList;

/** Caller access sampled before entering the WM global lock; never grants URI access. */
public final class CallerUriPermissionSnapshot {
    private static final String TAG = "CallerUriPermissions";
    private final int mUid;
    private final ArrayMap<GrantUri, Integer> mAccess = new ArrayMap<>();

    private CallerUriPermissionSnapshot(int uid) {
        mUid = uid;
    }

    /** Must be called together with grant collection, outside the WM global lock. */
    public static CallerUriPermissionSnapshot capture(Intent intent, int callerUid,
            UriGrantsManagerInternal grantsManager) {
        if (!android.security.Flags.contentUriPermissionApis() || callerUid < 0) return null;
        final ArraySet<Uri> uris = getContentUrisFromIntent(intent);
        if (uris.isEmpty()) return null;

        final CallerUriPermissionSnapshot snapshot = new CallerUriPermissionSnapshot(callerUid);
        for (int i = 0; i < uris.size(); i++) {
            final GrantUri uri = new GrantUri(
                    ContentProvider.getUserIdFromUri(uris.valueAt(i),
                            UserHandle.getUserId(callerUid)),
                    ContentProvider.getUriWithoutUserId(uris.valueAt(i)), 0);
            int access = 0;
            if (grantsManager.checkUriPermission(uri, callerUid,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION, true /* fullAccess */)) {
                access |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
            }
            if (grantsManager.checkUriPermission(uri, callerUid,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION, true /* fullAccess */)) {
                access |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            }
            snapshot.mAccess.put(uri, access);
        }
        return snapshot;
    }

    public boolean isForUid(int uid) {
        return uid == mUid;
    }

    public boolean hasPermission(GrantUri uri, int modeFlags) {
        final Integer access = mAccess.get(uri);
        return access != null && (access & modeFlags) == modeFlags;
    }

    public static ArraySet<Uri> getContentUrisFromIntent(Intent intent) {
        final ArraySet<Uri> uris = new ArraySet<>();
        if (intent == null) return uris;

        // getData
        addUriIfContentUri(intent.getData(), uris);

        // EXTRA_STREAM
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            final ArrayList<Uri> streams = tryToUnparcelArrayListExtraStreamsUri(intent);
            if (streams == null) {
                addUriIfContentUri(tryToUnparcelExtraStreamUri(intent), uris);
            } else {
                for (int i = streams.size() - 1; i >= 0; i--) {
                    addUriIfContentUri(streams.get(i), uris);
                }
            }
        }

        final ClipData clipData = intent.getClipData();
        if (clipData == null) return uris;

        for (int i = 0; i < clipData.getItemCount(); i++) {
            final ClipData.Item item = clipData.getItemAt(i);

            // getUri
            addUriIfContentUri(item.getUri(), uris);

            // getIntent
            uris.addAll(getContentUrisFromIntent(item.getIntent()));
        }
        return uris;
    }

    private static Uri tryToUnparcelExtraStreamUri(Intent intent) {
        try {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        } catch (BadParcelableException e) {
            // Even though the system "defuses" all the parsed Bundles, i.e. suppresses and logs
            // instances of {@link BadParcelableException}, we still want to be on the safer side
            // and catch the exception to ensure no breakages happen. If the unparcel fails, the
            // item is still preserved with the underlying parcel.
            Slog.w(TAG, "Failed to unparcel an URI in EXTRA_STREAM, returning null: " + e);
            return null;
        }
    }

    private static ArrayList<Uri> tryToUnparcelArrayListExtraStreamsUri(Intent intent) {
        try {
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
        } catch (BadParcelableException e) {
            // Even though the system "defuses" all the parsed Bundles, i.e. suppresses and logs
            // instances of {@link BadParcelableException}, we still want to be on the safer side
            // and catch the exception to ensure no breakages happen. If the unparcel fails, the
            // item is still preserved with the underlying parcel.
            Slog.w(TAG, "Failed to unparcel an ArrayList of URIs in EXTRA_STREAM, returning null: "
                    + e);
            return null;
        }
    }

    private static void addUriIfContentUri(Uri uri, ArraySet<Uri> uris) {
        if (uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            uris.add(uri);
        }
    }

}
