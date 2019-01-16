/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.


Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.VideoPlayback2.app.VideoPlayback;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.HINT;
import com.vuforia.ImageTarget;
import com.vuforia.ObjectTracker;
import com.vuforia.Rectangle;
import com.vuforia.State;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.VirtualButton;
import com.vuforia.Vuforia;
import com.vuforia.samples.SampleApplication.SampleApplicationControl;
import com.vuforia.samples.SampleApplication.SampleApplicationException;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.samples.SampleApplication.utils.SampleApplicationGLView;
import com.vuforia.samples.SampleApplication.utils.Texture;
import com.vuforia.samples.VideoPlayback2.R;
import com.vuforia.samples.VideoPlayback2.app.VideoPlayback.VideoPlayerHelper.MEDIA_STATE;
import com.vuforia.samples.VideoPlayback2.ui.SampleAppMenu.SampleAppMenu;
import com.vuforia.samples.VideoPlayback2.ui.SampleAppMenu.SampleAppMenuGroup;
import com.vuforia.samples.VideoPlayback2.ui.SampleAppMenu.SampleAppMenuInterface;


// The AR activity for the VideoPlayback sample.
public class VideoPlayback extends Activity implements SampleApplicationControl, SampleAppMenuInterface {
    private static final String LOGTAG = "VideoPlayback";

    SampleApplicationSession vuforiaAppSession;

    Activity mActivity;

    // Helpers to detect events such as double tapping:
    private GestureDetector mGestureDetector = null;
    private SimpleOnGestureListener mSimpleListener = null;

    // Movie for the Targets:
    public static final int NUM_TARGETS = 2;
    public static final int BUSINESS = 0;
    public static final int HOPE = 1;
    private VideoPlayerHelper mVideoPlayerHelper[] = null;
    private int mSeekPosition[] = null;
    private boolean mWasPlaying[] = null;
    private String mMovieName;

    // A boolean to indicate whether we come from full screen:
    private boolean mReturningFromFullScreen = false;

    // Our OpenGL view:
    private SampleApplicationGLView mGlView;

    // Our renderer:
    private VideoPlaybackRenderer mRenderer;

    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    DataSet dataSet = null;

    private RelativeLayout mUILayout;

    private boolean mPlayFullscreenVideo = false;

    private SampleAppMenu mSampleAppMenu;

