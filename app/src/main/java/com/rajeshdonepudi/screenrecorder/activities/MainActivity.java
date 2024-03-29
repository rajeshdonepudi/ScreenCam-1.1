package com.rajeshdonepudi.screenrecorder.activities;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.rajeshdonepudi.screenrecorder.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String TAG = this.getClass().getSimpleName();
    private static final int PERMISSION_CODE = 1;
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private static int DISPLAY_WIDTH;
    private static int DISPLAY_HEIGHT;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private ToggleButton mToggleButton;
    private MediaRecorder mMediaRecorder;
    private TextView textView;
    private String filePath;

    private int hasPermissionStorage;
    private int hasPermissionAudio;
    private static final int REQUEST_CODE_SOME_FEATURES_PERMISSIONS = 20;
    private List<String> permissions = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            hasPermissionStorage = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            hasPermissionAudio = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO);

            if (hasPermissionAudio != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }

            if (hasPermissionStorage != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }

            if (!permissions.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissions.toArray(new String[permissions.size()]), REQUEST_CODE_SOME_FEATURES_PERMISSIONS);
            }
        }


        textView = findViewById(R.id.textView);
        //textView.setText("L");

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        DISPLAY_WIDTH = size.x;
        DISPLAY_HEIGHT = size.y;

        mMediaRecorder = new MediaRecorder();
        //initRecorder();
        //prepareRecorder();

        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        mToggleButton = findViewById(R.id.toggle);
        mToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    shareScreen();
                    TextView textview2 = findViewById(R.id.textView2);
                    textview2.setText("On");
                    //   mToggleButton.setText("Off");
                } else {
                    // The toggle is disabled
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    Log.v(TAG, "Recording Stopped");
                    stopScreenSharing();
                    TextView textview2 = findViewById(R.id.textView2);
                    textview2.setText("Off");
                    Toast.makeText(getApplicationContext(), "Video Saved Successfully ", Toast.LENGTH_LONG).show();

                }
            }
        });

        mMediaProjectionCallback = new MediaProjectionCallback();


    }

    @Override
    public void onDestroy() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this, R.string.screen_cast_permission_denied_error, Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            mMediaRecorder.reset();
            textView.setText("");
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    private void shareScreen() {
        initRecorder();
        prepareRecorder();
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), PERMISSION_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        //mMediaRecorder.release();
        textView.setText("");
        addVideoToGallery();
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay(TAG,
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null /*Handler*/);
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (mToggleButton.isChecked()) {
                mToggleButton.setChecked(false);
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                Log.v(TAG, "Recording Stopped");
                initRecorder();
                prepareRecorder();
            }
            mMediaProjection = null;
            stopScreenSharing();
            Log.i(TAG, "MediaProjection Stopped");
        }
    }

    private void prepareRecorder() {
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
            finish();
        }
    }

    private void initRecorder() {

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String path = sp.getString("path", Environment.getExternalStorageDirectory().getPath());
        String format = sp.getString("prefOutput", "MPEG_4");
        String bitrate = sp.getString("prefBitrate", "260");
        String frameRate = sp.getString("prefFrameRate", "4");
        String audio = sp.getString("prefAudio", "AAC");
        String ext;

        textView.setText(String.format(getString(R.string.current_storage_path_text), path));

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        switch (format) {
            case "AAC_ADTS":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
                ext = ".m4a";
                break;
            case "AMR_NB":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
                ext = ".amr";
                break;
            case "AMR_WB":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
                ext = ".awb";
                break;
            case "MPEG_4":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                ext = ".mp4";
                break;
            case "THREE_GPP":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                ext = ".3gp";
                break;
            case "WEBM":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.WEBM);
                ext = ".webm";
                break;
            default:
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                ext = ".mp4";
        }

        switch (audio) {
            case "AAC":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                break;
            case "AAC_ELD":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC_ELD);
                break;
            case "AMR_NB":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                break;
            case "AMR_WB":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
                break;
            case "HE_AAC":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
                break;
            case "VORBIS":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.VORBIS);
                break;
            default:
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        }

        Calendar c = Calendar.getInstance();
        String mName = c.get(Calendar.DATE)
                + "_" + c.get(Calendar.MONTH)
                + "_" + c.get(Calendar.YEAR)
                + "_" + c.get(Calendar.HOUR_OF_DAY)
                + "_" + c.get(Calendar.MINUTE)
                + "_" + c.get(Calendar.SECOND)
                + ext;

        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(Integer.parseInt(bitrate) * 1000); //low260 high800
        mMediaRecorder.setVideoFrameRate(Integer.parseInt(frameRate));
        mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
        mMediaRecorder.setOutputFile(path + "/" + mName);
        filePath = path + "/" + mName;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {

            case R.id.action_settings:
                Intent i = new Intent(this, UserSettings.class);
                startActivity(i);
                return true;
            case R.id.action_about:
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.about_dialog_title))
                        .setMessage(getString(R.string.about_dialog_message))
                        .setPositiveButton(getString(R.string.about_dialog_positive_button_text),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        //continue
                                    }
                                })
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_SOME_FEATURES_PERMISSIONS: {
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d("Permissions", "Permission Granted: " + permissions[i]);
                    } else if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d("Permissions", "Permission Denied: " + permissions[i]);
                        finish();
                    }
                }
            }
            break;
            default: {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    private void addVideoToGallery() {
        Intent mediaScanIntent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
        File f = new File(filePath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }
}
