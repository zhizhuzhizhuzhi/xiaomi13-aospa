/*
 * Copyright (C) 2020 The Android Open Source Project
 * Copyright (C) 2023 Paranoid Android
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

package com.android.server.biometrics.sensors.face.sense;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.SynchronousUserSwitchObserver;
import android.app.UserSwitchObserver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.hardware.biometrics.BiometricConstants;
import android.hardware.biometrics.BiometricFaceConstants;
import android.hardware.biometrics.BiometricsProtoEnums;
import android.hardware.biometrics.ITestSession;
import android.hardware.biometrics.ITestSessionCallback;
import android.hardware.biometrics.face.V1_0.IBiometricsFace;
import android.hardware.face.Face;
import android.hardware.face.FaceAuthenticateOptions;
import android.hardware.face.FaceEnrollOptions;
import android.hardware.face.FaceSensorPropertiesInternal;
import android.hardware.face.IFaceServiceReceiver;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Slog;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;
import android.view.Surface;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.biometrics.AuthenticationStatsCollector;
import com.android.server.biometrics.SensorServiceStateProto;
import com.android.server.biometrics.SensorStateProto;
import com.android.server.biometrics.UserStateProto;
import com.android.server.biometrics.Utils;
import com.android.server.biometrics.log.BiometricContext;
import com.android.server.biometrics.log.BiometricLogger;
import com.android.server.biometrics.sensors.AcquisitionClient;
import com.android.server.biometrics.sensors.AuthenticationConsumer;
import com.android.server.biometrics.sensors.BaseClientMonitor;
import com.android.server.biometrics.AuthenticationStatsBroadcastReceiver;
import com.android.server.biometrics.sensors.BiometricNotificationUtils;
import com.android.server.biometrics.sensors.BiometricScheduler;
import com.android.server.biometrics.sensors.BiometricStateCallback;
import com.android.server.biometrics.sensors.ClientMonitorCallback;
import com.android.server.biometrics.sensors.ClientMonitorCallbackConverter;
import com.android.server.biometrics.sensors.ClientMonitorCompositeCallback;
import com.android.server.biometrics.sensors.EnumerateConsumer;
import com.android.server.biometrics.sensors.ErrorConsumer;
import com.android.server.biometrics.sensors.LockoutResetDispatcher;
import com.android.server.biometrics.sensors.LockoutTracker;
import com.android.server.biometrics.sensors.PerformanceTracker;
import com.android.server.biometrics.sensors.RemovalConsumer;
import com.android.server.biometrics.sensors.face.FaceUtils;
import com.android.server.biometrics.sensors.face.LockoutHalImpl;
import com.android.server.biometrics.sensors.face.ServiceProvider;
import com.android.server.biometrics.sensors.face.UsageStats;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

import vendor.aospa.biometrics.face.ISenseService;
import vendor.aospa.biometrics.face.ISenseServiceReceiver;

public class SenseProvider implements ServiceProvider {

    private static final String TAG = "SenseProvider";

    private static final String BIND_SENSE_ACTION = "co.aospa.sense.BIND";
    private static final String PACKAGE_NAME = "co.aospa.sense";
    private static final String SERVICE_NAME = "co.aospa.sense.SenseService";

    public static final int DEVICE_ID = 1008;
    private static final int ENROLL_TIMEOUT_SEC = 75;
    private static final int GENERATE_CHALLENGE_REUSE_INTERVAL_MILLIS = 60 * 1000;
    private static final int GENERATE_CHALLENGE_COUNTER_TTL_MILLIS =
            FaceGenerateChallengeClient.CHALLENGE_TIMEOUT_SEC * 1000;
    @VisibleForTesting
    public static Clock sSystemClock = Clock.systemUTC();

    private final SparseArray<SenseServiceConnection> mConnections = new SparseArray<>();
    private final SparseArray<ChallengeState> mChallenges = new SparseArray<>();
    private final SparseArray<IBinder> mRejectedBinders = new SparseArray<>();
    private long mConnectionGeneration;
    private volatile BaseClientMonitor mCallbackClient;
    private static final class ChallengeState {
        final List<Long> count = new ArrayList<>();
        FaceGenerateChallengeClient cache;
    }
    private ChallengeState challenge(int userId) {
        ChallengeState state = mChallenges.get(userId);
        if (state == null) { state = new ChallengeState(); mChallenges.put(userId, state); }
        return state;
    }
    private boolean mTestHalEnabled;

    @NonNull private final FaceSensorPropertiesInternal mSensorProperties;
    @NonNull private final BiometricStateCallback mBiometricStateCallback;
    @NonNull private final Context mContext;
    @NonNull private final BiometricScheduler mScheduler;
    @NonNull private final Handler mHandler;
    @NonNull private final LockoutHalImpl mLockoutTracker;
    private final LockoutResetDispatcher mLockoutResetDispatcher;
    @NonNull private final UsageStats mUsageStats;
    @NonNull private final Map<Integer, Long> mAuthenticatorIds;
    @Nullable private IBiometricsFace mDaemon;
    @NonNull private final HalResultController mHalResultController;
    @NonNull private final BiometricContext mBiometricContext;
    @Nullable private AuthenticationStatsCollector mAuthenticationStatsCollector;
    SparseArray<ISenseService> mServices;
    // for requests that do not use biometric prompt
    @NonNull private final AtomicLong mRequestCounter = new AtomicLong(0);
    private int mCurrentUserId = UserHandle.USER_NULL;
    private final int mSensorId;

    private final UserSwitchObserver mUserSwitchObserver = new SynchronousUserSwitchObserver() {
        @Override
        public void onUserSwitching(int newUserId) {
            mHandler.post(() -> {
                mCurrentUserId = newUserId;
                final BaseClientMonitor current = mScheduler.getCurrentClient();
                if (current != null && current.getTargetUserId() != newUserId) {
                    if (current instanceof FaceAuthenticationClient) {
                        mScheduler.cancelAuthenticationOrDetection(current.getToken(), current.getRequestId());
                    } else if (current instanceof FaceEnrollClient) {
                        mScheduler.cancelEnrollment(current.getToken(), current.getRequestId());
                    }
                }
                bindService(newUserId);
            });
        }
    };

    private static final class SenseScheduler extends BiometricScheduler {
        SenseScheduler() { super(0, null); }
        @Override
        protected void checkCurrentUserAndStartNextOperation() {
            startNextOperationIfIdle();
        }
    }

    private static final class SenseLockoutTracker extends LockoutHalImpl {
        private final SparseArray<Integer> modes = new SparseArray<>();
        @Override public synchronized int getLockoutModeForUser(int userId) {
            return modes.get(userId, LockoutTracker.LOCKOUT_NONE);
        }
        @Override public synchronized void setLockoutModeForUser(int userId, int mode) {
            modes.put(userId, mode);
        }
    }

    public static class HalResultController extends ISenseServiceReceiver.Stub {
        /**
         * Interface to sends results to the HalResultController's owner.
         */
        public interface Callback {
            /**
             * Invoked when the HAL sends ERROR_HW_UNAVAILABLE.
             */
            void onHardwareUnavailable();
        }

        private final int mSensorId;
        @NonNull private final Context mContext;
        @NonNull private final Handler mHandler;
        @NonNull private final BiometricScheduler mScheduler;
        @Nullable private Callback mCallback;
        @NonNull private final LockoutHalImpl mLockoutTracker;
        @NonNull private final LockoutResetDispatcher mLockoutResetDispatcher;


        HalResultController(int sensorId, @NonNull Context context, @NonNull Handler handler,
                @NonNull BiometricScheduler scheduler, @NonNull LockoutHalImpl lockoutTracker,
                @NonNull LockoutResetDispatcher lockoutResetDispatcher) {
            mSensorId = sensorId;
            mContext = context;
            mHandler = handler;
            mScheduler = scheduler;
            mLockoutTracker = lockoutTracker;
            mLockoutResetDispatcher = lockoutResetDispatcher;
        }

        public void setCallback(@Nullable Callback callback) {
            mCallback = callback;
        }

        private int mBoundUser = UserHandle.USER_NULL;
        private BooleanSupplier mConnectionValid = () -> true;
        private Supplier<BaseClientMonitor> mClientSnapshot;
        void setScope(int userId, BooleanSupplier valid, Supplier<BaseClientMonitor> snapshot) {
            mBoundUser = userId;
            mConnectionValid = valid;
            mClientSnapshot = snapshot;
        }
        private void dispatch(int reportedUser, Runnable result) { dispatch(reportedUser, false, result); }
        private void dispatch(int reportedUser, boolean terminal, Runnable result) {
            final BaseClientMonitor expected = mClientSnapshot == null ? null : mClientSnapshot.get();
            mHandler.post(() -> {
                if (!mConnectionValid.getAsBoolean()) return;
                final BaseClientMonitor client = mScheduler.getCurrentClient();
                if (mClientSnapshot != null && client != expected) return;
                if (mBoundUser != UserHandle.USER_NULL &&
                        (client == null || client.getTargetUserId() != mBoundUser ||
                         reportedUser != mBoundUser)) return;
                if (!terminal && (client instanceof FaceAuthenticationClient || client instanceof FaceEnrollClient)
                        && client.getTargetUserId() != ActivityManager.getCurrentUser()) return;
                result.run();
            });
        }

        @Override
        public void onEnrollResult(int faceId, int userId, int remaining) {
            dispatch(userId, () -> {
                final CharSequence name = FaceUtils.getLegacyInstance(mSensorId)
                        .getUniqueName(mContext, userId);
                final Face face = new Face(name, faceId, Long.valueOf(DEVICE_ID));

                final BaseClientMonitor client = mScheduler.getCurrentClient();
                if (!(client instanceof FaceEnrollClient)) {
                    Slog.e(TAG, "onEnrollResult for non-enroll client: "
                            + Utils.getClientName(client));
                    return;
                }

                final FaceEnrollClient enrollClient = (FaceEnrollClient) client;
                enrollClient.onEnrollResult(face, remaining);
            });
        }

        @Override
        public void onAuthenticated(int faceId, int userId, byte[] token) {
            dispatch(userId, () -> {
                final BaseClientMonitor client = mScheduler.getCurrentClient();
                if (!(client instanceof AuthenticationConsumer)) {
                    Slog.e(TAG, "onAuthenticated for non-authentication consumer: "
                            + Utils.getClientName(client));
                    return;
                }

                final AuthenticationConsumer authenticationConsumer =
                        (AuthenticationConsumer) client;
                final boolean authenticated = faceId != 0;
                final Face face = new Face("", faceId, DEVICE_ID);
                authenticationConsumer.onAuthenticated(face, authenticated, SenseUtils.toByteArrayList(token));
            });
        }

        @Override
        public void onAcquired(int userId, int acquiredInfo, int vendorCode) {
            dispatch(userId, () -> {
                final BaseClientMonitor client = mScheduler.getCurrentClient();
                if (!(client instanceof AcquisitionClient)) {
                    Slog.e(TAG, "onAcquired for non-acquire client: "
                            + Utils.getClientName(client));
                    return;
                }

                final AcquisitionClient<?> acquisitionClient =
                        (AcquisitionClient<?>) client;
                acquisitionClient.onAcquired(acquiredInfo, vendorCode);
            });
        }

        @Override
        public void onError(int error, int vendorCode) {
            dispatch(mBoundUser, true, () -> {
                final BaseClientMonitor client = mScheduler.getCurrentClient();
                Slog.d(TAG, "handleError"
                        + ", client: " + (client != null ? client.getOwnerString() : null)
                        + ", error: " + error
                        + ", vendorCode: " + vendorCode);
                if (!(client instanceof ErrorConsumer)) {
                    Slog.e(TAG, "onError for non-error consumer: " + Utils.getClientName(
                            client));
                    return;
                }

                final ErrorConsumer errorConsumer = (ErrorConsumer) client;
                errorConsumer.onError(error, vendorCode);

                if (error == BiometricConstants.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
                    Slog.e(TAG, "Got ERROR_HW_UNAVAILABLE");
                    if (mCallback != null) {
                        mCallback.onHardwareUnavailable();
                    }
                }
            });
        }

        @Override
        public void onRemoved(int[] faceIds, int userId) {
            dispatch(userId, () -> {
                final BaseClientMonitor client = mScheduler.getCurrentClient();
                if (!(client instanceof RemovalConsumer)) {
                    Slog.e(TAG, "onRemoved for non-removal consumer: "
                            + Utils.getClientName(client));
                    return;
                }

                final RemovalConsumer removalConsumer = (RemovalConsumer) client;

                if (faceIds.length > 0) {
                    // Convert to old fingerprint-like behavior, where remove() receives
                    // one removal at a time. This way, remove can share some more common code.
                    for (int i = 0; i < faceIds.length; i++) {
                        final int id = faceIds[i];
                        final Face face = new Face("", id, Long.valueOf(DEVICE_ID));
                        final int remaining = (faceIds.length - i) - 1;
                        Slog.d(TAG, "Removed, faceId: " + id + ", remaining: " + remaining);
                        removalConsumer.onRemoved(face, remaining);
                    }
                } else {
                    removalConsumer.onRemoved(null, 0 /* remaining */);
                }

                Settings.Secure.putIntForUser(mContext.getContentResolver(),
                        Settings.Secure.FACE_UNLOCK_RE_ENROLL, 0, userId);
            });
        }

        @Override
        public void onEnumerate(int[] faceIds, int userId) {
            dispatch(userId, () -> {
                final BaseClientMonitor client = mScheduler.getCurrentClient();
                if (!(client instanceof EnumerateConsumer)) {
                    Slog.e(TAG, "onEnumerate for non-enumerate consumer: "
                            + Utils.getClientName(client));
                    return;
                }

                final EnumerateConsumer enumerateConsumer = (EnumerateConsumer) client;

                if (faceIds.length > 0) {
                    // Convert to old fingerprint-like behavior, where enumerate() receives one
                    // template at a time. This way, enumerate can share some more common code.
                    for (int i = 0; i < faceIds.length; i++) {
                        final Face face = new Face("", faceIds[i], Long.valueOf(DEVICE_ID));
                        enumerateConsumer.onEnumerationResult(face, (faceIds.length - i) - 1);
                    }
                } else {
                    // For face, the HIDL contract is to receive an empty list when there are no
                    // templates enrolled. Send a null identifier since we don't consume them
                    // anywhere, and send remaining == 0 so this code can be shared with Face@1.1
                    enumerateConsumer.onEnumerationResult(null /* identifier */, 0);
                }
            });
        }

        @Override
        public void onLockoutChanged(long duration) {
            mHandler.post(() -> {
                if (!mConnectionValid.getAsBoolean()) return;
                Slog.d(TAG, "onLockoutChanged: " + duration);
                final @LockoutTracker.LockoutMode int lockoutMode;
                if (duration == 0) {
                    lockoutMode = LockoutTracker.LOCKOUT_NONE;
                } else if (duration == -1 || duration == Long.MAX_VALUE) {
                    lockoutMode = LockoutTracker.LOCKOUT_PERMANENT;
                } else {
                    lockoutMode = LockoutTracker.LOCKOUT_TIMED;
                }

                mLockoutTracker.setLockoutModeForUser(mBoundUser, lockoutMode);

                if (duration == 0) {
                    mLockoutResetDispatcher.notifyLockoutResetCallbacks(mSensorId);
                }
            });
        }
    }

    @VisibleForTesting
    public SenseProvider(@NonNull Context context,
            @NonNull BiometricStateCallback biometricStateCallback,
            @NonNull FaceSensorPropertiesInternal sensorProps,
            @NonNull LockoutResetDispatcher lockoutResetDispatcher,
            @NonNull BiometricScheduler scheduler) {
        mServices = new SparseArray<>();
        mSensorProperties = sensorProps;
        mContext = context;
        mBiometricStateCallback = biometricStateCallback;
        mSensorId = sensorProps.sensorId;
        mLockoutResetDispatcher = lockoutResetDispatcher;
        mScheduler = scheduler;
        mHandler = new Handler(Looper.getMainLooper());
        mBiometricContext = BiometricContext.getInstance(context);
        mUsageStats = new UsageStats(context);
        mAuthenticatorIds = new HashMap<>();
        mLockoutTracker = new SenseLockoutTracker();
        mHalResultController = new HalResultController(sensorProps.sensorId, context, mHandler,
                mScheduler, mLockoutTracker, lockoutResetDispatcher);
        mHalResultController.setCallback(() -> {
            mDaemon = null;
            mCurrentUserId = UserHandle.USER_NULL;
        });
        mCurrentUserId = ActivityManager.getCurrentUser();
        mHandler.post(() -> bindService(ActivityManager.getCurrentUser()));

        AuthenticationStatsBroadcastReceiver mBroadcastReceiver =
                new AuthenticationStatsBroadcastReceiver(
                        mContext,
                        BiometricsProtoEnums.MODALITY_FACE,
                        (AuthenticationStatsCollector collector) -> {
                            Slog.d(TAG, "Initializing AuthenticationStatsCollector");
                            mAuthenticationStatsCollector = collector;
                        });

        try {
            ActivityManager.getService().registerUserSwitchObserver(mUserSwitchObserver, TAG);
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to register user switch observer");
        }
    }

    public SenseProvider(Context context, BiometricStateCallback biometricStateCallback, FaceSensorPropertiesInternal sensorProps, LockoutResetDispatcher lockoutResetDispatcher) {
        this(context, biometricStateCallback, sensorProps, lockoutResetDispatcher, new SenseScheduler());
    }

    private ISenseService getDaemon() {
        return getService(ActivityManager.getCurrentUser());
    }

    private Supplier<ISenseService> daemonForUser(final int userId) {
        return () -> {
            final BaseClientMonitor current = mScheduler.getCurrentClient();
            if (current != null && current.getTargetUserId() != userId) return null;
            mCallbackClient = current;
            if (mTestHalEnabled) {
                final TestHal testHal = new TestHal(mContext, mSensorId);
                testHal.setCallback(mHalResultController);
                return testHal;
            }
            return getService(userId);
        };
    }

    @Override
    public boolean containsSensor(int sensorId) {
        return mSensorId == sensorId;
    }

    @Override
    @NonNull
    public List<FaceSensorPropertiesInternal> getSensorProperties() {
        final List<FaceSensorPropertiesInternal> properties = new ArrayList<>();
        properties.add(mSensorProperties);
        return properties;
    }

    @NonNull
    @Override
    public FaceSensorPropertiesInternal getSensorProperties(int sensorId) {
        return mSensorProperties;
    }

    @Override
    @NonNull
    public List<Face> getEnrolledFaces(int sensorId, int userId) {
        return FaceUtils.getLegacyInstance(mSensorId).getBiometricsForUser(mContext, userId);
    }

    @Override
    public boolean hasEnrollments(int sensorId, int userId) {
        return !getEnrolledFaces(sensorId, userId).isEmpty();
    }

    @Override
    @LockoutTracker.LockoutMode
    public int getLockoutModeForUser(int sensorId, int userId) {
        return mLockoutTracker.getLockoutModeForUser(userId);
    }

    @Override
    public long getAuthenticatorId(int sensorId, int userId) {
        return mAuthenticatorIds.getOrDefault(userId, 0L);
    }

    @Override
    public boolean isHardwareDetected(int sensorId) {
        final int userId = ActivityManager.getCurrentUser();
        if (!containsSensor(sensorId)) return false;
        if (getService(userId) != null) return true;
        mHandler.post(() -> bindService(userId));
        // Binding is asynchronous. Sensor availability must survive a reconnect.
        final Intent intent = new Intent(BIND_SENSE_ACTION)
                .setClassName(PACKAGE_NAME, SERVICE_NAME);
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
                && pm.resolveServiceAsUser(intent,
                        PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE, userId) != null;
    }

    private boolean isGeneratedChallengeCacheValid(int userId) {
        return challenge(userId).cache != null
                && sSystemClock.millis() - challenge(userId).cache.getCreatedAt()
                < GENERATE_CHALLENGE_REUSE_INTERVAL_MILLIS;
    }

    private void incrementChallengeCount(int userId) {
        challenge(userId).count.add(0, sSystemClock.millis());
    }

    private int decrementChallengeCount(int userId) {
        final long now = sSystemClock.millis();
        // ignore values that are old in case generate/revoke calls are not matched
        // this doesn't ensure revoke if calls are mismatched but it keeps the list from growing
        challenge(userId).count.removeIf(x -> now - x > GENERATE_CHALLENGE_COUNTER_TTL_MILLIS);
        if (!challenge(userId).count.isEmpty()) {
            challenge(userId).count.remove(0);
        }
        return challenge(userId).count.size();
    }

    /**
     * {@link IBiometricsFace} only supports a single in-flight challenge but there are cases where
     * two callers both need challenges (e.g. resetLockout right before enrollment).
     */
    @Override
    public void scheduleGenerateChallenge(int sensorId, int userId, @NonNull IBinder token,
            @NonNull IFaceServiceReceiver receiver, @NonNull String opPackageName) {
        mHandler.post(() -> {
            if (getService(userId) == null) {
                bindService(userId);
                try {
                    receiver.onError(BiometricFaceConstants.FACE_ERROR_HW_UNAVAILABLE, 0);
                    return;
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return;
                }
            }
            incrementChallengeCount(userId);

            if (isGeneratedChallengeCacheValid(userId)) {
                Slog.d(TAG, "Current challenge is cached and will be reused");
                challenge(userId).cache.reuseResult(receiver);
                return;
            }

            scheduleUpdateActiveUserWithoutHandler(userId);

            final FaceGenerateChallengeClient client = new FaceGenerateChallengeClient(mContext,
                    daemonForUser(userId), token, new ClientMonitorCallbackConverter(receiver), userId,
                    opPackageName, mSensorId,
                    createLogger(BiometricsProtoEnums.ACTION_UNKNOWN,
                            BiometricsProtoEnums.CLIENT_UNKNOWN),
                    mBiometricContext, sSystemClock.millis());
            challenge(userId).cache = client;
            mScheduler.scheduleClientMonitor(client, new ClientMonitorCallback() {
                @Override public void onClientFinished(BaseClientMonitor finished, boolean success) {
                    if (!success && challenge(userId).cache == client) {
                        challenge(userId).cache = null;
                        challenge(userId).count.clear();
                    }
                }
                @Override
                public void onClientStarted(@NonNull BaseClientMonitor clientMonitor) {
                    if (client != clientMonitor) {
                        Slog.e(TAG, "scheduleGenerateChallenge onClientStarted, mismatched client."
                                + " Expecting: " + client + ", received: " + clientMonitor);
                    }
                }
            });
        });
    }

    @Override
    public void scheduleRevokeChallenge(int sensorId, int userId, @NonNull IBinder token,
            @NonNull String opPackageName, long challenge) {
        mHandler.post(() -> {
            if (getService(userId) == null) {
                bindService(userId);
                challenge(userId).count.clear();
                challenge(userId).cache = null;
                Slog.w(TAG, "Revoke challenge: service not ready for user " + userId);
            }
            final boolean shouldRevoke = decrementChallengeCount(userId) == 0;
            if (!shouldRevoke) {
                Slog.w(TAG, "scheduleRevokeChallenge skipped - challenge still in use: "
                        + challenge(userId).count);
                return;
            }

            Slog.d(TAG, "scheduleRevokeChallenge executing - no active clients");
            challenge(userId).cache = null;

            final FaceRevokeChallengeClient client = new FaceRevokeChallengeClient(mContext,
                    daemonForUser(userId), token, userId, opPackageName, mSensorId,
                    createLogger(BiometricsProtoEnums.ACTION_UNKNOWN,
                            BiometricsProtoEnums.CLIENT_UNKNOWN),
                    mBiometricContext);
            mScheduler.scheduleClientMonitor(client, new ClientMonitorCallback() {
                @Override
                public void onClientFinished(@NonNull BaseClientMonitor clientMonitor,
                        boolean success) {
                    if (client != clientMonitor) {
                        Slog.e(TAG, "scheduleRevokeChallenge, mismatched client."
                                + "Expecting: " + client + ", received: " + clientMonitor);
                    }
                }
            });
        });
    }

    @Override
    public long scheduleEnroll(int sensorId, @NonNull IBinder token,
            @NonNull byte[] hardwareAuthToken, int userId, @NonNull IFaceServiceReceiver receiver,
            @NonNull String opPackageName, @NonNull int[] disabledFeatures,
            @Nullable Surface previewSurface, boolean debugConsent,
            @NonNull FaceEnrollOptions options) {
        final long id = mRequestCounter.incrementAndGet();
        mHandler.post(() -> {
            if (getService(userId) == null) {
                bindService(userId);
                try {
                    receiver.onError(2, 0);
                    return;
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return;
                }
            }
            scheduleUpdateActiveUserWithoutHandler(userId);

            BiometricNotificationUtils.cancelFaceReEnrollNotification(mContext);

            final FaceEnrollClient client = new FaceEnrollClient(mContext, daemonForUser(userId), token,
                    new ClientMonitorCallbackConverter(receiver), userId, hardwareAuthToken,
                    opPackageName, id, FaceUtils.getLegacyInstance(mSensorId), disabledFeatures,
                    ENROLL_TIMEOUT_SEC, previewSurface, mSensorId,
                    createLogger(BiometricsProtoEnums.ACTION_ENROLL,
                            BiometricsProtoEnums.CLIENT_UNKNOWN),
                    mBiometricContext,
                    mContext.getResources().getInteger(
                                com.android.internal.R.integer.config_faceMaxTemplatesPerUser));

            mScheduler.scheduleClientMonitor(client, new ClientMonitorCallback() {
                @Override
                public void onClientStarted(@NonNull BaseClientMonitor clientMonitor) {
                    mBiometricStateCallback.onClientStarted(clientMonitor);
                }

                @Override
                public void onBiometricAction(int action) {
                    mBiometricStateCallback.onBiometricAction(action);
                }

                @Override
                public void onClientFinished(@NonNull BaseClientMonitor clientMonitor,
                        boolean success) {
                    if (!success) invalidateFailedClient(clientMonitor);
                    mBiometricStateCallback.onClientFinished(clientMonitor, success);
                    if (success) {
                        // Update authenticatorIds
                        scheduleUpdateActiveUserWithoutHandler(client.getTargetUserId());
                    }
                }
            });
        });
        return id;
    }

    @Override
    public void cancelEnrollment(int sensorId, @NonNull IBinder token, long requestId) {
        mHandler.post(() -> mScheduler.cancelEnrollment(token, requestId));
    }

    @Override
    public long scheduleFaceDetect(@NonNull IBinder token,
            @NonNull ClientMonitorCallbackConverter callback,
            @NonNull FaceAuthenticateOptions options, int statsClient) {
        throw new IllegalStateException("Face detect not supported by IBiometricsFace@1.0. Did you"
                + "forget to check the supportsFaceDetection flag?");
    }

    @Override
    public void cancelFaceDetect(int sensorId, @NonNull IBinder token, long requestId) {
        throw new IllegalStateException("Face detect not supported by IBiometricsFace@1.0. Did you"
                + "forget to check the supportsFaceDetection flag?");
    }

    @Override
    public void scheduleAuthenticate(@NonNull IBinder token, long operationId,
            int cookie, @NonNull ClientMonitorCallbackConverter receiver,
            @NonNull FaceAuthenticateOptions options, long requestId, boolean restricted,
            int statsClient, boolean allowBackgroundAuthentication) {
        mHandler.post(() -> {
            final int userId = options.getUserId();
            if (getService(userId) == null) {
                bindService(userId);
                try {
                    receiver.onError(1008, 0, 1, 0);
                    return;
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return;
                }
            }
            scheduleUpdateActiveUserWithoutHandler(userId);

            final boolean isStrongBiometric = Utils.isStrongBiometric(mSensorId);
            final FaceAuthenticationClient client = new FaceAuthenticationClient(mContext,
                    daemonForUser(userId), token, requestId, receiver, operationId, restricted,
                    options, cookie, false /* requireConfirmation */,
                    createLogger(BiometricsProtoEnums.ACTION_AUTHENTICATE, statsClient),
                    mBiometricContext, isStrongBiometric, mLockoutTracker,
                    mUsageStats, allowBackgroundAuthentication,
                    Utils.getCurrentStrength(mSensorId));
            mScheduler.scheduleClientMonitor(client, new ClientMonitorCallback() {
                @Override public void onClientFinished(BaseClientMonitor finished, boolean success) {
                    if (!success) invalidateFailedClient(finished);
                }
            });
        });
    }

    @Override
    public long scheduleAuthenticate(@NonNull IBinder token, long operationId,
            int cookie, @NonNull ClientMonitorCallbackConverter receiver,
            @NonNull FaceAuthenticateOptions options, boolean restricted,
            int statsClient, boolean allowBackgroundAuthentication) {
        final long id = mRequestCounter.incrementAndGet();

        scheduleAuthenticate(token, operationId, cookie, receiver,
                options, id, restricted, statsClient, allowBackgroundAuthentication);

        return id;
    }

    @Override
    public void cancelAuthentication(int sensorId, @NonNull IBinder token, long requestId) {
        mHandler.post(() -> mScheduler.cancelAuthenticationOrDetection(token, requestId));
    }

    @Override
    public void scheduleRemove(int sensorId, @NonNull IBinder token, int faceId, int userId,
            @NonNull IFaceServiceReceiver receiver, @NonNull String opPackageName) {
        mHandler.post(() -> {
            if (getService(userId) == null) {
                bindService(userId);
                try {
                    receiver.onError(1, 0);
                    return;
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return;
                }
            }
            scheduleUpdateActiveUserWithoutHandler(userId);

            final FaceRemovalClient client = new FaceRemovalClient(mContext, daemonForUser(userId), token,
                    new ClientMonitorCallbackConverter(receiver), faceId, userId, opPackageName,
                    FaceUtils.getLegacyInstance(mSensorId), mSensorId,
                    createLogger(BiometricsProtoEnums.ACTION_REMOVE,
                            BiometricsProtoEnums.CLIENT_UNKNOWN),
                    mBiometricContext, mAuthenticatorIds,
                    BiometricsProtoEnums.UNENROLL_REASON_USER_REQUEST);
            mScheduler.scheduleClientMonitor(client, mBiometricStateCallback);
        });
    }

    @Override
    public void scheduleRemoveAll(int sensorId, @NonNull IBinder token, int userId,
            @NonNull IFaceServiceReceiver receiver, @NonNull String opPackageName) {
        mHandler.post(() -> {
            if (getService(userId) == null) {
                bindService(userId);
                try {
                    receiver.onError(1, 0);
                    return;
                } catch (RemoteException e) {
                    e.printStackTrace();
                    return;
                }
            }
            scheduleUpdateActiveUserWithoutHandler(userId);

            // For IBiometricsFace@1.0, remove(0) means remove all enrollments
            final FaceRemovalClient client = new FaceRemovalClient(mContext, daemonForUser(userId), token,
                    new ClientMonitorCallbackConverter(receiver), 0 /* faceId */, userId,
                    opPackageName,
                    FaceUtils.getLegacyInstance(mSensorId), mSensorId,
                    createLogger(BiometricsProtoEnums.ACTION_REMOVE,
                            BiometricsProtoEnums.CLIENT_UNKNOWN),
                    mBiometricContext, mAuthenticatorIds,
                    BiometricsProtoEnums.UNENROLL_REASON_USER_REQUEST);
            mScheduler.scheduleClientMonitor(client, mBiometricStateCallback);
        });
    }

    @Override
    public void scheduleResetLockout(int sensorId, int userId, @NonNull byte[] hardwareAuthToken) {
        mHandler.post(() -> {
            if (getService(userId) == null) {
                bindService(userId);
            }
            if (getEnrolledFaces(sensorId, userId).isEmpty()) {
                Slog.w(TAG, "Ignoring lockout reset, no templates enrolled for user: " + userId);
                return;
            }

            scheduleUpdateActiveUserWithoutHandler(userId);

            final FaceResetLockoutClient client = new FaceResetLockoutClient(mContext,
                    daemonForUser(userId), userId, mContext.getOpPackageName(), mSensorId,
                    createLogger(BiometricsProtoEnums.ACTION_UNKNOWN,
                            BiometricsProtoEnums.CLIENT_UNKNOWN),
                    mBiometricContext, hardwareAuthToken);
            mScheduler.scheduleClientMonitor(client, mBiometricStateCallback);
        });
    }

    @Override
    public void scheduleSetFeature(int sensorId, @NonNull IBinder token, int userId, int feature,
            boolean enabled, @NonNull byte[] hardwareAuthToken,
            @NonNull IFaceServiceReceiver receiver, @NonNull String opPackageName) {
        mHandler.post(() -> {
            if (getService(userId) == null) {
                bindService(userId);
                try { receiver.onFeatureSet(false, feature); }
                catch (RemoteException e) { Slog.w(TAG, "Feature failure callback", e); }
                return;
            }
            final List<Face> faces = getEnrolledFaces(sensorId, userId);
            if (faces.isEmpty()) {
                Slog.w(TAG, "Ignoring setFeature, no templates enrolled for user: " + userId);
                return;
            }

            scheduleUpdateActiveUserWithoutHandler(userId);

            final int faceId = faces.get(0).getBiometricId();
            final FaceSetFeatureClient client = new FaceSetFeatureClient(mContext,
                    daemonForUser(userId), token, new ClientMonitorCallbackConverter(receiver), userId,
                    opPackageName, mSensorId, BiometricLogger.ofUnknown(mContext, mHandler),
                    mBiometricContext,
                    feature, enabled, hardwareAuthToken, faceId);
            mScheduler.scheduleClientMonitor(client, mBiometricStateCallback);
        });
    }

    @Override
    public void scheduleGetFeature(int sensorId, @NonNull IBinder token, int userId, int feature,
            @Nullable ClientMonitorCallbackConverter listener, @NonNull String opPackageName) {
        mHandler.post(() -> {
            if (getService(userId) == null) {
                bindService(userId);
                if (listener != null) {
                    try {
                        listener.onError(1008, 0, 1, 0);
                        return;
                    } catch (RemoteException e) {
                        e.printStackTrace();
                        return;
                    }
                }
                return;
            }
            final List<Face> faces = getEnrolledFaces(sensorId, userId);
            if (faces.isEmpty()) {
                Slog.w(TAG, "Ignoring getFeature, no templates enrolled for user: " + userId);
                return;
            }

            scheduleUpdateActiveUserWithoutHandler(userId);

            final int faceId = faces.get(0).getBiometricId();
            final FaceGetFeatureClient client = new FaceGetFeatureClient(mContext, daemonForUser(userId),
                    token, listener, userId, opPackageName, mSensorId,
                    BiometricLogger.ofUnknown(mContext, mHandler), mBiometricContext,
                    feature, faceId);
            mScheduler.scheduleClientMonitor(client, new ClientMonitorCallback() {
                @Override
                public void onClientFinished(
                        @NonNull BaseClientMonitor clientMonitor, boolean success) {
                    if (success && feature == BiometricFaceConstants.FEATURE_REQUIRE_ATTENTION) {
                        final int settingsValue = client.getValue() ? 1 : 0;
                        Slog.d(TAG, "Updating attention value for user: " + userId
                                + " to value: " + settingsValue);
                        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                                Settings.Secure.FACE_UNLOCK_ATTENTION_REQUIRED,
                                settingsValue, userId);
                    }
                }
            });
        });
    }

    private void scheduleInternalCleanup(int userId,
            @Nullable ClientMonitorCallback callback) {
        mHandler.post(() -> {
            scheduleUpdateActiveUserWithoutHandler(userId);

            final FaceInternalCleanupClient client = new FaceInternalCleanupClient(mContext,
                    daemonForUser(userId), userId, mContext.getOpPackageName(), mSensorId,
                    createLogger(BiometricsProtoEnums.ACTION_ENUMERATE,
                            BiometricsProtoEnums.CLIENT_UNKNOWN),
                    mBiometricContext,
                    FaceUtils.getLegacyInstance(mSensorId), mAuthenticatorIds);
            mScheduler.scheduleClientMonitor(client, new ClientMonitorCompositeCallback(callback,
                    mBiometricStateCallback));
        });
    }

    @Override
    public void scheduleInternalCleanup(int sensorId, int userId,
            @Nullable ClientMonitorCallback callback) {
        scheduleInternalCleanup(userId, mBiometricStateCallback);
    }

    @Override
    public void scheduleInternalCleanup(int sensorId, int userId,
            @Nullable ClientMonitorCallback callback, boolean favorHalEnrollments) {
        scheduleInternalCleanup(userId, mBiometricStateCallback);
    }

    @Override
    public void startPreparedClient(int sensorId, int cookie) {
        mHandler.post(() -> {
            mScheduler.startPreparedClient(cookie);
        });
    }

    @Override
    public void dumpProtoState(int sensorId, ProtoOutputStream proto,
            boolean clearSchedulerBuffer) {
        final long sensorToken = proto.start(SensorServiceStateProto.SENSOR_STATES);

        proto.write(SensorStateProto.SENSOR_ID, mSensorProperties.sensorId);
        proto.write(SensorStateProto.MODALITY, SensorStateProto.FACE);
        proto.write(SensorStateProto.CURRENT_STRENGTH,
                Utils.getCurrentStrength(mSensorProperties.sensorId));
        proto.write(SensorStateProto.SCHEDULER, mScheduler.dumpProtoState(clearSchedulerBuffer));

        for (UserInfo user : UserManager.get(mContext).getUsers()) {
            final int userId = user.getUserHandle().getIdentifier();

            final long userToken = proto.start(SensorStateProto.USER_STATES);
            proto.write(UserStateProto.USER_ID, userId);
            proto.write(UserStateProto.NUM_ENROLLED, FaceUtils.getLegacyInstance(mSensorId)
                    .getBiometricsForUser(mContext, userId).size());
            proto.end(userToken);
        }

        proto.write(SensorStateProto.RESET_LOCKOUT_REQUIRES_HARDWARE_AUTH_TOKEN,
                mSensorProperties.resetLockoutRequiresHardwareAuthToken);
        proto.write(SensorStateProto.RESET_LOCKOUT_REQUIRES_CHALLENGE,
                mSensorProperties.resetLockoutRequiresChallenge);

        proto.end(sensorToken);
    }

    @Override
    public void dumpProtoMetrics(int sensorId, FileDescriptor fd) {
    }

    @Override
    public void dumpInternal(int sensorId, PrintWriter pw) {
        PerformanceTracker performanceTracker =
                PerformanceTracker.getInstanceForSensorId(mSensorId);

        JSONObject dump = new JSONObject();
        try {
            dump.put("service", TAG);

            JSONArray sets = new JSONArray();
            for (UserInfo user : UserManager.get(mContext).getUsers()) {
                final int userId = user.getUserHandle().getIdentifier();
                final int c = FaceUtils.getLegacyInstance(mSensorId)
                        .getBiometricsForUser(mContext, userId).size();
                JSONObject set = new JSONObject();
                set.put("id", userId);
                set.put("count", c);
                set.put("accept", performanceTracker.getAcceptForUser(userId));
                set.put("reject", performanceTracker.getRejectForUser(userId));
                set.put("acquire", performanceTracker.getAcquireForUser(userId));
                set.put("lockout", performanceTracker.getTimedLockoutForUser(userId));
                set.put("permanentLockout", performanceTracker.getPermanentLockoutForUser(userId));
                // cryptoStats measures statistics about secure face transactions
                // (e.g. to unlock password storage, make secure purchases, etc.)
                set.put("acceptCrypto", performanceTracker.getAcceptCryptoForUser(userId));
                set.put("rejectCrypto", performanceTracker.getRejectCryptoForUser(userId));
                set.put("acquireCrypto", performanceTracker.getAcquireCryptoForUser(userId));
                sets.put(set);
            }

            dump.put("prints", sets);
        } catch (JSONException e) {
            Slog.e(TAG, "dump formatting failure", e);
        }
        pw.println(dump);
        pw.println("HAL deaths since last reboot: " + performanceTracker.getHALDeathCount());

        mScheduler.dump(pw);
        mUsageStats.print(pw);
    }

    private void scheduleLoadAuthenticatorIds() {
        // Note that this can be performed on the scheduler (as opposed to being done immediately
        // when the HAL is (re)loaded, since
        // 1) If this is truly the first time it's being performed (e.g. system has just started),
        //    this will be run very early and way before any applications need to generate keys.
        // 2) If this is being performed to refresh the authenticatorIds (e.g. HAL crashed and has
        //    just been reloaded), the framework already has a cache of the authenticatorIds. This
        //    is safe because authenticatorIds only change when A) new template has been enrolled,
        //    or B) all templates are removed.
        mHandler.post(() -> {
            for (UserInfo user : UserManager.get(mContext).getAliveUsers()) {
                final int targetUserId = user.id;
                if (!mAuthenticatorIds.containsKey(targetUserId)) {
                    scheduleUpdateActiveUserWithoutHandler(targetUserId);
                }
            }
        });
    }

    /**
     * Schedules the {@link FaceUpdateActiveUserClient} without posting the work onto the handler.
     * Many/most APIs are user-specific. However, the HAL requires explicit "setActiveUser"
     * invocation prior to authenticate/enroll/etc. Thus, internally we usually want to schedule
     * this operation on the same lambda/runnable as those operations so that the ordering is
     * correct.
     */
    private void scheduleUpdateActiveUserWithoutHandler(int targetUserId) {
        if (getService(targetUserId) == null) bindService(targetUserId);
        final boolean hasEnrolled = !getEnrolledFaces(mSensorId, targetUserId).isEmpty();
        final FaceUpdateActiveUserClient client = new FaceUpdateActiveUserClient(mContext,
                daemonForUser(targetUserId), targetUserId, mContext.getOpPackageName(), mSensorId,
                createLogger(BiometricsProtoEnums.ACTION_UNKNOWN,
                        BiometricsProtoEnums.CLIENT_UNKNOWN),
                mBiometricContext, hasEnrolled, mAuthenticatorIds);
        mScheduler.scheduleClientMonitor(client);
    }

    private void invalidateFailedClient(BaseClientMonitor client) {
        // A delivered error acknowledges that Sense finished the operation. Only
        // an unresponsive client needs its binder quarantined for late callbacks.
        if (client instanceof FaceAuthenticationClient
                && ((FaceAuthenticationClient) client).hasReceivedError()) return;
        if (client instanceof FaceEnrollClient
                && ((FaceEnrollClient) client).hasReceivedError()) return;
        final SenseServiceConnection connection = mConnections.get(client.getTargetUserId());
        if (connection != null && connection.service != null) {
            mRejectedBinders.put(client.getTargetUserId(), connection.service.asBinder());
            connection.disconnect(false);
        }
    }

    public class SenseServiceConnection implements ServiceConnection {
        final int mUserId;
        final long mGeneration;
        ISenseService service;
        boolean bound;
        SenseServiceConnection(int userId) {
            mUserId = userId;
            mGeneration = ++mConnectionGeneration;
        }
        boolean current() { return mConnections.get(mUserId) == this; }
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            mHandler.post(() -> {
                if (!current()) return;
                if (binder.equals(mRejectedBinders.get(mUserId))) {
                    Slog.e(TAG, "Rejecting unquiesced Sense service for user " + mUserId);
                    disconnect(false);
                    return;
                }
                final ISenseService connected = ISenseService.Stub.asInterface(binder);
                if (connected == null) { disconnect(); return; }
                final HalResultController receiver = new HalResultController(mSensorId, mContext,
                        mHandler, mScheduler, mLockoutTracker, mLockoutResetDispatcher);
                receiver.setScope(mUserId, () -> current() && service == connected, () -> mCallbackClient);
                receiver.setCallback(() -> disconnect(false));
                try {
                    connected.setCallback(receiver);
                    service = connected;
                    synchronized (mServices) { mServices.put(mUserId, connected); }
                    scheduleInternalCleanup(mUserId, null);
                    scheduleGetFeature(mSensorId, new Binder(), mUserId, 1, null,
                            mContext.getOpPackageName());
                } catch (RemoteException e) { disconnect(); }
            });
        }
        void disconnect() { disconnect(true); }
        void disconnect(boolean notifyClient) {
            if (!current()) return;
            mConnections.remove(mUserId);
            synchronized (mServices) { mServices.remove(mUserId); }
            mChallenges.remove(mUserId);
            service = null;
            if (bound) {
                bound = false;
                try { mContext.unbindService(this); }
                catch (IllegalArgumentException e) { Slog.w(TAG, "Sense connection already unbound", e); }
            }
            final BaseClientMonitor client = mScheduler.getCurrentClient();
            if (notifyClient && client != null && client.getTargetUserId() == mUserId && client instanceof ErrorConsumer) {
                ((ErrorConsumer) client).onError(BiometricConstants.BIOMETRIC_ERROR_HW_UNAVAILABLE, 0);
            }
            // Do not reset other users' queued clients. Null daemon preflight fails closed.
        }
        @Override public void onServiceDisconnected(ComponentName name) { mHandler.post(this::disconnect); }
        @Override public void onBindingDied(ComponentName name) { mHandler.post(this::disconnect); }
        @Override public void onNullBinding(ComponentName name) { mHandler.post(this::disconnect); }
    }

    private ISenseService getService(int userId) {
        synchronized (mServices) { return userId < 0 ? null : mServices.get(userId); }
    }

    public boolean bindService(int userId) {
        if (userId < 0) return false;
        if (mConnections.get(userId) != null) return true;
        final Intent intent = new Intent(BIND_SENSE_ACTION).setClassName(PACKAGE_NAME, SERVICE_NAME);
        final SenseServiceConnection connection = new SenseServiceConnection(userId);
        mConnections.put(userId, connection);
        try {
            connection.bound = mContext.bindServiceAsUser(intent, connection,
                    Context.BIND_AUTO_CREATE, UserHandle.of(userId));
            if (!connection.bound) mConnections.remove(userId);
            else mHandler.postDelayed(() -> {
                if (connection.current() && connection.service == null) connection.disconnect();
            }, 10000);
            return connection.bound;
        } catch (SecurityException | IllegalArgumentException e) {
            mConnections.remove(userId);
            Slog.e(TAG, "Unable to bind Sense for user " + userId, e);
            return false;
        }
    }

    private BiometricLogger createLogger(int statsAction, int statsClient) {
        return new BiometricLogger(mContext, mHandler, BiometricsProtoEnums.MODALITY_FACE,
                statsAction, statsClient, mAuthenticationStatsCollector);
    }

    /**
     * Sends a debug message to the HAL with the provided FileDescriptor and arguments.
     */
    public void dumpHal(int sensorId, @NonNull FileDescriptor fd, @NonNull String[] args) { }

    void setTestHalEnabled(boolean enabled) {
        mTestHalEnabled = enabled;
    }

    @NonNull
    @Override
    public ITestSession createTestSession(int sensorId, @NonNull ITestSessionCallback callback,
            @NonNull String opPackageName) {
        return new BiometricTestSessionImpl(mContext, mSensorId, callback, this,
                mHalResultController);
    }
}