    private LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);

    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;

    boolean mIsInitialized = false;

    // Virtual Button

    // Virtual Button runtime creation:
    private boolean updateBtns = false;
    public String virtualButtonColors[] = { "play"}; // "blue", "yellow", "green"

    // Enumeration for masking button indices into single integer:
    private static final int BUTTON_1 = 1;
    //private static final int BUTTON_2 = 2;
    //private static final int BUTTON_3 = 4;
    //private static final int BUTTON_4 = 8;

    private byte buttonMask = 0;
    static final int NUM_BUTTONS = 1;


    boolean mIsDroidDevice = false;


    private View _viewCard;
   // private TextView _textType;
    private TextView _textValue;
    private ImageView _instanceImageView;


    // Called when the activity first starts or the user navigates back
    // to an activity.
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);

        vuforiaAppSession = new SampleApplicationSession(this);

        mActivity = this;

        startLoadingAnimation();

        vuforiaAppSession.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Load any sample specific textures:
        mTextures = new Vector<Texture>();
        loadTextures();

        // Create the gesture detector that will handle the single and
        // double taps:
        mSimpleListener = new SimpleOnGestureListener();
        mGestureDetector = new GestureDetector(getApplicationContext(), mSimpleListener);

        mVideoPlayerHelper = new VideoPlayerHelper[NUM_TARGETS];
        mSeekPosition = new int[NUM_TARGETS];
        mWasPlaying = new boolean[NUM_TARGETS];
        //mMovieName = new String();//[NUM_TARGETS];

        // Create the video player helper that handles the playback of the movie
        // for the targets:
        for (int i = 0; i < NUM_TARGETS; i++) {
            mVideoPlayerHelper[i] = new VideoPlayerHelper();
            mVideoPlayerHelper[i].init();
            mVideoPlayerHelper[i].setActivity(this);
        }

        //XXX
        mMovieName = "VideoPlayback/andeer.mp4"; //andeer andeerHD [BUSINESS] regione cellular
        //mMovieName[HOPE] = "VideoPlayback/andeer.mp4";

        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");


        LayoutParams layoutParamsControl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        LayoutInflater inflater = getLayoutInflater();
        _viewCard = inflater.inflate(R.layout.card, null);
        _viewCard.setVisibility(View.INVISIBLE);
        LinearLayout cardLayout = (LinearLayout) _viewCard.findViewById(R.id.card_layout);

        /*cardLayout.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    hideCard();
                    return true;
                }
                return false;
            }
        });*/
        addContentView(_viewCard, layoutParamsControl);

        //_textType = (TextView) _viewCard.findViewById(R.id.text_type);
        _textValue = (TextView) _viewCard.findViewById(R.id.text_value);
        _instanceImageView = (ImageView) _viewCard.findViewById(R.id.instance_image);

        // Set the double tap listener:
        mGestureDetector.setOnDoubleTapListener(new OnDoubleTapListener() {
            public boolean onDoubleTap(MotionEvent e) {
                // We do not react to this event
                return false;
            }

            public boolean onDoubleTapEvent(MotionEvent e) {
                // We do not react to this event
                return false;
            }

            // Handle the single tap
            public boolean onSingleTapConfirmed(MotionEvent e) {
                boolean isSingleTapHandled = false;
                // Do not react if the StartupScreen is being displayed
                for (int i = 0; i < NUM_TARGETS; i++) {
                    // Verify that the tap happened inside the target
                    if (mRenderer != null && mRenderer.isTapOnScreenInsideTarget(i, e.getX(), e.getY())) {

                        // Check if it is playable on texture
                        if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                            // We can play only if the movie was paused, ready
                            // or stopped
                            if ((mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PAUSED)
                                    || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.READY)
                                    || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.STOPPED)
                                    || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.REACHED_END)) {
                                // Pause all other media
                                pauseAll(i);

                                // If it has reached the end then rewind
                                if ((mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.REACHED_END))
                                    mSeekPosition[i] = 0;

                                mVideoPlayerHelper[i].play(mPlayFullscreenVideo, mSeekPosition[i]);
                                mSeekPosition[i] = VideoPlayerHelper.CURRENT_POSITION;
                            } else if (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PLAYING) {
                                // If it is playing then we pause it
                                mVideoPlayerHelper[i].pause();
                            }
                        } else if (mVideoPlayerHelper[i].isPlayableFullscreen()) {
                            // If it isn't playable on texture
                            // Either because it wasn't requested or because it
                            // isn't supported then request playback fullscreen.
                            mVideoPlayerHelper[i].play(true, VideoPlayerHelper.CURRENT_POSITION);
                        }

                        isSingleTapHandled = true;

                        // Even though multiple videos can be loaded only one
                        // can be playing at any point in time. This break
                        // prevents that, say, overlapping videos trigger
                        // simultaneously playback.
                        break;
                    }
                }

                return isSingleTapHandled;
            }
        });
    }


    public void playVideo(int i) {
        // Do not react if the StartupScreen is being displayed
        if (mRenderer != null) {
            // Check if it is playable on texture
            if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                // We can play only if the movie was paused, ready
                // or stopped
                if ((mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PAUSED)
                        || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.READY)
                        || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.STOPPED)
                        || (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.REACHED_END)) {
                    // Pause all other media
                    pauseAll(i);

                    // If it has reached the end then rewind
                    if ((mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.REACHED_END))
                        mSeekPosition[i] = 0;

                    mVideoPlayerHelper[i].play(mPlayFullscreenVideo, mSeekPosition[i]);
                    mSeekPosition[i] = VideoPlayerHelper.CURRENT_POSITION;
                } else if (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PLAYING) {
                    // If it is playing then we pause it
                    mVideoPlayerHelper[i].pause();
                }
            } else if (mVideoPlayerHelper[i].isPlayableFullscreen()) {
                // If it isn't playable on texture
                // Either because it wasn't requested or because it
                // isn't supported then request playback fullscreen.
                mVideoPlayerHelper[i].play(true, VideoPlayerHelper.CURRENT_POSITION);
            }
        }
    }

    // We want to load specific textures from the APK, which we will later
    // use for rendering.
    private void loadTextures() {
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/VuforiaSizzleReel_1.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/VuforiaSizzleReel_2.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/play.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/busy.png", getAssets()));
        mTextures.add(Texture.loadTextureFromApk("VideoPlayback/error.png", getAssets()));
    }

    // Called when the activity will start interacting with the user.
    protected void onResume() {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        try {
            vuforiaAppSession.resumeAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }

        // Resume the GL view:
        if (mGlView != null) {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }

        // Reload all the movies
        if (mRenderer != null) {
            for (int i = 0; i < NUM_TARGETS; i++) {
                if (!mReturningFromFullScreen) {
                    mRenderer.requestLoad(i, mMovieName, mSeekPosition[i], false);
                } else {
                    mRenderer.requestLoad(i, mMovieName, mSeekPosition[i], mWasPlaying[i]);
                }
            }
        }

        mReturningFromFullScreen = false;
    }


    // Called when returning from the full screen player
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {

            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            if (resultCode == RESULT_OK) {
                // The following values are used to indicate the position in
                // which the video was being played and whether it was being
                // played or not:
                String movieBeingPlayed = data.getStringExtra("movieName");
                mReturningFromFullScreen = true;

                // Find the movie that was being played full screen
                for (int i = 0; i < NUM_TARGETS; i++) {
                    if (movieBeingPlayed.compareTo(mMovieName) == 0) {
                        mSeekPosition[i] = data.getIntExtra("currentSeekPosition", 0);
                        mWasPlaying[i] = false;
                    }
                }
            }
        }
    }


    public void onConfigurationChanged(Configuration config) {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);

        vuforiaAppSession.onConfigurationChanged();
    }


    // Called when the system is about to start resuming a previous activity.
    protected void onPause() {
        Log.d(LOGTAG, "onPause");
        super.onPause();

        if (mGlView != null) {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        // Store the playback state of the movies and unload them:
        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is paused we need to store the position in which
            // this was currently playing:
            if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                mSeekPosition[i] = mVideoPlayerHelper[i].getCurrentPosition();
                mWasPlaying[i] = mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PLAYING ? true : false;
            }

            // We also need to release the resources used by the helper, though
            // we don't need to destroy it:
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].unload();
        }

        mReturningFromFullScreen = false;

        try {
            vuforiaAppSession.pauseAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }
    }


    // The final call you receive before your activity is destroyed.
    protected void onDestroy() {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();

        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is destroyed we need to release all resources:
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].deinit();
            mVideoPlayerHelper[i] = null;
        }

        try {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }

        // Unload texture:
        mTextures.clear();
        mTextures = null;

        System.gc();
    }


    // Pause all movies except one
    // if the value of 'except' is -1 then
    // do a blanket pause
    private void pauseAll(int except) {
        // And pause all the playing videos:
        for (int i = 0; i < NUM_TARGETS; i++) {
            // We can make one exception to the pause all calls:
            if (i != except) {
                // Check if the video is playable on texture
                if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                    // If it is playing then we pause it
                    mVideoPlayerHelper[i].pause();
                }
            }
        }
    }


    // Do not exit immediately and instead show the startup screen
    public void onBackPressed() {
        pauseAll(-1);
        super.onBackPressed();
    }


    private void startLoadingAnimation() {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay, null);

        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout.findViewById(R.id.loading_indicator);

        // Shows the loading indicator at start
        loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        // Adds the inflated layout to the view
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }


    // Initializes AR application components.
    private void initApplicationAR() {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new VideoPlaybackRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);

        // The renderer comes has the OpenGL context, thus, loading to texture
        // must happen when the surface has been created. This means that we
        // can't load the movie from this thread (GUI) but instead we must
        // tell the GL thread to load it once the surface has been created.
        for (int i = 0; i < NUM_TARGETS; i++) {
            mRenderer.setVideoPlayerHelper(i, mVideoPlayerHelper[i]);
            mRenderer.requestLoad(i, mMovieName, 0, false);
        }

        mGlView.setRenderer(mRenderer);

        for (int i = 0; i < NUM_TARGETS; i++) {
            float[] temp = {0f, 0f, 0f};
            mRenderer.targetPositiveDimensions[i].setData(temp);
            mRenderer.videoPlaybackTextureID[i] = -1;
        }

    }


    // We do not handle the touch event here, we just forward it to the
    // gesture detector
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = false;
        if (mSampleAppMenu != null)
            result = mSampleAppMenu.processEvent(event);

        // Process the Gestures
        if (!result)
            mGestureDetector.onTouchEvent(event);

        return result;
    }


    @Override
    public boolean doInitTrackers() {
        // Indicate if the trackers were initialized correctly
        boolean result = true;

        // Initialize the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        Tracker tracker = trackerManager.initTracker(ObjectTracker.getClassType());

        if (tracker == null) {
            Log.d(LOGTAG, "Failed to initialize ObjectTracker.");
            result = false;
        } else {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }

        return result;
    }


    @Override
    public boolean doLoadTrackersData() {


        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker == null) {
            Log.d(LOGTAG, "Failed to load tracking data set because the ObjectTracker has not been initialized.");
            return false;
        }

        // Create the data sets:
        dataSet = objectTracker.createDataSet();
        if (dataSet == null) {
            Log.d(LOGTAG, "Failed to create a new tracking data.");
            return false;
        }

        // Load the data sets:
        //XXX
        if (!dataSet.load("VirtualButton.xml", STORAGE_TYPE.STORAGE_APPRESOURCE)) { //VirtualButton Regione CellularLineVideo
            Log.d(LOGTAG, "Failed to load data set.");
            return false;
        }

        // Activate the data set:
        if (!objectTracker.activateDataSet(dataSet)) {
            Log.d(LOGTAG, "Failed to activate data set.");
            return false;
        }

        Log.d(LOGTAG, "Successfully loaded and activated data set.");
        return true;
    }


    @Override
    public boolean doStartTrackers() {
        // Indicate if the trackers were started correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());
        if (objectTracker != null) {
            objectTracker.start();
            Vuforia.setHint(HINT.HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 2);
        } else
            result = false;

        return result;
    }


    @Override
    public boolean doStopTrackers() {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.stop();
        else
            result = false;

        return result;
    }


    @Override
    public boolean doUnloadTrackersData() {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;

        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker == null) {
            Log.d(LOGTAG, "Failed to destroy the tracking data set because the ObjectTracker has not been initialized.");
            return false;
        }

        if (dataSet != null) {
            if (objectTracker.getActiveDataSet() == dataSet && !objectTracker.deactivateDataSet(dataSet)) {
                Log.d(LOGTAG, "Failed to destroy the tracking data set StonesAndChips because the data set could not be deactivated.");
                result = false;
            } else if (!objectTracker.destroyDataSet(dataSet)) {
                Log.d(LOGTAG, "Failed to destroy the tracking data set StonesAndChips.");
                result = false;
            }

            dataSet = null;
        }

        return result;
    }


    @Override
    public boolean doDeinitTrackers() {
        // Indicate if the trackers were deinitialized correctly
        boolean result = true;

        // Deinit the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        trackerManager.deinitTracker(ObjectTracker.getClassType());

        return result;
    }


    @Override
    public void onInitARDone(SampleApplicationException exception) {

        if (exception == null) {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            // Hides the Loading Dialog
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);

            // Sets the layout background to transparent
            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            try {
                vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            } catch (SampleApplicationException e) {
                Log.e(LOGTAG, e.getString());
            }

            boolean result = CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

            if (!result)
                Log.e(LOGTAG, "Unable to enable continuous autofocus");

            mSampleAppMenu = new SampleAppMenu(this, this, "Video Playback", mGlView, mUILayout, null);
            setSampleAppMenuSettings();

            mIsInitialized = true;

        } else {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }

    }


    // Shows initialization error messages as System dialogs
    public void showInitializationErrorMessage(String message) {
        final String errorMessage = message;
        runOnUiThread(new Runnable() {
            public void run() {
                if (mErrorDialog != null) {
                    mErrorDialog.dismiss();
                }

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        VideoPlayback.this);
                builder
                        .setMessage(errorMessage)
                        .setTitle(getString(R.string.INIT_ERROR))
                        .setCancelable(false)
                        .setIcon(0)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        finish();
                                    }
                                });

                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }


    // @Override
    // public void onVuforiaUpdate(State state) {
    //}

    @Override
    public void onVuforiaUpdate(State state)
    {
        if (updateBtns)
        {
            // Update runs in the tracking thread therefore it is guaranteed
            // that the tracker is
            // not doing anything at this point. => Reconfiguration is possible.

            ObjectTracker ot = (ObjectTracker) (TrackerManager.getInstance().getTracker(ObjectTracker.getClassType()));
            assert (dataSet != null);

            // Deactivate the data set prior to reconfiguration:
            ot.deactivateDataSet(dataSet);

            assert (dataSet.getNumTrackables() > 0);
            Trackable trackable = dataSet.getTrackable(0);

            assert (trackable != null);
            assert (trackable.getType() == ObjectTracker.getClassType());
            ImageTarget imageTarget = (ImageTarget) (trackable);

            if ((buttonMask & BUTTON_1) != 0)
            {
                Log.d(LOGTAG, "Toggle Button 1");

                //XXX
                //toggleVirtualButton(imageTarget, virtualButtonColors[0], -108.68f, -53.52f, -75.75f, -65.87f);
                //toggleVirtualButton(imageTarget, virtualButtonColors[0], -33.33f, -4.65f, -16.67f, -16.65f);
                //toggleVirtualButton(imageTarget, virtualButtonColors[0], -40.0f, 40.0f, 40.0f, -40.0f);

            }
            /*if ((buttonMask & BUTTON_2) != 0)
            {
                Log.d(LOGTAG, "Toggle Button 2");

                toggleVirtualButton(imageTarget, virtualButtonColors[1],
                    -45.28f, -53.52f, -12.35f, -65.87f);
            }
            if ((buttonMask & BUTTON_3) != 0)
            {
                Log.d(LOGTAG, "Toggle Button 3");

                toggleVirtualButton(imageTarget, virtualButtonColors[2],
                    14.82f, -53.52f, 47.75f, -65.87f);
            }
            if ((buttonMask & BUTTON_4) != 0)
            {
                Log.d(LOGTAG, "Toggle Button 4");

                toggleVirtualButton(imageTarget, virtualButtonColors[3],
                    76.57f, -53.52f, 109.50f, -65.87f);
            }*/

            // Reactivate the data set:
            ot.activateDataSet(dataSet);

            buttonMask = 0;
            updateBtns = false;
        }
    }


    final private static int CMD_BACK = -1;
    final private static int CMD_FULLSCREEN_VIDEO = 1;
    final public static int CMD_BUTTON_RED = 2;

    // This method sets the menu's settings
    private void setSampleAppMenuSettings() {
        SampleAppMenuGroup group;

        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);

        group = mSampleAppMenu.addGroup("", true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            group.addSelectionItem(getString(R.string.menu_playFullscreenVideo), CMD_FULLSCREEN_VIDEO, mPlayFullscreenVideo);
        }

        mSampleAppMenu.attachMenu();
    }


    @Override
    public boolean menuProcess(int command) {

        boolean result = true;

        switch (command) {
            case CMD_BACK:
                finish();
                break;

            case CMD_FULLSCREEN_VIDEO:
                mPlayFullscreenVideo = !mPlayFullscreenVideo;

                for (int i = 0; i < mVideoPlayerHelper.length; i++) {
                    if (mVideoPlayerHelper[i].getStatus() == MEDIA_STATE.PLAYING) {
                        // If it is playing then we pause it
                        mVideoPlayerHelper[i].pause();

                        mVideoPlayerHelper[i].play(true,
                                mSeekPosition[i]);
                    }
                }
                break;
            case CMD_BUTTON_RED:
                addButtonToToggle(0);
                break;

        }

        return result;
    }

    // Create/destroy a Virtual Button at runtime
    //
    // Note: This will NOT work if the tracker is active!
    boolean toggleVirtualButton(ImageTarget imageTarget, String name, float left, float top, float right, float bottom)
    {
        Log.d(LOGTAG, "toggleVirtualButton");

        boolean buttonToggleSuccess = false;

        VirtualButton virtualButton = imageTarget.getVirtualButton(name);
        if (virtualButton != null)
        {
            Log.d(LOGTAG, "Destroying Virtual Button> " + name);
            buttonToggleSuccess = imageTarget.destroyVirtualButton(virtualButton);
        } else
        {
            Log.d(LOGTAG, "Creating Virtual Button> " + name);
            Rectangle vbRectangle = new Rectangle(left, top, right, bottom);
            VirtualButton virtualButton2 = imageTarget.createVirtualButton(name, vbRectangle);

            if (virtualButton2 != null)
            {
                // This is just a showcase. The values used here a set by
                // default on Virtual Button creation
                virtualButton2.setEnabled(true);
                virtualButton2.setSensitivity(VirtualButton.SENSITIVITY.HIGH);
                buttonToggleSuccess = true;
            }
        }

        return buttonToggleSuccess;
    }


    private void addButtonToToggle(int virtualButtonIdx)
    {
        Log.d(LOGTAG, "addButtonToToggle");

        assert (virtualButtonIdx >= 0 && virtualButtonIdx < NUM_BUTTONS);

        switch (virtualButtonIdx)
        {
            case 0:
                buttonMask |= BUTTON_1;
                break;

            /*case 1:
                buttonMask |= BUTTON_2;
                break;

            case 2:
                buttonMask |= BUTTON_3;
                break;

            case 3:
                buttonMask |= BUTTON_4;
                break;*/
        }
        updateBtns = true;
    }

    void showCard(final String type, final Spanned value, final Bitmap bitmap) {
        final Context context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // if scard is already visible with same VuMark, do nothing
                if ((_viewCard.getVisibility() == View.VISIBLE) && (_textValue.getText().equals(value))) {
                    return;
                }
                Animation bottomUp = AnimationUtils.loadAnimation(context, R.anim.bottom_up);

                //_textType.setText(type);

                _textValue.setMovementMethod(LinkMovementMethod.getInstance());
                _textValue.setText(value);

                if (bitmap != null) {
                    _instanceImageView.setImageBitmap(bitmap);
                }

                _viewCard.bringToFront();
                _viewCard.setVisibility(View.VISIBLE);
                _viewCard.startAnimation(bottomUp);
                // mUILayout.invalidate();
            }
        });
    }

    void hideCard() {
        final Context context = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // if card not visible, do nothing
                if (_viewCard.getVisibility() != View.VISIBLE) {
                    return;
                }
                //_textType.setText("");
                _textValue.setText("");
                Animation bottomDown = AnimationUtils.loadAnimation(context, R.anim.bottom_down);

                _viewCard.startAnimation(bottomDown);
                _viewCard.setVisibility(View.INVISIBLE);
                // mUILayout.invalidate();
            }
        });
    }

    public Bitmap getBitmapFromAsset(String filePath) {
        final Context context = this;
        AssetManager assetManager = context.getAssets();

        InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            // handle exception
        }

        return bitmap;
    }
}
