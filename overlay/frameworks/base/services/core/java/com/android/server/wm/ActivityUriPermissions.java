/*
 * Copyright (C) 2026 The Paranoid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.server.wm;

import android.annotation.Nullable;

import com.android.server.uri.CallerUriPermissionSnapshot;
import com.android.server.uri.NeededUriGrants;

/** Recipient grants and caller access for one activity launch or result delivery. */
final class ActivityUriPermissions {
    // Recipient grants and the sender's access at dispatch time are independent.
    final NeededUriGrants grants;
    final CallerUriPermissionSnapshot caller;

    private ActivityUriPermissions(NeededUriGrants grants, CallerUriPermissionSnapshot caller) {
        this.grants = grants;
        this.caller = caller;
    }

    @Nullable
    static ActivityUriPermissions create(NeededUriGrants grants,
            CallerUriPermissionSnapshot caller) {
        return grants == null && caller == null ? null : new ActivityUriPermissions(grants, caller);
    }

    @Nullable
    static NeededUriGrants grantsOf(ActivityUriPermissions permissions) {
        return permissions == null ? null : permissions.grants;
    }

    @Nullable
    static CallerUriPermissionSnapshot callerOf(ActivityUriPermissions permissions) {
        return permissions == null ? null : permissions.caller;
    }
}
