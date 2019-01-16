/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.


Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <Vuforia/Vuforia.h>
#include <Vuforia/CameraDevice.h>
#include <Vuforia/Renderer.h>
#include <Vuforia/VideoBackgroundConfig.h>
#include <Vuforia/Trackable.h>
#include <Vuforia/TrackableResult.h>
#include <Vuforia/Tool.h>
#include <Vuforia/Tracker.h>
#include <Vuforia/TrackerManager.h>
#include <Vuforia/ObjectTracker.h>
#include <Vuforia/CameraCalibration.h>
#include <Vuforia/UpdateCallback.h>
#include <Vuforia/DataSet.h>


#include "SampleUtils.h"
#include "Texture.h"
#include "CubeShaders.h"
#include "Teapot.h"
#include "Yello.h"
#include "Buildings.h"

#ifdef __cplusplus
extern "C"
{
#endif

// Textures:
int textureCount                = 0;
Texture** textures              = 0;

unsigned int shaderProgramID    = 0;
GLint vertexHandle              = 0;
GLint textureCoordHandle        = 0;
GLint mvpMatrixHandle           = 0;
GLint texSampler2DHandle        = 0;

// Screen dimensions:
int screenWidth                 = 0;
int screenHeight                = 0;

// Indicates whether screen is in portrait (true) or landscape (false) mode
bool isActivityInPortraitMode   = false;

// The projection matrix used for rendering virtual objects:
Vuforia::Matrix44F projectionMatrix;

// The viewport used for rendering virtual objects
GLfloat viewport[4];

// Constants:
static const float kObjectScale          = 3.f;
static const float kBuildingsObjectScale = 12.f;

Vuforia::DataSet* dataSetStonesAndChips  = 0;
Vuforia::DataSet* dataSetTarmac          = 0;

bool switchDataSetAsap           = false;
bool isExtendedTrackingActivated = false;

Vuforia::CameraDevice::CAMERA_DIRECTION currentCamera;

const int STONES_AND_CHIPS_DATASET_ID = 0;
const int TARMAC_DATASET_ID = 1;
int selectedDataset = STONES_AND_CHIPS_DATASET_ID;

long rotation = 0;

// Object to receive update callbacks from Vuforia SDK
class ImageTargets_UpdateCallback : public Vuforia::UpdateCallback
{   
    virtual void Vuforia_onUpdate(Vuforia::State& /*state*/)
    {
        if (switchDataSetAsap)
        {
            switchDataSetAsap = false;

            // Get the object tracker:
            Vuforia::TrackerManager& trackerManager = Vuforia::TrackerManager::getInstance();
            Vuforia::ObjectTracker* objectTracker = static_cast<Vuforia::ObjectTracker*>(
                trackerManager.getTracker(Vuforia::ObjectTracker::getClassType()));
            if (objectTracker == 0 || dataSetStonesAndChips == 0 || dataSetTarmac == 0 ||
                objectTracker->getActiveDataSet() == 0)
            {
                LOG("Failed to switch data set.");
                return;
            }
            
            switch( selectedDataset )
            {
                case STONES_AND_CHIPS_DATASET_ID:
                    if (objectTracker->getActiveDataSet() != dataSetStonesAndChips)
                    {
                        objectTracker->deactivateDataSet(dataSetTarmac);
                        objectTracker->activateDataSet(dataSetStonesAndChips);
                    }
                    break;
                    
                case TARMAC_DATASET_ID:
                    if (objectTracker->getActiveDataSet() != dataSetTarmac)
                    {
                        objectTracker->deactivateDataSet(dataSetStonesAndChips);
                        objectTracker->activateDataSet(dataSetTarmac);
                    }
                    break;
            }

            if(isExtendedTrackingActivated)
            {
                Vuforia::DataSet* currentDataSet = objectTracker->getActiveDataSet();
                for (int tIdx = 0; tIdx < currentDataSet->getNumTrackables(); tIdx++)
                {
                    Vuforia::Trackable* trackable = currentDataSet->getTrackable(tIdx);
                    trackable->startExtendedTracking();
                }
            }

        }
    }
};

ImageTargets_UpdateCallback updateCallback;


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_setActivityPortraitMode(JNIEnv *, jobject, jboolean isPortrait)
{
    isActivityInPortraitMode = isPortrait;
}



JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_switchDatasetAsap(JNIEnv *, jobject, jint datasetId)
{
    selectedDataset = datasetId;
    switchDataSetAsap = true;
}


JNIEXPORT int JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_initTracker(JNIEnv *, jobject)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_initTracker");
    
    // Initialize the object tracker:
    Vuforia::TrackerManager& trackerManager = Vuforia::TrackerManager::getInstance();
    Vuforia::Tracker* tracker = trackerManager.initTracker(Vuforia::ObjectTracker::getClassType());
    if (tracker == NULL)
    {
        LOG("Failed to initialize ObjectTracker.");
        return 0;
    }

    LOG("Successfully initialized ObjectTracker.");
    return 1;
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_deinitTracker(JNIEnv *, jobject)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_deinitTracker");

    // Deinit the object tracker:
    Vuforia::TrackerManager& trackerManager = Vuforia::TrackerManager::getInstance();
    trackerManager.deinitTracker(Vuforia::ObjectTracker::getClassType());
}


