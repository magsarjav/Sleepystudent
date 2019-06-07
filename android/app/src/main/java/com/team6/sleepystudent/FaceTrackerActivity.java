/*
 * Copyright (C) The Android Open Source Project
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
package com.team6.sleepystudent;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;


/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;


    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int PERMISSION_ALL = 1;


    //thresholds for drowsiness detector
    private static final float EYE_CLOSENESS_THRESHOLD = 0.57f;
    private static final long DROWSINESS_THRESHOLD_SECONDS = 5;
    private static final long SLEEPING_THRESHOLD_SECONDS = 10;


    public static boolean CURRENT_DROWSINESS_STATUS;
    public static long NORMAL_BEGINS_AT;
    public static long SLEEPING_BEGINS_AT;
    private static boolean isRecording;
    public static String RECORDED_AUDIO_FILE;

    private MediaRecorder recorder;

    private TextView drowsyStatus;
    private ImageView drowsyIcon;
    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_facetracker);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mPreview = findViewById(R.id.preview);
        mGraphicOverlay = findViewById(R.id.faceOverlay);


        drowsyStatus = findViewById(R.id.drowsyStatus);
        drowsyIcon = findViewById(R.id.drowsyIcon);


        android.support.v7.preference.PreferenceManager
                .setDefaultValues(this, R.xml.preferences, false);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        String[] PERMISSIONS = {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.VIBRATE
        };

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }

        createCameraSource();
        refreshCameraSettings(getBaseContext());

        isRecording = false;
        FloatingActionButton stopAudioButton = findViewById(R.id.btn_stop_audio);
        findViewById(R.id.btn_stop_audio).setVisibility(View.INVISIBLE);
        stopAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopRecording();
                isRecording = false;
                findViewById(R.id.btn_stop_audio).setVisibility(View.INVISIBLE);
                findViewById(R.id.btn_settings).setVisibility(View.VISIBLE);

                AlertDialog.Builder builder = new AlertDialog.Builder(FaceTrackerActivity.this);
                builder.setTitle("Audio captured")
                        .setMessage("Recorded audio file saved in " + RECORDED_AUDIO_FILE)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Continue with delete operation
                            }
                        })
                        .show();
            }
        });

        FloatingActionButton settingsButton = findViewById(R.id.btn_settings);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(view.getContext(), SettingsActivity.class);
                startActivity(intent);
            }
        });


        //Initial state of detection
        //changeToNoFace();
    }

    public static boolean hasPermissions(Context context, String[] permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean hasPermissionRecord(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshCameraSettings(Context context) {
        SharedPreferences sharedPref = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(context);
        boolean viewCamera = sharedPref.getBoolean
                ("switch_camera", false);
        boolean debugMode = sharedPref.getBoolean
                ("switch_debug", false);

        if (viewCamera) {
            mPreview.setVisibility(View.VISIBLE);
            if (debugMode) {
                mGraphicOverlay.setVisibility(View.VISIBLE);
            } else {
                mGraphicOverlay.setVisibility(View.GONE);
            }
        } else {
            mPreview.setVisibility(View.INVISIBLE);
            mGraphicOverlay.setVisibility(View.GONE);
        }

        CURRENT_DROWSINESS_STATUS = false;
        NORMAL_BEGINS_AT = System.currentTimeMillis();

        changeToNoFace();

    }


    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        Context context = getApplicationContext();
        FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setProminentFaceOnly(true)
                .build();

        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!detector.isOperational()) {
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }


        mCameraSource = new CameraSource.Builder(context, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(20.0f)
                .build();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Log.i("disp", metrics.heightPixels + " " + metrics.widthPixels);
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
        refreshCameraSettings(this);
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();

    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }

    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {


        if (requestCode != PERMISSION_ALL) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("We are sorry")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }

    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    //Check current drowsy status
    public static boolean isDrowsy() {
        if ((System.currentTimeMillis() - FaceTrackerActivity.SLEEPING_BEGINS_AT) / 1000 >= DROWSINESS_THRESHOLD_SECONDS) {
            return true;
        } else {
            return false;

        }
    }

    //Check current sleeping status
    public static boolean isSleeping() {
        if ((System.currentTimeMillis() - FaceTrackerActivity.SLEEPING_BEGINS_AT) / 1000 >= SLEEPING_THRESHOLD_SECONDS) {
            return true;
        } else {
            return false;

        }
    }


    //Shows status when no face is detected
    public void changeToNoFace() {
        drowsyStatus.setText("No face");
        drowsyStatus.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.colorGray));
        drowsyIcon.setImageResource(R.drawable.ic_face_dis_light);
        drowsyIcon.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.colorGray));

        clearTopLayout();
    }

    //Shows status when face is detected
    public void changeToTrackingFace() {
        drowsyStatus.setText("Good");
        drowsyStatus.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.colorGreen));
        drowsyIcon.setImageResource(R.drawable.ic_face_smile_light);
        drowsyIcon.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.colorGreen));

        clearTopLayout();
    }

    //Shows status when drowsiness is detected
    public void changeToWarning() {
        drowsyStatus.setText("Wake up");
        drowsyStatus.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.colorRed));
        drowsyIcon.setImageResource(R.drawable.ic_face_neutral_light);
        drowsyIcon.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.colorRed));


        SharedPreferences sharedPref = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean switchVibrating = sharedPref.getBoolean
                ("switch_vibrating", false);
        if (switchVibrating) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            // Vibrate for 500 milliseconds
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            else {
                //deprecated in API 26
                v.vibrate(500);
            }
        }

        boolean switchFlickering = sharedPref.getBoolean
                ("switch_flickering", false);

        if (switchFlickering) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int randomnumber = new Random().nextInt(3) + 1;
                    int c = R.color.colorRed;
                    if (randomnumber == 1) {
                        c = R.color.colorGreen;
                    } else if (randomnumber == 2) {
                        c = R.color.colorWhite;
                    }
                    findViewById(R.id.topLayout).setForeground(new ColorDrawable(ContextCompat.getColor(getBaseContext(), c)));
                }
            });


        }


    }

    //Shows status when sleeping is detected
    public void changeToSleeping() {
        SharedPreferences sharedPref = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean switchRecording = sharedPref.getBoolean
                ("switch_recording", false);

        if (switchRecording && hasPermissionRecord(this)) {

            showRecordingStatus();

            if (!isRecording) {
                isRecording = true;
                startRecording();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                findViewById(R.id.btn_stop_audio).setVisibility(View.VISIBLE);
                                findViewById(R.id.btn_settings).setVisibility(View.INVISIBLE);
                            }
                        });
                    }
                }).start();
            }

        } else {
            drowsyStatus.setText("Sleep well");
            drowsyStatus.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.colorGreen));
            drowsyIcon.setImageResource(R.drawable.ic_face_neutral_light);
            drowsyIcon.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.colorGreen));
        }

        clearTopLayout();
    }

    private void showRecordingStatus() {
        drowsyStatus.setText("Recording");
        drowsyStatus.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.colorGreen));
        drowsyIcon.setImageResource(R.drawable.ic_mic_light);
        drowsyIcon.setColorFilter(ContextCompat.getColor(getBaseContext(), R.color.colorGreen));
    }

    private void clearTopLayout() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.topLayout).setForeground(null);
            }
        });
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);

            if (face.getIsLeftEyeOpenProbability() > EYE_CLOSENESS_THRESHOLD || face.getIsRightEyeOpenProbability() > EYE_CLOSENESS_THRESHOLD) {

                if (CURRENT_DROWSINESS_STATUS) {
                    NORMAL_BEGINS_AT = System.currentTimeMillis();
                } else {
                    SLEEPING_BEGINS_AT = System.currentTimeMillis();
                }
                CURRENT_DROWSINESS_STATUS = false;
            } else {
                if (!CURRENT_DROWSINESS_STATUS) {
                    SLEEPING_BEGINS_AT = System.currentTimeMillis();
                } else {
                    NORMAL_BEGINS_AT = System.currentTimeMillis();
                }
                CURRENT_DROWSINESS_STATUS = true;
            }

            if (!isRecording) {
                if (isSleeping()) {
                    changeToSleeping();
                } else {
                    if (isDrowsy()) {
                        changeToWarning();
                    } else {
                        changeToTrackingFace();
                    }
                }
            }


        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);

            if (!isRecording) {
                changeToNoFace();
            }
            CURRENT_DROWSINESS_STATUS = false;
            SLEEPING_BEGINS_AT = System.currentTimeMillis();
        }


        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }

    }

    private void startRecording() {
        Log.i("Audio", "Recording start");
        long now = System.currentTimeMillis();
        Date date = new Date(now);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        String getTime = sdf.format(date);

        String externalStorage = Environment.getExternalStorageDirectory().getAbsolutePath();

        File f = new File(externalStorage, "recorded_audio");
        if (!f.exists()) {
            f.mkdirs();
        }

        RECORDED_AUDIO_FILE = externalStorage + "/recorded_audio/" + getTime + ".3gp";

        Log.i("Audio", "Filename : " + RECORDED_AUDIO_FILE);

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(RECORDED_AUDIO_FILE);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e("Audio", "prepare() failed");
        }

        recorder.start();


    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
            Log.i("Audio", "Recording done");
        }

    }


}
