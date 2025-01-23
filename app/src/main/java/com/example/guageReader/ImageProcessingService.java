package com.example.guageReader;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import com.googlecode.tesseract.android.TessBaseAPI;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageProcessingService extends Service {

    private static final String CHANNEL_ID = "image_processing_channel";
    private File file ;


    private NotificationManager notificationManager;

    private TessBaseAPI tessBaseAPI = new TessBaseAPI();


    private Interpreter interpreter;

    private Point center = new Point();

    private double maxRadius = 0;


    private int inputImageWidth;

    private int inputImageHeight;

    private static int OUTPUT_CLASSES_COUNT = 10;

    public ImageProcessingService() {
    }

//    public void setImage(Bitmap bitmap) {
//        this.bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
//    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Create Notification Channel (for Android 8.0 and above)
        CharSequence name = "Image Processing";
        String description = "Service for processing images";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);

        Assets.extractAssets(getBaseContext());


        if (!tessBaseAPI.init(Assets.getTessDataPath(getBaseContext()), "eng", TessBaseAPI.OEM_DEFAULT)) {
            tessBaseAPI.recycle();
        }

        tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK);
        tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "-0123456789");




        // Start image processing in the background
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Show a notification indicating that the service is running
        Intent notificationIntent = new Intent(this, ImageDisplayFragment.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Image Processing")
                .setContentText("Processing in background...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();

        if (intent != null && intent.hasExtra("image")) {
            file = intent.getParcelableExtra("image", File.class);
//            new Thread(this::processImage).start();
            new Thread(this::processGuage).start();

//            if (bitmap != null) {
//                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true); // Create a mutable copy
//            }
        }

        startForeground(1, notification);

        return START_STICKY; // Restart the service if it's killed
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }
        if (interpreter != null) {
            interpreter.close();
        }
        tessBaseAPI.recycle();

    }


    private void findCircle() {
        Bitmap bitmap;
        try {
            FileInputStream stream = new FileInputStream(file);
            bitmap = BitmapFactory.decodeStream(stream);
            stream.close();

            Mat mat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4);

            // Convert the Bitmap to Mat
            Utils.bitmapToMat(bitmap, mat);

            Mat blurMat = new Mat();
            Imgproc.GaussianBlur(mat, blurMat, new org.opencv.core.Size(5, 5), 0);



            // Convert the Mat to grayscale
            Mat grayMat = new Mat();
            Imgproc.cvtColor(blurMat, grayMat, Imgproc.COLOR_BGR2GRAY);

            Mat thresh = new Mat();
            Imgproc.threshold(grayMat, thresh, 128, 255, Imgproc.THRESH_BINARY);


            Mat edges = new Mat();
            Imgproc.Canny(thresh, edges, 50, 200, 3);



            Mat circles = new Mat();
            Imgproc.HoughCircles(edges, circles, Imgproc.HOUGH_GRADIENT, 1.0,
                    (double)edges.rows()/2, // change this value to detect circles with different distances to each other
                    50.0, 30.0, 0, 0); // change the last two parameters

            maxRadius = 0;
            int maxRadiusIndex = -1;
            for (int x = 0; x < circles.cols(); x++) {
                double[] c = circles.get(0, x);
                if (c[2] > maxRadius) {
                    Point center = new Point(Math.round(c[0]), Math.round(c[1]));
                    if (center.x < ((double) bitmap.getWidth() /2) + 30 && center.x > ((double) bitmap.getWidth() /2) - 30 ) {

                        maxRadius = c[2];
                        maxRadiusIndex = x;
                    }
                }

            }
            double[] c = circles.get(0, maxRadiusIndex);

            if (c != null) {
                center = new Point(Math.round(c[0]), Math.round(c[1]));
            }

        } catch (FileNotFoundException e) {

            e.printStackTrace();
        } catch (IOException e) {

            throw new RuntimeException(e);
        }


    }


    private void processGuage() {

        // find circle guage
        findCircle();
        Bitmap bitmap;
        Point possibleNeedlePoint = new Point();
        try {
            FileInputStream stream = new FileInputStream(file);
            bitmap = BitmapFactory.decodeStream(stream);
            stream.close();

            Mat mat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4);

            // Convert the Bitmap to Mat
            Utils.bitmapToMat(bitmap, mat);

            // Convert the Mat to grayscale
            Mat grayMat = new Mat();
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY);

            Mat thresh = new Mat();
            Imgproc.threshold(grayMat, thresh, 128, 255, Imgproc.THRESH_BINARY);

            Mat edges = new Mat();
            Imgproc.Canny(thresh, edges, 50, 200, 3);

            Mat lines = new Mat(); // will hold the results of the detection
            Imgproc.HoughLinesP(edges, lines, 1, Math.PI/180, 15, 50, 10);

            Imgproc.circle(mat, center, 1, new Scalar(255, 0,0, 255), 3, 8, 0);
            Imgproc.circle(mat, center, (int) Math.round(maxRadius), new Scalar(255, 0, 0, 0), 3, 8, 0);

            Mat mask = Mat.zeros(edges.rows(), edges.cols(), edges.type());

            // Color space outside our guage (circle) with black
            Imgproc.circle(mask, center, (int) Math.round(maxRadius) - 65, new Scalar(255, 255, 255, 255), -1);
            Core.bitwise_and(edges, mask, edges);

            // find the approximate needle
            if (lines.rows() > 0) {
                double maxDist = 80;

                double needleDist = 0;
                for (int i = 0; i < lines.rows(); i++) {
                    double[] l = lines.get(i, 0);
                    double dist1 = Math.sqrt(Math.pow(l[0] - center.x, 2) + Math.pow(l[1] - center.y, 2));
                    double dist2 = Math.sqrt(Math.pow(l[2] - center.x, 2) + Math.pow(l[3] - center.y, 2));
                    if (dist2 <= maxDist || dist1 <= maxDist) {
                        if (dist2 > dist1 && needleDist < dist2) {
                            possibleNeedlePoint = new Point(l[2], l[3]);
                            needleDist = dist2;
                        } else if ( dist1 > dist2 && needleDist < dist1 ) {
                            possibleNeedlePoint = new Point(l[0], l[1]);
                            needleDist = dist1;
                        }
                    }
                }
                Imgproc.line(mat, new Point(possibleNeedlePoint.x, possibleNeedlePoint.y), new Point(center.x, center.y), new Scalar(255, 0, 0, 255), 1, Imgproc.LINE_AA, 0);

            }

            // find contours which can be numbers
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();

            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // Color all contours which are not numbers with black
            for (MatOfPoint contour : contours) {
                Rect boundingRect = Imgproc.boundingRect(contour);
                if (
                        boundingRect.width >= 0.8 * boundingRect.height && boundingRect.width <= 150
                                && !(boundingRect.height < 12 && boundingRect.height >= 6)
                ) {
                    Imgproc.rectangle(edges,boundingRect, new Scalar(0, 0,0 ,0 ), -1);
                }
            }

            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(6, 1));
            Mat dilated = new Mat();
            Imgproc.dilate(edges, dilated, kernel, new Point(-1, -1), 3);

            Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            Utils.matToBitmap(thresh, bitmap);

            int max = -10000;
            Rect maxRect = new Rect();


            int min = 10000;
            Rect minRect = new Rect();


            // find max and min and the bounding boxes
            for (MatOfPoint contour : contours) {
                Rect boundingRect = Imgproc.boundingRect(contour);

                if ( boundingRect.height >= 35 && boundingRect.height < 80 && boundingRect.width < 100
                ) {
                    Bitmap roiBitmap = Bitmap.createBitmap(bitmap, boundingRect.x, boundingRect.y,
                            boundingRect.width, boundingRect.height);
                    String val = classify(roiBitmap);
                    try {
                        int foo = Integer.parseInt(val);
                        if (foo > max) {
                            maxRect = boundingRect;
                            max = foo;
                        }
                        if (foo < min) {
                            boolean inside = false;

                            // check if contour region is not a region of other numbers contour
                            // for ex: 0 can belong to 800, 50 ..
                            Point centreOfCountour  = new Point(boundingRect.x + (double) (boundingRect.width) / 2,
                                    boundingRect.y + (double) (boundingRect.height) / 2);
//                            for (MatOfPoint outerC : contours) {
//                                if (outerC != contour) {
//                                    double i = Imgproc.pointPolygonTest(new MatOfPoint2f(outerC.toArray()), centreOfCountour,
//                                            false);
//
//                                    Log.d("bitmap", String.valueOf(i) + " " + foo + " " + min);
//
//                                    if (i > 0) {
//                                        inside = true;
//                                    }
//                                }
//                            }
                            if (inside != true) {
                                minRect = boundingRect;
                                min = foo;
                            }
                        }
                    }
                    catch (NumberFormatException e) {
                    }
                }
            }


            drawBox(maxRect, mat);
            drawBox(minRect, mat);

            double angle1 = calculateAngle(center,new Point(minRect.x + (double) minRect.width /2,minRect.y + (double) minRect.height /2 ));
            double angle2 = calculateAngle(center, new Point(maxRect.x + (double) maxRect.width /2,maxRect.y + (double) maxRect.height /2 ));
            double angle3 = calculateAngle(center, possibleNeedlePoint);

            Utils.matToBitmap(mat, bitmap);

            Intent intent = new Intent("image_processed");
            intent.putExtra("processed_bitmap", bitmap);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            Intent intent2 = new Intent("values");
            intent2.putExtra("min", min);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent2);

            intent2.putExtra("max", max);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent2);

            intent2.putExtra("minAngle", angle1);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent2);
            intent2.putExtra("maxAngle", angle2);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent2);
            intent2.putExtra("needleAngle", angle3);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent2);

        } catch (FileNotFoundException e) {

            e.printStackTrace();
        } catch (IOException e) {

            throw new RuntimeException(e);
        }


    }

    void drawBox(Rect rect, Mat mat) {
        Point pt1 = new Point(rect.x, rect.y);
        Point pt2 = new Point(rect.x + rect.width, rect.y);
        Point pt3 = new Point(rect.x + rect.width, rect.y + rect.height);
        Point pt4 = new Point(rect.x, rect.y + rect.height);
        Scalar color = new Scalar(255, 0, 0, 225);
        Imgproc.line(mat, pt1, pt2,color, 2); // Top line
        Imgproc.line(mat, pt2, pt3, color, 2); // Right line
        Imgproc.line(mat, pt3, pt4, color, 2); // Bottom line
        Imgproc.line(mat, pt4, pt1, color, 2); // Left line
    }

    public static double calculateAngle(Point p1, Point p2) {
        double ydiff = p1.y - p2.y;
        double xdiff = p2.x - p1.x;
        double angle = Math.atan2(ydiff, xdiff);
        angle = Math.toDegrees(angle);

        Log.d("bitmap", String.valueOf(angle)+ ydiff + " " + xdiff);
        if ( (ydiff < 0 && xdiff < 0) || ( ydiff < 0 && xdiff > 0) ) {
            angle += 360;
        }

        return angle;
    }

    private String classify(Bitmap bitmap) {

        Mat mat = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC4);

        Utils.bitmapToMat(bitmap, mat);

        int borderSize = 20;
        Scalar borderColor = new Scalar(255, 255, 255, 255); // White color

        // Add border to the image
        Mat dst = new Mat();

        Core.copyMakeBorder(mat, dst, borderSize, borderSize, borderSize, borderSize,
                Core.BORDER_CONSTANT, borderColor);

//        Mat grayMat = new Mat();
//        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY);

        Mat thresh = new Mat();
        Imgproc.threshold(dst, thresh, 0, 255, Imgproc.THRESH_BINARY);

        Bitmap borderedBitmap = Bitmap.createBitmap(thresh.cols(), thresh.rows() , Bitmap.Config.ARGB_8888);

        Utils.matToBitmap(thresh, borderedBitmap);

        tessBaseAPI.setImage(borderedBitmap);

        String x = tessBaseAPI.getUTF8Text();;

        return x;
    }

}