/* SPDX-License-Identifier: Apache-2.0 */
package com.android.server.display;

/** A bounded veto of darkening, never a second brightness or ambient-lux source. */
final class RearLightDarkeningGuard {
    static final long MAX_HOLD_MILLIS = 5000;
    static final long TORCH_SETTLE_MILLIS = 2000;
    private static final float MIN_FRONT_LUX = 10f;
    private static final float MIN_REAR_LUX = 5f;
    private float mRearLux = Float.NaN;
    private float mFrontReference = Float.NaN;
    private float mRearReference = Float.NaN;
    private float mLastFrontLux = Float.NaN;
    private boolean mHolding;
    private boolean mExhausted;
    private boolean mInterference;
    private long mHoldStarted;
    private long mIgnoreRearBefore;

    void reset() {
        mRearLux = mFrontReference = mRearReference = mLastFrontLux = Float.NaN;
        mHolding = mExhausted = mInterference = false;
        mHoldStarted = mIgnoreRearBefore = 0;
    }

    void setInterference(long now, boolean interference) {
        if (mInterference == interference) return;
        mInterference = interference;
        mRearLux = mFrontReference = mRearReference = Float.NaN;
        mHolding = mExhausted = false;
        mIgnoreRearBefore = now + TORCH_SETTLE_MILLIS;
    }

    void onRearSample(long now, float lux) {
        // ON_CHANGE samples remain valid until another event or unregistration.
        if (mInterference || now < mIgnoreRearBefore) return;
        mRearLux = valid(lux) ? lux : Float.NaN;
        if (!rearSupportsReference()) {
            mHolding = false;
            mFrontReference = mRearReference = Float.NaN;
        }
    }

    void onFrontSample(long now, float lux, float acceptedLux) {
        if (!valid(lux)) return;
        expire(now);
        // Capture each sensor relative to its OWN stable baseline; do not assume
        // that front and rear lux have matching calibration or field of view.
        if (!valid(mFrontReference) && stableFront(mLastFrontLux, acceptedLux)
                && rearAvailable(now)) {
            arm(acceptedLux);
        }
        if (stableFront(lux, acceptedLux) && rearAvailable(now)) {
            arm(acceptedLux);
        } else if (!mHolding && !mExhausted && !mInterference
                && valid(mFrontReference) && lux <= mFrontReference * 0.25f
                && rearSupportsReference()) {
            // Latch the abrupt RAW front drop before native smoothing can accept
            // several smaller downward steps and lose the pre-occlusion baseline.
            mHolding = true;
            mHoldStarted = now;
        }
        mLastFrontLux = lux;
    }

    boolean shouldDeferDarkening(long now) {
        expire(now);
        return mHolding && !mInterference && rearSupportsReference();
    }

    boolean isHolding() { return mHolding; }

    private void expire(long now) {
        // Checked only along actual sensor/native-controller operations. No timer
        // or periodic task is created, and the same drop cannot restart the hold.
        if (mHolding && now - mHoldStarted >= MAX_HOLD_MILLIS) {
            mHolding = false;
            mExhausted = true;
        }
    }

    private void arm(float frontLux) {
        mFrontReference = frontLux;
        mRearReference = mRearLux;
        mHolding = mExhausted = false;
    }

    private boolean rearAvailable(long now) {
        return !mInterference && now >= mIgnoreRearBefore
                && valid(mRearLux) && mRearLux >= MIN_REAR_LUX;
    }

    private boolean rearSupportsReference() {
        return valid(mRearLux) && valid(mRearReference)
                && mRearLux >= Math.max(MIN_REAR_LUX, mRearReference * 0.6f);
    }

    private static boolean stableFront(float lux, float acceptedLux) {
        return valid(lux) && valid(acceptedLux) && acceptedLux >= MIN_FRONT_LUX
                && lux >= acceptedLux * 0.8f;
    }

    private static boolean valid(float lux) {
        return Float.isFinite(lux) && lux >= 0;
    }

    @Override public String toString() {
        return "RearLightDarkeningGuard{holding=" + mHolding + ", exhausted=" + mExhausted
                + ", rearLux=" + mRearLux + ", frontReference=" + mFrontReference
                + ", rearReference=" + mRearReference + ", interference=" + mInterference + "}";
    }
}
