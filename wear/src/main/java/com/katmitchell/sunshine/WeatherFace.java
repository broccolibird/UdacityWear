/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.katmitchell.sunshine;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import com.katmitchell.sunshine.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WeatherFace extends CanvasWatchFaceService {

    private static final String TAG = "WeatherFace";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {

        private final WeakReference<WeatherFace.Engine> mWeakReference;

        public EngineHandler(WeatherFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WeatherFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        boolean mRegisteredTimeZoneReceiver = false;

        GoogleApiClient mGoogleApiClient;

        Paint mBackgroundPaint;

        Paint mTimePaint;

        Paint mDatePaint;

        Paint mHighPaint;

        Paint mLowPaint;

        boolean mAmbient;

        Time mTime;

        Date mDate;

        SimpleDateFormat mDateFormat;

        int mWeatherId;

        String mTempHigh = "90", mTempLow = "60";

        Bitmap mWeatherBitmap;

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(TAG, "onDataChanged: ");

            for (DataEvent event : dataEventBuffer) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                mWeatherId = dataMap.getInt("weather_id");
                mTempHigh = Integer.toString((int) dataMap.getDouble("high"));
                mTempLow = Integer.toString((int) dataMap.getDouble("low"));

                setWeatherBitmap();

                invalidate();
            }
        }

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();

                mDate = new Date();
            }
        };

        float mXOffsetTime;

        float mYOffsetTime;

        float mXOffsetDate;

        float mYOffsetDate;

        float mXOffsetHigh, mXOffsetLow;

        float mYOffsetTemp;

        float mXOffsetImage;

        float mYOffsetImage;

        float mYOffsetDivider;

        float mDividerLeft, mDividerRight;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = WeatherFace.this.getResources();
            mYOffsetTime = resources.getDimension(R.dimen.y_offset_time);
            mYOffsetDate = resources.getDimension(R.dimen.y_offset_date);
            mYOffsetTemp = resources.getDimension(R.dimen.y_offset_temp);
            mYOffsetImage = resources.getDimension(R.dimen.y_offset_image);
            mYOffsetDivider = resources.getDimension(R.dimen.y_offset_divider);
            mDividerLeft = resources.getDimension(R.dimen.divider_left);
            mDividerRight = resources.getDimension(R.dimen.divider_right);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.solid_text));
            mTime = new Time();

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.alpha_text));
            mDate = new Date();

            mHighPaint = new Paint();
            mHighPaint = createTextPaint(resources.getColor(R.color.solid_text));

            mLowPaint = new Paint();
            mLowPaint = createTextPaint(resources.getColor(R.color.alpha_text));

            mDateFormat = new SimpleDateFormat("EEE, MMM d yyyy");

            mWeatherBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_clear);

            mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                    .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle connectionHint) {
                            Log.d(TAG, "onConnected: " + connectionHint);
                            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
                        }

                        @Override
                        public void onConnectionSuspended(int cause) {
                            Log.d(TAG, "onConnectionSuspended: " + cause);
                        }
                    })
                    .addOnConnectionFailedListener(
                            new GoogleApiClient.OnConnectionFailedListener() {
                                @Override
                                public void onConnectionFailed(ConnectionResult result) {
                                    Log.d(TAG, "onConnectionFailed: " + result);
                                }
                            })
                    // Request access only to the Wearable API
                    .addApi(Wearable.API)
                    .build();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);

            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();

            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WeatherFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WeatherFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WeatherFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffsetTime = resources.getDimension(isRound
                    ? R.dimen.x_offset_time_round : R.dimen.x_offset_time);
            mXOffsetDate = resources
                    .getDimension(isRound ? R.dimen.x_offset_date_round : R.dimen.x_offset_date);

            mXOffsetHigh = resources
                    .getDimension(isRound ? R.dimen.x_offset_high_round : R.dimen.x_offset_high);
            mXOffsetLow = resources
                    .getDimension(isRound ? R.dimen.x_offset_low_round : R.dimen.x_offset_low);

            mXOffsetImage = resources
                    .getDimension(isRound ? R.dimen.x_offset_image_round : R.dimen.x_offset_image);

            float timeTextSize = resources.getDimension(R.dimen.text_size_time);
            float dateTextSize = resources.getDimension(R.dimen.text_size_date);
            float tempTextSize = resources.getDimension(R.dimen.text_size_temp);

            mTimePaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mHighPaint.setTextSize(tempTextSize);
            mLowPaint.setTextSize(tempTextSize);

            mDividerLeft = isRound ? resources.getDimension(R.dimen.divider_left_round)
                    : resources.getDimension(R.dimen.divider_left);
            mDividerRight = isRound ? resources.getDimension(R.dimen.divider_right_round)
                    : resources.getDimension(R.dimen.divider_right);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mHighPaint.setAntiAlias(!inAmbientMode);
                    mLowPaint.setAntiAlias(!inAmbientMode);

                }
                if (mAmbient) {
                    mWeatherBitmap = toGrayscale(mWeatherBitmap);
                } else {
                    mWeatherBitmap = BitmapFactory
                            .decodeResource(getResources(), R.drawable.ic_clear);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }


        private void setWeatherBitmap() {
            int weatherRes = getIconResourceForWeatherCondition(mWeatherId);
            mWeatherBitmap = BitmapFactory.decodeResource(getResources(), weatherRes);

            if (mAmbient) {
                mWeatherBitmap = toGrayscale(mWeatherBitmap);
            }
        }

        public int getIconResourceForWeatherCondition(int weatherId) {
            // Based on weather code data found at:
            // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.ic_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.ic_rain;
            } else if (weatherId == 511) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.ic_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.ic_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                return R.drawable.ic_storm;
            } else if (weatherId == 800) {
                return R.drawable.ic_clear;
            } else if (weatherId == 801) {
                return R.drawable.ic_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.ic_cloudy;
            }
            return -1;
        }

        private Bitmap toGrayscale(Bitmap bmpOriginal) {
            int width, height;
            height = bmpOriginal.getHeight();
            width = bmpOriginal.getWidth();

            Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmpGrayscale);
            Paint paint = new Paint();
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(0);
            ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
            paint.setColorFilter(f);
            c.drawBitmap(bmpOriginal, 0, 0, paint);
            return bmpGrayscale;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String timeText = String.format("%d:%02d", mTime.hour, mTime.minute);
            canvas.drawText(timeText, mXOffsetTime, mYOffsetTime, mTimePaint);

            mDate = new Date();
            String dateText = mDateFormat.format(mDate);
            canvas.drawText(dateText, mXOffsetDate, mYOffsetDate, mDatePaint);

            canvas.drawText(mTempHigh + "\u00B0", mXOffsetHigh, mYOffsetTemp, mHighPaint);
            canvas.drawText(mTempLow + "\u00B0", mXOffsetLow, mYOffsetTemp, mLowPaint);

            canvas.drawBitmap(mWeatherBitmap, mXOffsetImage, mYOffsetImage, mHighPaint);

            canvas.drawLine(mDividerLeft, mYOffsetDivider, mDividerRight, mYOffsetDivider,
                    mLowPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