JNIEXPORT int JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_loadTrackerData(JNIEnv *, jobject)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_loadTrackerData");
    
    // Get the object tracker:
    Vuforia::TrackerManager& trackerManager = Vuforia::TrackerManager::getInstance();
    Vuforia::ObjectTracker* objectTracker = static_cast<Vuforia::ObjectTracker*>(
                    trackerManager.getTracker(Vuforia::ObjectTracker::getClassType()));
    if (objectTracker == NULL)
    {
        LOG("Failed to load tracking data set because the ObjectTracker has not"
            " been initialized.");
        return 0;
    }

    // Create the data sets:
    dataSetStonesAndChips = objectTracker->createDataSet();
    if (dataSetStonesAndChips == 0)
    {
        LOG("Failed to create a new tracking data.");
        return 0;
    }

    dataSetTarmac = objectTracker->createDataSet();
    if (dataSetTarmac == 0)
    {
        LOG("Failed to create a new tracking data.");
        return 0;
    }

    // Load the data sets:
    if (!dataSetStonesAndChips->load("StonesAndChips.xml", Vuforia::STORAGE_APPRESOURCE))
    {
        LOG("Failed to load data set.");
        return 0;
    }

    if (!dataSetTarmac->load("Tarmac.xml", Vuforia::STORAGE_APPRESOURCE))
    {
        LOG("Failed to load data set.");
        return 0;
    }

    // Activate the data set:
    if (!objectTracker->activateDataSet(dataSetStonesAndChips))
    {
        LOG("Failed to activate data set.");
        return 0;
    }

    LOG("Successfully loaded and activated data set.");
    return 1;
}


