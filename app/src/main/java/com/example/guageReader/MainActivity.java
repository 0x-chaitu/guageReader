package com.example.guageReader;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.guageReader.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.core.Preview;
import androidx.camera.core.CameraSelector;
import android.util.Log;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.PermissionChecker;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.ArrayList;
import android.content.Context;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.Map;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding viewBinding;

    private ImageCapture imageCapture;

    ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                // Callback is invoked after the user selects a media item or closes the
                // photo picker.
                if (uri != null) {
                    Log.d("PhotoPicker", "Selected URI: " + uri);
                    try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        showImageFragment(bitmap);
                    } catch (IOException e) {
                        Log.e(TAG, "Error loading image: " + e.getMessage());
                        Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d("PhotoPicker", "No media selected");
                }
            });
    private TextView textView1;
    private TextView textView2;
    private TextView textView3;

    private RectOverlay rectOverlay;

    private ExecutorService cameraExecutor;



    private ActivityResultLauncher<String[]> activityResultLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        // Handle Permission granted/rejected
                        boolean permissionGranted = true;
                        for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                            if (Arrays.asList(REQUIRED_PERMISSIONS).contains(entry.getKey()) && !entry.getValue()) {
                                permissionGranted = false;
                            }
                        }
                        if (!permissionGranted) {
                            Toast.makeText(getApplicationContext(),
                                    "Permission request denied",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            startCamera();
                        }
                    }
            );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

//        textView1 = viewBinding.textView1;
//        textView2 = viewBinding.textView2;
//        textView3 = viewBinding.textView3;
        rectOverlay = viewBinding.rectOverlay;

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissions();
        }

        viewBinding.imageCaptureButton.setOnClickListener(v -> takePhoto());
        viewBinding.imageSelectButton.setOnClickListener(v -> selectPhoto());
//        viewBinding.videoCaptureButton.setOnClickListener(v -> captureVideo());

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (OpenCVLoader.initLocal()) {
            Log.i("Loaded", "OpenCV loaded successfully");
        } else {
            Log.e("Loaded", "OpenCV initialization failed!");
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

    }

    private void takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        if (imageCapture == null) return;


        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
//                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        Log.e(TAG, "Photo capture failed: " + exc.getMessage(), exc);
                    }

                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        Bitmap bitmap = image.toBitmap();
                        showImageFragment(bitmap);
                    }
                }
        );
    }

    private void selectPhoto() {
        pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }


    private void showImageFragment(Bitmap bitmap) {
        ImageDisplayFragment imageDisplayFragment = ImageDisplayFragment.newInstance(bitmap);
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.main, imageDisplayFragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Used to bind the lifecycle of cameras to the lifecycle owner
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();


                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        //.setTargetAspectRatio(AspectRatio.RATIO_16_9)
                        //.setTargetResolution(Properties.TARGET_RESOLUTION)
                        .build();
                imageAnalyzer.setAnalyzer(cameraExecutor, new ImageAnalyzer());


                // Preview
                Preview preview = new Preview.Builder()
                        .build();

                preview.setSurfaceProvider(viewBinding.viewFinder.getSurfaceProvider());


                imageCapture = new ImageCapture.Builder().build();

                // Select back camera as a default
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll();

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, imageCapture
//                            ,imageAnalyzer
                            );

                } catch (Exception exc) {
                    Log.e(TAG, "Use case binding failed", exc);
                }
            } catch (ExecutionException | InterruptedException e) {
                // Handle any errors that occur while getting the cameraProvider
                Log.e(TAG, "Failed to get camera provider", e);
            }
        }, ContextCompat.getMainExecutor(this));

    }


    private void requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS);
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }





    private static final String TAG = "GuageReader";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static final String[] REQUIRED_PERMISSIONS;

    static {
        ArrayList<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        REQUIRED_PERMISSIONS = permissions.toArray(new String[0]);
    }

    private class ImageAnalyzer implements ImageAnalysis.Analyzer {

        @Override
        public void analyze(@NonNull ImageProxy image) {
            Bitmap bitmap = image.toBitmap();

            Mat mat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4);

            // Convert the Bitmap to Mat
            Utils.bitmapToMat(bitmap, mat);

            // Convert the Mat to grayscale
            Mat grayMat = new Mat();
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY);

            Imgproc.medianBlur(grayMat, grayMat, 5);

            Mat circles = new Mat();
            Imgproc.HoughCircles(grayMat, circles, Imgproc.HOUGH_GRADIENT, 1.0,
                    (double)grayMat.rows()/2, // change this value to detect circles with different distances to each other
                    50.0, 30.0, 0, 0); // change the last two parameters

            double maxRadius = 0;
            int maxRadiusIndex = -1;
            for (int x = 0; x < circles.cols(); x++) {
                double[] c = circles.get(0, x);
                if (c[2] > maxRadius) {
                    maxRadius = c[2];
                    maxRadiusIndex = x;
                }
            }
            double[] c = circles.get(0, maxRadiusIndex);

            if (c != null) {

                Point center = new Point(Math.round(c[0]), Math.round(c[1]));
                // circle radius
                // draw the circle center
                Imgproc.circle(mat, center, 1, new Scalar(0, 255, 0), 3, 8, 0);
                // draw the circle outline
                Imgproc.circle(mat, center, (int) Math.round(c[2]), new Scalar(0, 0, 255), 3, 8, 0);


            }

            Utils.matToBitmap(mat, bitmap);
            rectOverlay.drawBounds(bitmap);
            image.close();
        }
    }
}