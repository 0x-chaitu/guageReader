package com.example.guageReader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageDisplayFragment extends Fragment {

    private ImageView imageView;
    private Bitmap bitmap;

    public static ImageDisplayFragment newInstance(Bitmap bitmap) {
        ImageDisplayFragment fragment = new ImageDisplayFragment();
        Bundle args = new Bundle();


        args.putParcelable("bitmap", bitmap);
        fragment.setArguments(args);
        return fragment;
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.image_display_fragment, container, false);
        imageView = view.findViewById(R.id.imageView);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            bitmap = getArguments().getParcelable("bitmap", Bitmap.class);



            new Thread(() -> {
                Intent serviceIntent = new Intent(getContext(), ImageProcessingService.class);

                try {
                    //Write file
                    String filename = "bitmap.png";
                    File file = new File(getContext().getFilesDir(), filename);
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                        // PNG is a lossless format, the compression factor (100) is ignored
                        out.close();
                        bitmap.recycle();

                        //Pop intent
                        serviceIntent.putExtra("image", file);
                        ContextCompat.startForegroundService(getContext(), serviceIntent);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();


            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mMessageReceiver, new IntentFilter("image_processed"));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mMessageReceiver);
    }




    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("image_processed")) {
                Bitmap processedBitmap = intent.getParcelableExtra("processed_bitmap", Bitmap.class);
                if (processedBitmap != null) {
//                    Canvas canvas = new Canvas(bitmap);
//                    Paint paint = new Paint();
//                    paint.setColor(Color.RED);
//                    canvas.drawBitmap(processedBitmap, 0, 0, paint);
                    imageView.setImageBitmap(processedBitmap);
                }
            }
        }
    };


}