JNIEXPORT int JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_destroyTrackerData(JNIEnv *, jobject)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_destroyTrackerData");

    // Get the object tracker:
    Vuforia::TrackerManager& trackerManager = Vuforia::TrackerManager::getInstance();
    Vuforia::ObjectTracker* objectTracker = static_cast<Vuforia::ObjectTracker*>(
        trackerManager.getTracker(Vuforia::ObjectTracker::getClassType()));
    if (objectTracker == NULL)
    {
        LOG("Failed to destroy the tracking data set because the ObjectTracker has not"
            " been initialized.");
        return 0;
    }
    
    if (dataSetStonesAndChips != 0)
    {
        if (objectTracker->getActiveDataSet() == dataSetStonesAndChips &&
            !objectTracker->deactivateDataSet(dataSetStonesAndChips))
        {
            LOG("Failed to destroy the tracking data set StonesAndChips because the data set "
                "could not be deactivated.");
            return 0;
        }

        if (!objectTracker->destroyDataSet(dataSetStonesAndChips))
        {
            LOG("Failed to destroy the tracking data set StonesAndChips.");
            return 0;
        }

        LOG("Successfully destroyed the data set StonesAndChips.");
        dataSetStonesAndChips = 0;
    }

    if (dataSetTarmac != 0)
    {
        if (objectTracker->getActiveDataSet() == dataSetTarmac &&
            !objectTracker->deactivateDataSet(dataSetTarmac))
        {
            LOG("Failed to destroy the tracking data set Tarmac because the data set "
                "could not be deactivated.");
            return 0;
        }

        if (!objectTracker->destroyDataSet(dataSetTarmac))
        {
            LOG("Failed to destroy the tracking data set Tarmac.");
            return 0;
        }

        LOG("Successfully destroyed the data set Tarmac.");
        dataSetTarmac = 0;
    }

    return 1;
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_onVuforiaInitializedNative(JNIEnv *, jobject)
{
    // Register the update callback where we handle the data set swap:
    Vuforia::registerCallback(&updateCallback);

    // Comment in to enable tracking of up to 2 targets simultaneously and
    // split the work over multiple frames:
    Vuforia::setHint(Vuforia::HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 2);
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargetsRenderer_renderFrame(JNIEnv *, jobject)
{
    //LOG("Java_com_vuforia_samples_ImageTargets_GLRenderer_renderFrame");

    // Clear color and depth buffer 
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // Get the state from Vuforia and mark the beginning of a rendering section
    Vuforia::State state = Vuforia::Renderer::getInstance().begin();
    
    // Explicitly render the Video Background
    Vuforia::Renderer::getInstance().drawVideoBackground();
       
    glEnable(GL_DEPTH_TEST);

    // We must detect if background reflection is active and adjust the culling direction. 
    // If the reflection is active, this means the post matrix has been reflected as well,
    // therefore standard counter clockwise face culling will result in "inside out" models. 
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK);
    if(Vuforia::Renderer::getInstance().getVideoBackgroundConfig().mReflection == Vuforia::VIDEO_BACKGROUND_REFLECTION_ON)
        glFrontFace(GL_CW);  //Front camera
    else
        glFrontFace(GL_CCW);   //Back camera

    // Set the viewport
    glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

    // Did we find any trackables this frame?
    for(int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
    {
        // Get the trackable:
        const Vuforia::TrackableResult* result = state.getTrackableResult(tIdx);
        const Vuforia::Trackable& trackable = result->getTrackable();
        Vuforia::Matrix44F modelViewMatrix = Vuforia::Tool::convertPose2GLMatrix(result->getPose());

        if(!isExtendedTrackingActivated)
        {
            // Choose the texture based on the target name:
            int textureIndex;
            if (strcmp(trackable.getName(), "box") == 0)
            {
                textureIndex = 0;
            }
            else if (strcmp(trackable.getName(), "box2") == 0)
            {
                textureIndex = 1;
            }
            else
            {
                textureIndex = 2;
            }

            const Texture* const thisTexture = textures[textureIndex];

            Vuforia::Matrix44F modelViewProjection;

            SampleUtils::translatePoseMatrix(0.0f, 0.0f, kObjectScale, &modelViewMatrix.data[0]);
            //SampleUtils::rotatePoseMatrix((rotation++) % 360, 0.0f, 1.0f, 0.0f, &modelViewMatrix.data[0]);
            SampleUtils::scalePoseMatrix(kObjectScale, kObjectScale, kObjectScale, &modelViewMatrix.data[0]);
            SampleUtils::multiplyMatrix(&projectionMatrix.data[0], &modelViewMatrix.data[0] , &modelViewProjection.data[0]);

            glUseProgram(shaderProgramID);

            glVertexAttribPointer(vertexHandle, 3, GL_FLOAT, GL_FALSE, 0, (const GLvoid*) &teapotVertices[0]);
            glVertexAttribPointer(textureCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, (const GLvoid*) &teapotTexCoords[0]);

            glEnableVertexAttribArray(vertexHandle);
            glEnableVertexAttribArray(textureCoordHandle);
            /*
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, thisTexture->mTextureID);
            glUniform1i(texSampler2DHandle, 0 ); //GL_TEXTURE0
            */
            glUniformMatrix4fv(mvpMatrixHandle, 1, GL_FALSE, (GLfloat*)&modelViewProjection.data[0] );
            
            glDrawElements(GL_TRIANGLES, NUM_TEAPOT_OBJECT_INDEX, GL_UNSIGNED_SHORT,(const GLvoid*) &teapotIndices[0]);
            
            glDisableVertexAttribArray(vertexHandle);
            glDisableVertexAttribArray(textureCoordHandle);

            SampleUtils::checkGlError("ImageTargets renderFrame");
        }
        else
        {
            const Texture* const thisTexture = textures[3];

            Vuforia::Matrix44F modelViewProjection;

            SampleUtils::translatePoseMatrix(0.0f, 0.0f, kBuildingsObjectScale, &modelViewMatrix.data[0]);
            SampleUtils::rotatePoseMatrix(90.0f, 1.0f, 0.0f, 0.0f, &modelViewMatrix.data[0]);
            SampleUtils::scalePoseMatrix(kBuildingsObjectScale, kBuildingsObjectScale, kBuildingsObjectScale,
                                         &modelViewMatrix.data[0]);
            SampleUtils::multiplyMatrix(&projectionMatrix.data[0],
                                        &modelViewMatrix.data[0] ,
                                        &modelViewProjection.data[0]);

            glUseProgram(shaderProgramID);

            glVertexAttribPointer(vertexHandle, 3, GL_FLOAT, GL_FALSE, 0,(const GLvoid*) &buildingsVerts[0]);
            glVertexAttribPointer(textureCoordHandle, 2, GL_FLOAT, GL_FALSE, 0, (const GLvoid*) &buildingsTexCoords[0]);

            glEnableVertexAttribArray(vertexHandle);
            glEnableVertexAttribArray(textureCoordHandle);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, thisTexture->mTextureID);
            glUniform1i(texSampler2DHandle, 0 /*GL_TEXTURE0*/);
            glUniformMatrix4fv(mvpMatrixHandle, 1, GL_FALSE, (GLfloat*)&modelViewProjection.data[0] );
            glDrawArrays(GL_TRIANGLES, 0, buildingsNumVerts);

            glDisableVertexAttribArray(vertexHandle);
            glDisableVertexAttribArray(textureCoordHandle);

            SampleUtils::checkGlError("ImageTargets renderFrame");
        }
    }

    glDisable(GL_DEPTH_TEST);

    Vuforia::Renderer::getInstance().end();
}


