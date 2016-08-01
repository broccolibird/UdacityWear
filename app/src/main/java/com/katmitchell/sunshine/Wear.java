package com.katmitchell.sunshine;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallbacks;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import com.katmitchell.sunshine.data.WeatherContract;
import com.katmitchell.sunshine.sync.SunshineSyncAdapter;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.Date;

/**
 * Created by Kat on 7/31/16.
 */
public class Wear {

    private static final String TAG = "Wear";

    public static void updateWear(Context context) {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                    }
                })
                // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult = googleApiClient.blockingConnect();

        if (connectionResult.isSuccess()) {

            // we'll query our contentProvider, as always
            String locationQuery = Utility.getPreferredLocation(context);
            Uri weatherUri = WeatherContract.WeatherEntry
                    .buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());
            Cursor cursor = context.getContentResolver()
                    .query(weatherUri, SunshineSyncAdapter.NOTIFY_WEATHER_PROJECTION, null, null,
                            null);

            if (cursor.moveToFirst()) {
                int weatherId = cursor.getInt(SunshineSyncAdapter.INDEX_WEATHER_ID);
                double high = cursor.getDouble(SunshineSyncAdapter.INDEX_MAX_TEMP);
                double low = cursor.getDouble(SunshineSyncAdapter.INDEX_MIN_TEMP);
                Log.d(TAG, "updateWear with data: " + weatherId + ", " + high + ", " + low);

                PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/forecast");
                putDataMapReq.getDataMap().putInt("weather_id", weatherId);
                putDataMapReq.getDataMap().putDouble("high", high);
                putDataMapReq.getDataMap().putDouble("low", low);
                putDataMapReq.getDataMap().putLong("timestamp", System.currentTimeMillis());
                PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
                putDataMapReq.setUrgent();
                PendingResult<DataApi.DataItemResult> pendingResult =
                        Wearable.DataApi.putDataItem(googleApiClient, putDataReq);
                pendingResult.setResultCallback(new ResultCallbacks<DataApi.DataItemResult>() {
                    @Override
                    public void onSuccess(@NonNull DataApi.DataItemResult dataItemResult) {
                        Log.d(TAG, "onSuccess");
                    }

                    @Override
                    public void onFailure(@NonNull Status status) {
                        Log.d(TAG, "onFailure: " + status.getStatusMessage());
                    }
                });
                Log.d(TAG, "updateWear: sent update");

            }
            cursor.close();

        }

        googleApiClient.disconnect();
    }

    public static void updateWearAsynchronous(Context context) {
        new UpdateWearAsyncTask(context).execute();
    }

    public static class UpdateWearAsyncTask extends AsyncTask<Void, Void, Void> {

        private Context mContext;

        public UpdateWearAsyncTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            updateWear(mContext);
            return null;
        }
    }
}
