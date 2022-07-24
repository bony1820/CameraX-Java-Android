package com.example.camerax;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private String TAG = "MainActivity";

    // variable view
    private PreviewView previewView;
    private Button takePhotoBtn, recordVideoBtn;

    // variable
    private ListenableFuture<ProcessCameraProvider> cameraProviderListenableFuture;
    private ImageCapture imageCapture;
    private VideoCapture videoCapture;
    private ImageAnalysis imageAnalysis;

    private ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                Log.d(TAG, ": " + isGranted);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Lấy phần tử preview camera
        previewView = findViewById(R.id.previewCamera);
        takePhotoBtn = findViewById(R.id.takePhotoBtn);
        recordVideoBtn = findViewById(R.id.recordVideoBtn);

        takePhotoBtn.setOnClickListener(this);
        recordVideoBtn.setOnClickListener(this);

        //Yêu cầu quyền truy cập camera
        requestPermissionLauncher.launch(new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        });

        cameraProviderListenableFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderListenableFuture.addListener(() -> {

            //Khi phần cứng khả thi thì lấy ra camera provider
            try {
                ProcessCameraProvider cameraProvider = cameraProviderListenableFuture.get();
                startCamera(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecutor());
    }


    @SuppressLint("RestrictedApi")
    private void startCamera(@NonNull ProcessCameraProvider cameraProvider) {
        Display display = ((WindowManager)
                getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        // Select camera
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Preview use case
        Preview preview = new Preview.Builder()
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image capture use case
        imageCapture = new ImageCapture.Builder()
                .setTargetRotation(display.getRotation())
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();


        // Video capture use case
        videoCapture = new VideoCapture.Builder()
                .setVideoFrameRate(30)
                .build();

        // Image Analysis use case
        imageAnalysis = new ImageAnalysis.Builder()
                // enable the following line if RGBA output is needed.
                //.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(getExecutor(), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                Log.d(TAG, "analyze: " + rotationDegrees);
                // insert your code here.
                // after done, release the ImageProxy object
                imageProxy.close();
            }
        });

        cameraProvider.bindToLifecycle(
                (LifecycleOwner) this,
                cameraSelector,
                preview,
//                imageAnalysis,
                imageCapture,
                videoCapture
        );
    }

    private void capturePhoto() {
        if (imageCapture == null) return;
        Log.d(TAG, "capturePhoto: ");
        long timestamp = System.currentTimeMillis();

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build();

        ImageCapture.OnImageSavedCallback onImageSavedCallback =
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Toast.makeText(MainActivity.this, "Photo has been saved successfully.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this, "Error saving photo: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                };
        imageCapture.takePicture(
                outputFileOptions,
                getExecutor(),
                onImageSavedCallback
        );
    }

    @SuppressLint("RestrictedApi")
    private void recordVideo() {
        if (videoCapture == null) return;
        long timestamp = System.currentTimeMillis();

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestamp);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");

        @SuppressLint("RestrictedApi")
        VideoCapture.OutputFileOptions outputFileOptions =
                new VideoCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build();

        VideoCapture.OnVideoSavedCallback onVideoSavedCallback =
                new VideoCapture.OnVideoSavedCallback() {
                    @SuppressLint("RestrictedApi")
                    @Override
                    public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                        Toast.makeText(MainActivity.this, "Video has been saved successfully.", Toast.LENGTH_SHORT).show();
                    }

                    @SuppressLint("RestrictedApi")
                    @Override
                    public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                        Toast.makeText(MainActivity.this, "Error saving video: " + message, Toast.LENGTH_SHORT).show();
                    }
                };
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        videoCapture.startRecording(outputFileOptions, getExecutor(), onVideoSavedCallback);
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for (String permission: permissions) {
            Log.d(TAG, "onRequestPermissionsResult: " + permission);
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.takePhotoBtn:
                capturePhoto();
                break;
            case R.id.recordVideoBtn:
                if (recordVideoBtn.getText() == getText(R.string.recordVideo)) {
                    recordVideoBtn.setText(getText(R.string.recordingVideo));
                    recordVideo();
                } else {
                    recordVideoBtn.setText(getText(R.string.recordVideo));
                    videoCapture.stopRecording();
                }
                break;

        }
    }
}