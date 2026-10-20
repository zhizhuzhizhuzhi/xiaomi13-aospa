/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.bluetooth;

import android.annotation.FlaggedApi;
import android.annotation.Hide;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.bluetooth.flags.Flags;

import java.util.Objects;

/**
 * A bonded device's identity, connection and audio-profile state read in the Bluetooth process.
 *
 * <p>This is a point-in-time snapshot, not a subscription. Profile connection and alias events
 * require a new query. Group IDs are meaningful only for a connected corresponding profile.
 *
 * @hide
 */
@Hide
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
@FlaggedApi(Flags.FLAG_ASYNC_BONDED_DEVICE_SNAPSHOT)
public final class BluetoothBondedDeviceInfo implements Parcelable {
    private final BluetoothDevice mDevice;
    private final String mAlias;
    private final boolean mConnected;
    private final int[] mConnectedAudioProfiles;
    private final long mHearingAidSyncId;
    private final int mLeAudioGroupId;

    /** Creates a snapshot from the Bluetooth service's current device and profile state. */
    public BluetoothBondedDeviceInfo(@NonNull BluetoothDevice device, @Nullable String alias,
            boolean connected, @NonNull int[] connectedAudioProfiles, long hearingAidSyncId,
            int leAudioGroupId) {
        mDevice = Objects.requireNonNull(device);
        mAlias = alias;
        mConnected = connected;
        mConnectedAudioProfiles = Objects.requireNonNull(connectedAudioProfiles).clone();
        mHearingAidSyncId = hearingAidSyncId;
        mLeAudioGroupId = leAudioGroupId;
    }

    private BluetoothBondedDeviceInfo(Parcel in) {
        this(in.readTypedObject(BluetoothDevice.CREATOR), readAlias(in), in.readBoolean(),
                in.createIntArray(), in.readLong(), in.readInt());
    }

    private static @Nullable String readAlias(Parcel in) {
        CharSequence alias = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        return alias == null ? null : alias.toString();
    }

    /** Returns the bonded device. */
    @RequiresNoPermission
    public @NonNull BluetoothDevice getDevice() { return mDevice; }

    /** Returns the alias reported by the service, or null if no alias is available. */
    @RequiresNoPermission
    public @Nullable String getAlias() { return mAlias; }

    /** Returns whether the service reports a live ACL connection. */
    @RequiresNoPermission
    public boolean isConnected() { return mConnected; }

    /** Returns the connected A2DP, hearing-aid and LE Audio profile IDs. */
    @RequiresNoPermission
    public @NonNull int[] getConnectedAudioProfiles() { return mConnectedAudioProfiles.clone(); }

    /** Returns the hearing-aid sync ID, or HI_SYNC_ID_INVALID without that connected profile. */
    @RequiresNoPermission
    public long getHearingAidSyncId() { return mHearingAidSyncId; }

    /** Returns the LE Audio group ID, or GROUP_ID_INVALID without that connected profile. */
    @RequiresNoPermission
    public int getLeAudioGroupId() { return mLeAudioGroupId; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(mDevice, flags);
        TextUtils.writeToParcel(mAlias, dest, flags);
        dest.writeBoolean(mConnected);
        dest.writeIntArray(mConnectedAudioProfiles);
        dest.writeLong(mHearingAidSyncId);
        dest.writeInt(mLeAudioGroupId);
    }

    public static final @NonNull Creator<BluetoothBondedDeviceInfo> CREATOR =
            new Creator<>() {
                @Override
                public BluetoothBondedDeviceInfo createFromParcel(Parcel in) {
                    return new BluetoothBondedDeviceInfo(in);
                }

                @Override
                public BluetoothBondedDeviceInfo[] newArray(int size) {
                    return new BluetoothBondedDeviceInfo[size];
                }
            };
}