void
configureVideoBackground()
{
    // Get the default video mode:
    Vuforia::CameraDevice& cameraDevice = Vuforia::CameraDevice::getInstance();
    Vuforia::VideoMode videoMode = cameraDevice.
                                getVideoMode(Vuforia::CameraDevice::MODE_DEFAULT);


    // Configure the video background
    Vuforia::VideoBackgroundConfig config;
    config.mEnabled = true;
    config.mPosition.data[0] = 0.0f;
    config.mPosition.data[1] = 0.0f;
    
    if (isActivityInPortraitMode)
    {
        //LOG("configureVideoBackground PORTRAIT");
        config.mSize.data[0] = videoMode.mHeight
                                * (screenHeight / (float)videoMode.mWidth);
        config.mSize.data[1] = screenHeight;

        if(config.mSize.data[0] < screenWidth)
        {
            LOG("Correcting rendering background size to handle missmatch between screen and video aspect ratios.");
            config.mSize.data[0] = screenWidth;
            config.mSize.data[1] = screenWidth * 
                              (videoMode.mWidth / (float)videoMode.mHeight);
        }
    }
    else
    {
        //LOG("configureVideoBackground LANDSCAPE");
        config.mSize.data[0] = screenWidth;
        config.mSize.data[1] = videoMode.mHeight
                            * (screenWidth / (float)videoMode.mWidth);

        if(config.mSize.data[1] < screenHeight)
        {
            LOG("Correcting rendering background size to handle missmatch between screen and video aspect ratios.");
            config.mSize.data[0] = screenHeight
                                * (videoMode.mWidth / (float)videoMode.mHeight);
            config.mSize.data[1] = screenHeight;
        }
    }

    LOG("Configure Video Background : Video (%d,%d), Screen (%d,%d), mSize (%d,%d)", videoMode.mWidth, videoMode.mHeight, screenWidth, screenHeight, config.mSize.data[0], config.mSize.data[1]);

    // Set the config:
    Vuforia::Renderer::getInstance().setVideoBackgroundConfig(config);

    // Set the viewport to use when rendering an augmentation
    viewport[0] = ((screenWidth - config.mSize.data[0]) / 2) + config.mPosition.data[0];
    viewport[1] = ((screenHeight - config.mSize.data[1]) / 2) + config.mPosition.data[1];
    viewport[2] = config.mSize.data[0];
    viewport[3] = config.mSize.data[1];
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_initApplicationNative(
                            JNIEnv* env, jobject obj, jint width, jint height)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_initApplicationNative");
    
    // Store screen dimensions
    screenWidth = width;
    screenHeight = height;
        
    // Handle to the activity class:
    jclass activityClass = env->GetObjectClass(obj);

    jmethodID getTextureCountMethodID = env->GetMethodID(activityClass,
                                                    "getTextureCount", "()I");
    if (getTextureCountMethodID == 0)
    {
        LOG("Function getTextureCount() not found.");
        return;
    }

    textureCount = env->CallIntMethod(obj, getTextureCountMethodID);
    if (!textureCount)
    {
        LOG("getTextureCount() returned zero.");
        return;
    }

    textures = new Texture*[textureCount];

    jmethodID getTextureMethodID = env->GetMethodID(activityClass,
        "getTexture", "(I)Lcom/vuforia/samples/ImageTargets/Texture;");

    if (getTextureMethodID == 0)
    {
        LOG("Function getTexture() not found.");
        return;
    }

    // Register the textures
    for (int i = 0; i < textureCount; ++i)
    {

        jobject textureObject = env->CallObjectMethod(obj, getTextureMethodID, i); 
        if (textureObject == NULL)
        {
            LOG("GetTexture() returned zero pointer");
            return;
        }

        textures[i] = Texture::create(env, textureObject);
    }
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_initApplicationNative finished");
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_deinitApplicationNative(
                                                        JNIEnv* env, jobject obj)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_deinitApplicationNative");

    isExtendedTrackingActivated = false;

    // Release texture resources
    if (textures != 0)
    {    
        for (int i = 0; i < textureCount; ++i)
        {
            delete textures[i];
            textures[i] = NULL;
        }
    
        delete[]textures;
        textures = NULL;
        
        textureCount = 0;
    }
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_startCamera(JNIEnv *,
                                                                         jobject, jint camera)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_startCamera");
    
    currentCamera = static_cast<Vuforia::CameraDevice::CAMERA_DIRECTION> (camera);

    // Initialize the camera:
    if (!Vuforia::CameraDevice::getInstance().init(currentCamera))
        return;

    // Select the default camera mode:
    if (!Vuforia::CameraDevice::getInstance().selectVideoMode(
                                Vuforia::CameraDevice::MODE_DEFAULT))
        return;

    // Configure the rendering of the video background
    configureVideoBackground();
    
    // Start the camera:
    if (!Vuforia::CameraDevice::getInstance().start())
        return;

    // Uncomment to enable flash
    //if(Vuforia::CameraDevice::getInstance().setFlashTorchMode(true))
    //    LOG("IMAGE TARGETS : enabled torch");

    // Uncomment to enable infinity focus mode, or any other supported focus mode
    // See CameraDevice.h for supported focus modes
    //if(Vuforia::CameraDevice::getInstance().setFocusMode(Vuforia::CameraDevice::FOCUS_MODE_INFINITY))
    //    LOG("IMAGE TARGETS : enabled infinity focus");

    // Start the tracker:
    Vuforia::TrackerManager& trackerManager = Vuforia::TrackerManager::getInstance();
    Vuforia::Tracker* objectTracker = trackerManager.getTracker(Vuforia::ObjectTracker::getClassType());
    if(objectTracker != 0)
        objectTracker->start();
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_stopCamera(JNIEnv *, jobject)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_stopCamera");

    // Stop the tracker:
    Vuforia::TrackerManager& trackerManager = Vuforia::TrackerManager::getInstance();
    Vuforia::Tracker* objectTracker = trackerManager.getTracker(Vuforia::ObjectTracker::getClassType());
    if(objectTracker != 0)
        objectTracker->stop();
    
    Vuforia::CameraDevice::getInstance().stop();
    Vuforia::CameraDevice::getInstance().deinit();
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_setProjectionMatrix(JNIEnv *, jobject)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargets_setProjectionMatrix");

    // Cache the projection matrix:
    const Vuforia::CameraCalibration& cameraCalibration =
                                Vuforia::CameraDevice::getInstance().getCameraCalibration();
    projectionMatrix = Vuforia::Tool::getProjectionGL(cameraCalibration, 10.0f, 5000.0f);
}

