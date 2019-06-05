package com.example.facestat;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //FaceDetector detector = new FaceDetector.Builder(this)
        //        .setProminentFaceOnly(true)
        //        .build();

        FaceDetector detector = new FaceDetector.Builder(this)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setProminentFaceOnly(true)
                .setTrackingEnabled(false) // <-- set to false
                .build();


        ArrayList<Float> leftEyeProbs = new ArrayList<>();
        ArrayList<Float> rightEyeProbs = new ArrayList<>();
        ArrayList<Float> smileProbs = new ArrayList<>();

        AssetManager assetManager= getAssets();
        try {
            String assets[] = assetManager.list("img");

            for (int i = 0; i < assets.length; i++) {
                InputStream inputStream = getAssets().open("img/" + assets[i]);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                Frame.Builder frameBuilder = new Frame.Builder();
                frameBuilder.setBitmap(bitmap);
                Frame frame = frameBuilder.build();

                SparseArray<Face> faces = detector.detect(frame);
                if (faces.size() > 0) {
                    Face face = faces.valueAt(0);
                    leftEyeProbs.add(face.getIsLeftEyeOpenProbability());
                    rightEyeProbs.add(face.getIsRightEyeOpenProbability());
                    smileProbs.add(face.getIsSmilingProbability());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        float leftEyeProbSum = 0.0f;
        float rightEyeProbSum = 0.0f;
        float smileProbSum = 0.0f;

        int numImages = leftEyeProbs.size();
        for (int i = 0; i < numImages; i++) {
            leftEyeProbSum += leftEyeProbs.get(i);
            rightEyeProbSum += rightEyeProbs.get(i);
            smileProbSum += smileProbs.get(i);
        }

        System.out.println("TOTAL NUMBER OF IMAGES: " + numImages);
        System.out.println("LEFT EYE MEAN: " + leftEyeProbSum / (float) numImages);
        System.out.println("RIGHT EYE MEAN: " + rightEyeProbSum / (float) numImages);
        System.out.println("SMILE MEAN: " + smileProbSum / (float) numImages);

        // TODO calc std
    }
}
