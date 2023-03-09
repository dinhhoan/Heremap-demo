package com.here.hellomap;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class LocationWorker extends Worker {

    private static final String TAG = LocationWorker.class.getSimpleName();
    private FusedLocationProviderClient mFusedLocationClient;

    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mFusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                    if (location != null) {
                        double latitude = location.getLatitude();
                        double longitude = location.getLongitude();
                        Log.d(TAG, "Latitude: " + latitude + ", Longitude: " + longitude);
                        // Gửi dữ liệu về MainActivity để cập nhật UI
                        Intent intent = new Intent(MainActivity.TAG);
                        intent.putExtra("latitude", latitude);
                        intent.putExtra("longitude", longitude);
                        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

                    }
                });
                return Result.success();
            } else {
                return Result.failure();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error getting location", ex);
            return Result.failure();
        }
    }
}
