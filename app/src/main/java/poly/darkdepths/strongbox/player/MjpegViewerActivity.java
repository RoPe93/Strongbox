package poly.darkdepths.strongbox.player;

/**
 * This class is largely based on the IOCipher example (https://github.com/n8fr8/IOCipherCameraExample).
 * @author Nathan of the Guardian Project
 */

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.VirtualFileSystem;
import poly.darkdepths.strongbox.Globals;
import poly.darkdepths.strongbox.Security;
import poly.darkdepths.strongbox.encoders.AACHelper;
import poly.darkdepths.strongbox.encoders.MediaConstants;

public class MjpegViewerActivity extends Activity {
    private static final String TAG = "MjpegActivity";

    private MjpegView mv;
    private AudioTrack at;

    InputStream isAudio = null;
    AACHelper aac = null;
    boolean useAAC = false;

    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Starting MjpegViewerActivity");
        super.onCreate(savedInstanceState);

        Globals appState = (Globals) getApplicationContext();
        VirtualFileSystem vfs = appState.getVFS();

        if(vfs.isMounted())
            Log.d("VFS", "IOCipher container mounted");
        else {
            Log.d("VFS", "IOCipher not mounted. Mounting...");
            Security securestore = appState.getSecurestore();
            vfs.mount(appState.getDbFile(), securestore.getKey().getEncoded());
        }


        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //prevent screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        mv = new MjpegView(this);
        setContentView(mv);

        final String ioCipherVideoPath = getIntent().getExtras().getString("video");
        final String ioCipherAudioPath = getIntent().getExtras().getString("audio");

        try {

            mv.setDisplayMode(MjpegView.SIZE_BEST_FIT);
            mv.setFrameDelay(5);

            File fileAudio = null;

            if (ioCipherAudioPath == null)
            {
                fileAudio = new File(ioCipherVideoPath + ".pcm");

                if (!fileAudio.exists())
                {
                    fileAudio = new File(ioCipherVideoPath + ".aac");
                    useAAC = true;
                }
            }
            else
            {
                fileAudio = new File(ioCipherAudioPath);

                if (getIntent().getExtras().containsKey("useAAC"))
                    useAAC = getIntent().getExtras().getBoolean("useAAC",false);
                else if (ioCipherAudioPath.endsWith(".aac"))
                    useAAC = true;
            }

            if (fileAudio.exists())
            {
                initAudio(fileAudio.getAbsolutePath());
                new Thread ()
                {
                    public void run ()
                    {
                        try {
                            playAudio();
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
            mv.setSource(new MjpegInputStream(new FileInputStream(ioCipherVideoPath)));



        }
        catch (Exception e) {

            Log.e(TAG,"an error occcured init'g video playback",e);
        }
    }

    public void initAudio(String vfsPath) throws Exception {

        isAudio = new BufferedInputStream(new FileInputStream(vfsPath));

        if (useAAC)
        {
            aac = new AACHelper();
            aac.setDecoder(MediaConstants.sAudioSampleRate, MediaConstants.sAudioChannels, MediaConstants.sAudioBitRate);
        }
        else
        {

            int minBufferSize = AudioTrack.getMinBufferSize(MediaConstants.sAudioSampleRate,
                    MediaConstants.sChannelConfigOut, AudioFormat.ENCODING_PCM_16BIT)*8;

            at = new AudioTrack(AudioManager.STREAM_MUSIC, MediaConstants.sAudioSampleRate,
                    MediaConstants.sChannelConfigOut, AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize, AudioTrack.MODE_STREAM);
        }

    }

    public void playAudio () throws IOException
    {
        if (useAAC)
            aac.startPlaying(isAudio);
        else
        {
            try{
                byte[] music = null;
                music = new byte[512];
                at.play();

                int i = 0;
                while((i = isAudio.read(music)) != -1)
                    at.write(music, 0, i);

            } catch (IOException e) {
                e.printStackTrace();
            }

            at.stop();
            at.release();
            isAudio.close();
            at = null;
        }
    }

    public void onPause() {
        super.onPause();
        mv.stopPlayback();

        if (at!=null)
            at.stop();

        timeout();
    }


    private void timeout() {
        final Globals appState = (Globals) getApplicationContext();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isAppOnForeground(getApplicationContext())) {
                    Log.d(TAG, "Timeout reached. Closing down application.");
                    appState.getSecurestore().destroyKey();
                    finish();
                }
            }
        }, appState.getTimeout());
    }

    private boolean isAppOnForeground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        final String packageName = context.getPackageName();
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