// ----------------------------------------------------------------------------
// Activates Camera Flash
// ----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_activateFlash(JNIEnv*, jobject, jboolean flash)
{
    return Vuforia::CameraDevice::getInstance().setFlashTorchMode((flash==JNI_TRUE)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_autofocus(JNIEnv*, jobject)
{
    return Vuforia::CameraDevice::getInstance().setFocusMode(Vuforia::CameraDevice::FOCUS_MODE_TRIGGERAUTO) ? JNI_TRUE : JNI_FALSE;
}


JNIEXPORT jboolean JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_setFocusMode(JNIEnv*, jobject, jint mode)
{
    int focusMode;

    switch ((int)mode)
    {
        case 0:
            focusMode = Vuforia::CameraDevice::FOCUS_MODE_NORMAL;
            break;
        
        case 1:
            focusMode = Vuforia::CameraDevice::FOCUS_MODE_CONTINUOUSAUTO;
            break;
            
        case 2:
            focusMode = Vuforia::CameraDevice::FOCUS_MODE_INFINITY;
            break;
            
        case 3:
            focusMode = Vuforia::CameraDevice::FOCUS_MODE_MACRO;
            break;
    
        default:
            return JNI_FALSE;
    }
    
    return Vuforia::CameraDevice::getInstance().setFocusMode(focusMode) ? JNI_TRUE : JNI_FALSE;
}


JNIEXPORT jboolean JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_startExtendedTracking(JNIEnv*, jobject)
{
    Vuforia::TrackerManager& trackerManager = Vuforia::TrackerManager::getInstance();
    Vuforia::ObjectTracker* objectTracker = static_cast<Vuforia::ObjectTracker*>(
          trackerManager.getTracker(Vuforia::ObjectTracker::getClassType()));

    Vuforia::DataSet* currentDataSet = objectTracker->getActiveDataSet();
    if (objectTracker == 0 || currentDataSet == 0)
        return JNI_FALSE;

    for (int tIdx = 0; tIdx < currentDataSet->getNumTrackables(); tIdx++)
    {
        Vuforia::Trackable* trackable = currentDataSet->getTrackable(tIdx);
        if(!trackable->startExtendedTracking())
            return JNI_FALSE;
    }

    isExtendedTrackingActivated = true;
    return JNI_TRUE;
}


JNIEXPORT jboolean JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargets_stopExtendedTracking(JNIEnv*, jobject)
{
    Vuforia::TrackerManager& trackerManager = Vuforia::TrackerManager::getInstance();
    Vuforia::ObjectTracker* objectTracker = static_cast<Vuforia::ObjectTracker*>(
          trackerManager.getTracker(Vuforia::ObjectTracker::getClassType()));

    Vuforia::DataSet* currentDataSet = objectTracker->getActiveDataSet();
    if (objectTracker == 0 || currentDataSet == 0)
        return JNI_FALSE;

    for (int tIdx = 0; tIdx < currentDataSet->getNumTrackables(); tIdx++)
    {
        Vuforia::Trackable* trackable = currentDataSet->getTrackable(tIdx);
        if(!trackable->stopExtendedTracking())
            return JNI_FALSE;
    }
    
    isExtendedTrackingActivated = false;
    return JNI_TRUE;
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargetsRenderer_initRendering(
                                                    JNIEnv* env, jobject obj)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargetsRenderer_initRendering");

    // Define clear color
    glClearColor(0.0f, 0.0f, 0.0f, Vuforia::requiresAlpha() ? 0.0f : 1.0f);
    
    // Now generate the OpenGL texture objects and add settings
    for (int i = 0; i < textureCount; ++i)
    {
        glGenTextures(1, &(textures[i]->mTextureID));
        glBindTexture(GL_TEXTURE_2D, textures[i]->mTextureID);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, textures[i]->mWidth,
                textures[i]->mHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                (GLvoid*)  textures[i]->mData);
    }
  
    shaderProgramID     = SampleUtils::createProgramFromBuffer(cubeMeshVertexShader,
                                                            cubeFragmentShader);

    vertexHandle        = glGetAttribLocation(shaderProgramID,
                                                "vertexPosition");
    textureCoordHandle  = glGetAttribLocation(shaderProgramID,
                                                "vertexTexCoord");
    mvpMatrixHandle     = glGetUniformLocation(shaderProgramID,
                                                "modelViewProjectionMatrix");
    texSampler2DHandle  = glGetUniformLocation(shaderProgramID, 
                                                "texSampler2D");
    //vertexColorHandler = glGetAttribLocation(shaderProgram, "vertexColor");
                                                
}


JNIEXPORT void JNICALL
Java_com_vuforia_samples_ImageTargets_ImageTargetsRenderer_updateRendering(
                        JNIEnv* env, jobject obj, jint width, jint height)
{
    LOG("Java_com_vuforia_samples_ImageTargets_ImageTargetsRenderer_updateRendering");

    // Update screen dimensions
    screenWidth = width;
    screenHeight = height;

    // Reconfigure the video background
    configureVideoBackground();
}


#ifdef __cplusplus
}
#endif
