/*
 * Copyright (C) 2019-2023 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.hellomap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.here.hellomap.PermissionsRequestor.ResultListener;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.engine.SDKNativeEngine;
import com.here.sdk.core.engine.SDKOptions;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.mapview.MapError;
import com.here.sdk.mapview.MapImage;
import com.here.sdk.mapview.MapImageFactory;
import com.here.sdk.mapview.MapMarker;
import com.here.sdk.mapview.MapMeasure;
import com.here.sdk.mapview.MapScene;
import com.here.sdk.mapview.MapScheme;
import com.here.sdk.mapview.MapView;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {


    public static final String TAG = MainActivity.class.getSimpleName();
    private PermissionsRequestor permissionsRequestor;
    private MapView mapView;

    private WorkManager mWorkManager;

    // Define a variable to hold the map marker.
    private MapMarker mGreenDot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Usually, you need to initialize the HERE SDK only once during the lifetime of an application.
        initializeHERESDK();

        setContentView(R.layout.activity_main);

        // Get a MapView instance from layout.
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);

        mapView.setOnReadyListener(new MapView.OnReadyListener() {
            @Override
            public void onMapViewReady() {
                // This will be called each time after this activity is resumed.
                // It will not be called before the first map scene was loaded.
                // Any code that requires map data may not work as expected until this event is received.
                Log.d(TAG, "HERE Rendering Engine attached.");
            }
        });

        handleAndroidPermissions();

        // Register BroadcastReceiver
        IntentFilter filter = new IntentFilter(TAG);
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocationUpdateReceiver, filter);

        // Create WorkRequest
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest saveRequest =
                new PeriodicWorkRequest.Builder(LocationWorker.class, 3, TimeUnit.SECONDS)
                        .setConstraints(constraints)
                        .build();
        // Enqueue WorkRequest
        mWorkManager = WorkManager.getInstance(this);
        mWorkManager.enqueue(saveRequest);

    }

    private void initializeHERESDK() {
        // Set your credentials for the HERE SDK.
        String accessKeyID = "xR_kqRMKrdyWUaiONQUNuQ";
        String accessKeySecret = "6ixG94occ9EZRpkKAtFW9-YstVk0EsbEJ8M6WPXyU8vtZ3lN592K4r8C89NjCNVKTjClwbzxPxM2vt6kcLFy4A";
        SDKOptions options = new SDKOptions(accessKeyID, accessKeySecret);
        try {
            Context context = this;
            SDKNativeEngine.makeSharedInstance(context, options);
        } catch (InstantiationErrorException e) {
            throw new RuntimeException("Initialization of HERE SDK failed: " + e.error.name());
        }
    }

    private void handleAndroidPermissions() {
        permissionsRequestor = new PermissionsRequestor(this);
        permissionsRequestor.request(new ResultListener(){

            @Override
            public void permissionsGranted() {
                Toast.makeText(MainActivity.this, "permissionsGranted", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void permissionsDenied() {
                Log.e(TAG, "Permissions denied by user.");
            }
        });
    }

    private BroadcastReceiver mLocationUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double lat = intent.getDoubleExtra("latitude", 0.0);
            double lng = intent.getDoubleExtra("longitude", 0.0);
            Log.d(TAG, "onReceive: lat=" + lat + ", lng=" + lng);
            // Update UI here

            GeoCoordinates geoCoordinates = new GeoCoordinates(lat, lng);
            MapImage mapImage = MapImageFactory.fromResource(context.getResources(), R.drawable.ic_plot_small);
            MapMarker mapMarker = new MapMarker(geoCoordinates, mapImage);
            mapView.getMapScene().addMapMarker(mapMarker);

            Toast.makeText(context, "Update location" + lat + "and" + lng, Toast.LENGTH_SHORT).show();
            loadMapScene(lat, lng);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionsRequestor.onRequestPermissionsResult(requestCode, grantResults);
    }

    private void loadMapScene(double lag, double lng) {
        // Load a scene from the HERE SDK to render the map with a map scheme.
        mapView.getMapScene().loadScene(MapScheme.NORMAL_DAY, new MapScene.LoadSceneCallback() {
            @Override
            public void onLoadScene(@Nullable MapError mapError) {
                if (mapError == null) {
                    double distanceInMeters = 1000 * 10;
                    MapMeasure mapMeasureZoom = new MapMeasure(MapMeasure.Kind.DISTANCE, distanceInMeters);
                    mapView.getCamera().lookAt(new GeoCoordinates(lag, lng), mapMeasureZoom);
                } else {
                    Log.d(TAG, "Loading map failed: mapError: " + mapError.name());
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocationUpdateReceiver);
        mapView.onDestroy();
        disposeHERESDK();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        mapView.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }
    
    private void disposeHERESDK() {
        // Free HERE SDK resources before the application shuts down.
        // Usually, this should be called only on application termination.
        // Afterwards, the HERE SDK is no longer usable unless it is initialized again.
        SDKNativeEngine sdkNativeEngine = SDKNativeEngine.getSharedInstance();
        if (sdkNativeEngine != null) {
            sdkNativeEngine.dispose();
            // For safety reasons, we explicitly set the shared instance to null to avoid situations,
            // where a disposed instance is accidentally reused.
            SDKNativeEngine.setSharedInstance(null);
        }
    }
}
