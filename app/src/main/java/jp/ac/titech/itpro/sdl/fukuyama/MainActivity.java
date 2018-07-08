package jp.ac.titech.itpro.sdl.fukuyama;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    static {
        System.loadLibrary("opencv_java3");
    }
    private static final String TAG = "MainActivity";
    private final static int REQ_PHOTO = 1234;
    private static final String KEY_CAMERA_URI = "MainActivity.cameraUri";
    private static final String KEY_FILE_PATH = "MainActivity.filePath";
    private ImageView imageView = null;
    private Uri cameraUri;
    private String filePath;
    private CascadeClassifier faceDetector;
    private Bitmap fukuyama;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button photoButton = findViewById(R.id.photo_button);
        imageView = findViewById(R.id.photo_view);
        photoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraIntent();
            }
        });

        checkPermission();
        try {
            initFaceDetetcor();
        }
        catch (IOException e) { e.printStackTrace(); }

        InputStream inStream = this.getResources().openRawResource(R.raw.fukuyama);
        fukuyama = BitmapFactory.decodeStream(inStream);
    }
    protected void initFaceDetetcor() throws IOException {
        InputStream inStream = this.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
        File cascadeDir = this.getDir("cascade", Context.MODE_PRIVATE);
        File cascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt.xml");
        // 取得したxmlファイルを特定ディレクトリに出力
        FileOutputStream outStream = new FileOutputStream(cascadeFile);
        byte[] buf = new byte[2048];
        int rdBytes;
        while ((rdBytes = inStream.read(buf)) != -1) {
            outStream.write(buf, 0, rdBytes);
        }
        outStream.close();
        inStream.close();
        // 出力したxmlファイルのパスをCascadeClassifierの引数にする
        faceDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
        // CascadeClassifierインスタンスができたら出力したファイルはいらないので削除
        if (faceDetector.empty()) {
            faceDetector = null;
        } else {
            cascadeDir.delete();
            cascadeFile.delete();
        }
    }



    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        super.onActivityResult(reqCode, resCode, data);
        switch (reqCode) {
            case REQ_PHOTO:
                if (resCode == RESULT_OK) {
                    if(cameraUri != null){
                        Log.d("poi", "poi~");
//                        filePath = data.getStringExtra(KEY_FILE_PATH);
                        imageView.setImageURI(cameraUri);

                        Mat matImg = new Mat();
                        Bitmap bmp = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
                        Utils.bitmapToMat(bmp, matImg);
                        bmp = bmp.copy(Bitmap.Config.ARGB_8888, true);
                        Canvas canvas = new Canvas(bmp);
                        MatOfRect faceRects = new MatOfRect();
                        faceDetector.detectMultiScale(matImg, faceRects);
                        Log.i(TAG ,"認識された顔の数:" + faceRects.toArray().length);
                        for (Rect face : faceRects.toArray()) {
                            canvas.drawBitmap(fukuyama, null, new android.graphics.Rect(face.x, face.y, face.x + face.width, face.y + face.height), new Paint());
                            Log.i(TAG ,"顔の縦幅" + face.height);
                            Log.i(TAG ,"顔の横幅" + face.width);
                            Log.i(TAG ,"顔の位置（Y座標）" + face.y);
                            Log.i(TAG ,"顔の位置（X座標）" + face.x);
                        }
                        imageView.setImageBitmap(bmp);
                        try {
                            FileOutputStream fostream = new FileOutputStream(filePath);
                            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fostream);
                            fostream.flush();
                            fostream.close();
                            break;
                        }
                        catch (IOException e) { e.printStackTrace(); }

                        registerDatabase(filePath);
                    }
                }
                break;
        }
    }
    private void registerDatabase(String file) {
        ContentValues contentValues = new ContentValues();
        ContentResolver contentResolver = MainActivity.this.getContentResolver();
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        contentValues.put("_data", file);
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,contentValues);
    }

    private void cameraIntent(){
        File cameraFolder = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES),"img_Fukuyama");
        cameraFolder.mkdirs();

        String fileName = new SimpleDateFormat(
                "yyyyddHHmmss", Locale.US).format(new Date());
        filePath = String.format("%s/%s.jpg", cameraFolder.getPath(),fileName);
        Log.d("debug","filePath:"+filePath);

        File cameraFile = new File(filePath);
        cameraUri = FileProvider.getUriForFile(
                MainActivity.this,
                getApplicationContext().getPackageName() + ".provider",
                cameraFile);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
//        intent.putExtra(KEY_FILE_PATH, filePath);
        startActivityForResult(intent, REQ_PHOTO);
    }

    public void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            requestStoragePermissions();
        }
    }

    private void requestStoragePermissions() {
        final int REQUEST_PERMISSION = 1000;
        ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE },
                    REQUEST_PERMISSION);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_CAMERA_URI, cameraUri);
        outState.putString(KEY_FILE_PATH, filePath);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        cameraUri = savedInstanceState.getParcelable(KEY_CAMERA_URI);
        filePath = savedInstanceState.getString(filePath);
        imageView = findViewById(R.id.photo_view);
        if(cameraUri != null){
            imageView.setImageURI(cameraUri);
        }
    }
}
