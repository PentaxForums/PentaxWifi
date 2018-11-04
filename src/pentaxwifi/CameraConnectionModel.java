/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi;

import com.ricoh.camera.sdk.wireless.api.CameraDevice;
import com.ricoh.camera.sdk.wireless.api.CameraDeviceDetector;
import com.ricoh.camera.sdk.wireless.api.CameraEventListener;
import com.ricoh.camera.sdk.wireless.api.CameraImage;
import com.ricoh.camera.sdk.wireless.api.Capture;
import com.ricoh.camera.sdk.wireless.api.CaptureState;
import com.ricoh.camera.sdk.wireless.api.DeviceInterface;
import com.ricoh.camera.sdk.wireless.api.response.Response;
import com.ricoh.camera.sdk.wireless.api.response.Error;
import com.ricoh.camera.sdk.wireless.api.response.Result;
import com.ricoh.camera.sdk.wireless.api.response.StartCaptureResponse;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureMethod;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureSetting;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CustomImage;
import com.ricoh.camera.sdk.wireless.api.setting.capture.DigitalFilter;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ExposureCompensation;
import com.ricoh.camera.sdk.wireless.api.setting.capture.FNumber;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ISO;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ShutterSpeed;
import com.ricoh.camera.sdk.wireless.api.setting.capture.StillImageCaptureFormat;
import com.ricoh.camera.sdk.wireless.api.setting.capture.StillImageQuality;
import com.ricoh.camera.sdk.wireless.api.setting.capture.WhiteBalance;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Model for camera connections through the Pentax Wi-Fi SDK
 * @author Adam
 */
public class CameraConnectionModel
{
    private CameraDevice cam;
    
    private ShutterSpeed tv;
    private FNumber av;
    private ExposureCompensation ev;
    private ISO iso;
    // TODO - also track other state supported by the SDK, such as WB and CI

    private final Queue<FuturePhoto> imageQueue;
    private final Map<Capture, Boolean> captureState;
    private final List<CameraImage> capturedImages;
    private final Map<CameraImage, File> downloadedImages;
    private final Map<CameraImage, File> downloadedThumbs;

    private boolean queueLocked;
    private ExecutorService pool;
    private ScheduledExecutorService capturePool;
    
    // Time to re-check status in blocked capture thread (ms)
    public static final int WAIT_INTERVAL = 250;
    
    // Base timeout for an image capture (ms)
    public static final int MIN_TIMEOUT = 20000;
    
    // Keepalive interval (ms)
    public static final int KEEPALIVE = 20000;
    
    // Connection retry time (ms)
    public static final int STARTUP_RETRY = 5000;
    
    // API v1.1 only reliably supports a single concurrent download
    public static final int NUM_DOWNLOAD_THREADS = 1;
    
    /**
     * Creates the model with empty state 
     */
    public CameraConnectionModel()
    {
        this.imageQueue = new LinkedList<>();
        this.capturedImages = new ArrayList<>();
        this.downloadedImages = new HashMap<>();
        this.downloadedThumbs = new HashMap<>();
        this.captureState = new HashMap<>();
        this.queueLocked = false;
        this.pool = Executors.newFixedThreadPool(NUM_DOWNLOAD_THREADS);
        this.capturePool = Executors.newScheduledThreadPool(1);
    }
        
    /**
     * Gets the most recently captured image
     * @return 
     */
    public CameraImage getLastImage()
    {
        if (!this.capturedImages.isEmpty())
        {
            return this.capturedImages.get(this.capturedImages.size() - 1);
        }
                
        return null;
    }
        
    /**
     * Gets a list of all captured images
     * @return 
     */
    public List<CameraImage> getCapturedImages()
    {
        return this.capturedImages;
    }
    
    /**
     * Gets the image file corresponding to a downloaded camera image
     * @param i
     * @return 
     */
    public File getDownloadedImage(CameraImage i)
    {
        return this.downloadedImages.get(i);
    }
    
    /**
     * Gets the thumbnail corresponding to a downloaded camera image
     * @param i
     * @return 
     */
    public File getDownloadedThumb(CameraImage i)
    {
        return this.downloadedThumbs.get(i);
    }
    
    /**
     * Gets all images currently on the camera.  Not downloaded unless download function is called.
     * @return 
     * @throws pentaxwifi.CameraException 
     */
    public List<CameraImage> getAllImagesOnCamera() throws CameraException
    {
        if (connected())
        {
            return this.cam.getImages();
        }
        else
        {
            throw new CameraException("Not connected");
        }
    }
    
    /**
     * Aborts queued image downloads
     * @param l 
     */
    synchronized public void abortImageDownload(CaptureEventListener l)
    {
        this.pool.shutdownNow();
        
        if (l != null)
        {
            l.imageDownloaded(null, null, false);
        }
        
        this.pool = Executors.newFixedThreadPool(NUM_DOWNLOAD_THREADS);
    }
        
    /**
     * Asynchronously downloads image data
     * @param savePath
     * @param i
     * @param isThumbnail 
     * @param l 
     */
    public void downloadImage(String savePath, CameraImage i, boolean isThumbnail, CaptureEventListener l)
    {        
        this.pool.submit(
            new Thread(() -> 
            {
                FileOutputStream outputStream = null;
                File f = null;
                boolean error = false;

                try
                {
                    if (savePath == null)
                    {
                        f = File.createTempFile(isThumbnail ? "thumb" : "image", i.getName());
                    }
                    else
                    {
                        f = new File(savePath + "/" + i.getName());
                    }
                    
                    // TODO - ensure these are properly closed if thread interrupted
                    
                    Response response;
                    
                    outputStream = new FileOutputStream(f);
                    
                    if (isThumbnail)
                    {
                        response = i.getThumbnail(outputStream);
                        downloadedThumbs.put(i, f);
                        
                        if (!response.getErrors().isEmpty())
                        {
                            throw new IOException("Failed to download file: " + response.getErrors().toString());
                        }
                    }
                    else
                    {
                        response = i.getData(outputStream);
                        downloadedImages.put(i, f);
                        
                        if (!response.getErrors().isEmpty())
                        {
                            throw new IOException("Failed to download file: " + response.getErrors().toString());
                        }
                    }

                    if (l != null)
                    {
                        l.imageDownloaded(i, f, isThumbnail);    
                    }
                }
                catch (IOException e)
                {                    
                    if (l != null)
                    {
                        l.imageDownloaded(i, null, isThumbnail);    
                    }
                    
                    error = true;
                }
                finally
                {
                    if (outputStream != null)
                    {
                        try
                        {
                            outputStream.close();
                        }
                        catch (IOException e)
                        {
                            //do nothing
                        }
                    }
                    
                    // On error, delete the incomplete file after stream is closed
                    if (error)
                    {
                        if (f != null)
                        {                            
                            f.delete();
                        }
                    }
                }
            })
        );
    }
    
    /**
     * String representation of connection state
     * @return 
     */
    @Override
    public String toString()
    {
        if (connected())
        {
            return String.format(this.getClass().getSimpleName() + ": %s %s %s %s %s", getCameraModel(), tv, av, ev, iso);
        }
        else
        {
            return this.getClass().getSimpleName() + ": Not connected";
        }
    }
    
    /**
     * Adds a photo to be captured later
     * @param p
     * @throws CameraException 
     */
    synchronized public void enqueuePhoto(FuturePhoto p) throws CameraException
    {
        if (!queueLocked)
        {
            this.imageQueue.add(p);
        }
        else
        {
            throw new CameraException("Queue is currently being processed.");
        }
    }
    
    /**
     * Requests to abort processing the queue
     */
    synchronized public void abortQueue()
    {
        if (queueLocked)
        {
            this.capturePool.shutdownNow();
            this.queueLocked = false;
            
            for (CameraEventListener e : this.cam.getEventListeners())
            {
                (new Thread (() -> {
                  e.captureComplete(null, null);  
                })).start();
            }
        }
    }
    
    /**
     * Converts shutter speed string to an integer (used for timeout calculation)
     * @param ss
     * @return 
     */
    private int parseShutterSpeed(ShutterSpeed ss)
    {
        String val = ss.getValue().toString();
        
        if (val.contains("/"))
        {
            return 1;
        }
        else
        {
            return Math.round(Float.parseFloat(val)); 
        }
    }
    
    /**
     * Captures the next queued photo (only to be called internally)
     */
    private void dequeuePhoto(int timeout)
    {
        // Queue must be in progress to proceed
        if (!queueLocked || !connected())
        {
            return;
        }
        
        if (!imageQueue.isEmpty())
        {
            FuturePhoto next = imageQueue.remove();
            
            try
            {                
                Capture c = this.captureImageWithSettings(next.focus, next.settings);
                
                int waited = 0;
                
                // Block thread until capture completes
                while (!captureState.containsKey(c))
                {
                    try
                    {   
                        if (waited > timeout + 2 * parseShutterSpeed(this.tv))
                        {
                            throw new InterruptedException("Timeout in image queue.");
                        }
                        
                        Thread.sleep(WAIT_INTERVAL);
                        waited += WAIT_INTERVAL;
                    }
                    catch (InterruptedException ex)
                    {
                        // Put the image back in the queue
                        imageQueue.add(next);
                        abortQueue();
                        return;
                    }                    
                }
                
                if (imageQueue.isEmpty())
                {
                    this.queueLocked = false;
                }  
            }
            catch (CameraException ex)
            {
                abortQueue();
                
                System.out.println(ex.toString());
            }
        }
    }
    
    /**
     * Checks the status of the image queue
     * @return 
     */
    synchronized public int getQueueSize()
    {
        return this.imageQueue.size();
    }
    
    /**
     * Checks the status of the image queue
     * @return 
     */
    synchronized public boolean queueProcessing()
    {
        return this.queueLocked;
    }
    
    /**
     * Clears all queued photos
     */
    synchronized public void emptyQueue()
    {
        if (!this.queueLocked && connected())
        {    
            this.imageQueue.clear();
        }
    }
    
    /**
     * Starts sequential capture of queued images
     * @param delay
     * @throws CameraException 
     */
    synchronized public void processQueue(int delay) throws CameraException
    {
        if (!connected())
        {
            throw new CameraException("Not connected.");
        }
        
        if (!this.queueLocked)
        {     
            this.queueLocked = true;
            this.capturePool = Executors.newScheduledThreadPool(1);
            
            // Determine an appropriate timeout
            int timeout = delay * 2 * 1000 + MIN_TIMEOUT;
            
            // Start one worker for each item
            for (int i = 0; i < imageQueue.size(); i ++)
            {
                Runnable r = () -> {
                    dequeuePhoto(timeout);
                };
                                
                if (delay > 0)
                {                                    
                    capturePool.scheduleWithFixedDelay(
                        r, 0, delay, TimeUnit.SECONDS);                    
                }
                else
                {
                    capturePool.submit(r);
                }
            }
        }
        else
        {
            throw new CameraException("Queue currently being processed.");
        }
    }
        
    /**
     * Obtains current settings from the camera
     * @throws CameraException 
     */
    public void refreshCurrentSettings() throws CameraException
    {
        tv = new ShutterSpeed();
        ev  = new ExposureCompensation();
        av = new FNumber();
        iso = new ISO();
        
        Response r =
            cam.getCaptureSettings(
                Arrays.asList(tv, ev, av, iso));

        if (!r.getErrors().isEmpty())
        {
            throw new CameraException(r.getErrors().toString());
        } 
    }
    
    /**
     * Settings camera settings, then commands an image capture
     * @param focus
     * @param settings
     * @return 
     * @throws CameraException 
     */
    public Capture captureImageWithSettings(Boolean focus, List<CaptureSetting> settings) throws CameraException
    {
        for (CaptureSetting s : settings)
        {
            setCaptureSettings(Arrays.asList(s));
        }
                
        return captureStillImage(focus);
    }
    
    /**
     * Prepares camera settings for the next capture
     * @param settings
     * @throws CameraException 
     */
    public void setCaptureSettings(List<CaptureSetting> settings) throws CameraException
    {                
        if (connected())
        {  
            for (CaptureSetting s : settings)
            {
                Response r = cam.setCaptureSettings(Arrays.asList(s));
                if (r.getResult() == Result.ERROR)
                {
                    throw new CameraException("Settings configuration FAILED: " + r.getErrors().get(0).getMessage());
                }
            }
        }
        else
        {
            throw new CameraException("Not connected");
        }
        
        this.refreshCurrentSettings();
    }
    
    /**
     * Starts live view
     * @throws CameraException 
     */
    public void startLiveView() throws CameraException
    {
        if (connected())
        {
            Response r = this.cam.startLiveView();
            if (r.getResult() == Result.ERROR)
            {
                throw new CameraException("Live view start FAILED: " + r.getErrors().get(0).getMessage());
            }
        }
        else
        {
            throw new CameraException("Not connected");
        }
    }
    
    /**
     * Stops live view
     * @throws CameraException 
     */
    public void stopLiveView() throws CameraException
    {
        if (connected())
        {
            Response r = this.cam.stopLiveView();
            if (r.getResult() == Result.ERROR)
            {
                throw new CameraException("Live view stop FAILED: " + r.getErrors().get(0).getMessage());
            }
        }
        else
        {
            throw new CameraException("Not connected");
        }
    }
    
    /**
     * Attempts to establish a connection to the camera
     * @throws pentaxwifi.CameraException
     */
    public final void connect() throws CameraException
    {
        disconnect();
        
        List<CameraDevice> detectedDevices =
            CameraDeviceDetector.detect(DeviceInterface.WLAN);
        
        if (detectedDevices.isEmpty() == false)
        {
            cam = detectedDevices.get(0);  
            
            Response r = cam.connect(DeviceInterface.WLAN);
                        
            if (!r.getErrors().isEmpty())
            {
                disconnect();
                
                // Retry once
                try
                {
                    Thread.sleep(STARTUP_RETRY);
                    connect();
                }
                catch (InterruptedException ex) {}
                
                if (!connected())
                {
                    throw new CameraException(r.getErrors().toString());
                }
            }
                        
            refreshCurrentSettings();
            
            // Add local listener for completed captures
            this.addListener(
                new CameraEventListener()
                {
                    @Override
                    public void captureComplete(CameraDevice sender, Capture capture)
                    {                    
                        if (capture != null)
                        {
                            captureState.put(capture, true);
                        }
                    }   

                    @Override
                    public void imageStored(CameraDevice sender, CameraImage image)
                    {
                        capturedImages.add(image);
                    }

                    @Override
                    public void deviceDisconnected(CameraDevice sender)
                    {   
                        disconnect();
                    }
                }
            );
            
            // Start keepalive thread
            ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
            exec.scheduleAtFixedRate(() -> {
                try
                {
                    // Only fire if the camera isn't busy
                    if (null == this.cam.getStatus().getCurrentCapture() ||
                            this.cam.getStatus().getCurrentCapture().getState() == CaptureState.COMPLETE)
                    {
                        refreshCurrentSettings();
                    }
                }
                catch (CameraException e)
                {
                    disconnect();
                    exec.shutdownNow();
                }
            }, 0, KEEPALIVE, TimeUnit.SECONDS);
        }    
        
        if (!connected())
        {
            throw new CameraException("Failed to detect camera.");
        }
    }
        
    /**
     * Disconnects from the camera
     */
    public void disconnect()
    {
        if (connected())
        {
            cam.disconnect(DeviceInterface.WLAN);
            cam = null;
        }
    }
    
    /**
     * Checks if the connection was established
     * @return 
     */
    public boolean connected()
    {
        return cam != null;
    }
    
    /**
     * Commands the camera to focus
     * @throws CameraException 
     */
    public void focus() throws CameraException
    {
        if (connected())
        {        
            try
            {
                Response r = cam.focus();

                if (r.getResult() != Result.OK)
                {
                    throw new CameraException("Focus FAILED: " + r.getErrors().get(0).getMessage());
                }
            }
            catch (UnsupportedOperationException e)
            {
                throw new CameraException("Focus is not supported.");
            }
        }
        else
        {
            throw new CameraException("Not connected");
        }
    }
    
    /**
     * Commands the camera to take a photo
     * @param focus
     * @return 
     * @throws CameraException 
     */
    public Capture captureStillImage(boolean focus) throws CameraException
    {
        if (!connected())
        {
            throw new CameraException("Not connected");
        }
        
        CaptureMethod captureMethod = new CaptureMethod();
        Response response =
            cam.getCaptureSettings(
                Arrays.asList((CaptureSetting) captureMethod));
        
        if (response.getResult() == Result.ERROR)
        {
            for (CameraEventListener e : this.cam.getEventListeners())
            {
                (new Thread (() -> {
                  e.captureComplete(null, null);  
                })).start();
            }
            
            throw new CameraException("Image capture FAILED: " + response.getErrors().get(0).getMessage());
        }

        if (!CaptureMethod.STILL_IMAGE.equals(captureMethod))
        {
            List<CaptureSetting> availableList = captureMethod.getAvailableSettings();
            if (availableList.contains(CaptureMethod.STILL_IMAGE))
            {
                response = cam.setCaptureSettings(
                    Arrays.asList((CaptureSetting) CaptureMethod.STILL_IMAGE));
                if (response.getResult() == Result.ERROR)
                {
                    throw new CameraException("Image capture FAILED: " + response.getErrors().get(0).getMessage());
                }
            }
            else 
            {
                throw new CameraException("Camera does not support image capture.");
            }
        }

        StartCaptureResponse startCaptureResponse = null;
        
        try
        {
            startCaptureResponse = cam.startCapture(focus);
        }
        catch (UnsupportedOperationException e)
        {
            throw new CameraException("Camera does not support image capture.");
        }
        
        if (startCaptureResponse.getResult() == Result.OK)
        {
            System.out.printf("Capturing StillImage has started. Capture ID: %s%n",
                startCaptureResponse.getCapture().getId());
            
            return startCaptureResponse.getCapture();
        }
        else
        {    
            throw new CameraException("Image capture FAILED: " + startCaptureResponse.getErrors().get(0).getMessage());            
        }
    }
    
    // Event listener functions
    
    public final void addListener(CameraEventListener e)
    {
        if (connected())
        {
            cam.addEventListener(e);
        }
    }
    
    public void removeListener(CameraEventListener e)
    {
        if (connected())
        {
            cam.removeEventListener(e);
        }
    }
    
    // Camera state getters
    
    public String getCameraModel()
    {
        if (connected())
        {
            return this.cam.getModel();
        }      
        
        return "";
    }    
    
    public int getCameraBattery()
    {        
        if (connected())
        {
            return this.cam.getStatus().getBatteryLevel();
        }
        
        return 0;
    }
    
    public String getCameraSerial()
    {
        if (connected())
        {
            return this.cam.getSerialNumber();
        }      
        
        return "";
    }  
    
    public String getCameraManufacturer()
    {
        if (connected())
        {
            return this.cam.getManufacturer();
        }      
        
        return "";
    }  
    
    public String getCameraFirmware()
    {
        if (connected())
        {
            return this.cam.getFirmwareVersion();
        }      
        
        return "";
    }  
    
    // Settings generators
    
    public List<CaptureSetting> genCaptureSettings(FNumber nAv)
    {
        return Arrays.asList(
            nAv
        );
    }
    
    public List<CaptureSetting> genCaptureSettings(ShutterSpeed nTv)
    {
        return Arrays.asList(
            nTv
        );
    }
    
    public List<CaptureSetting> genCaptureSettings(ISO nIso)
    {
        return Arrays.asList(
            nIso
        );
    }
    
    public List<CaptureSetting> genCaptureSettings(ExposureCompensation nEv)
    {
        return Arrays.asList(
            nEv
        );
    }
    
    public List<CaptureSetting> genCaptureSettings(ExposureCompensation nEv, ISO nIso)
    {
        return Arrays.asList(
            nEv, nIso
        );
    }
    
    public List<CaptureSetting> genCaptureSettings(ShutterSpeed nTv, ExposureCompensation nEv, ISO nIso)
    {
        return Arrays.asList(
            nTv, nEv, nIso
        );
    }
    
    public List<CaptureSetting> genCaptureSettings(FNumber nAv, ExposureCompensation nEv, ISO nIso)
    {
        return Arrays.asList(
            nAv, nEv, nIso
        );
    }
    
    public List<CaptureSetting> genCaptureSettings(FNumber nAv, ShutterSpeed nTv)
    {
        return Arrays.asList(
            nTv, nAv
        );
    }
    
    public List<CaptureSetting> genCaptureSettings(FNumber nAv, ShutterSpeed nTv, ISO nIso)
    {
        return Arrays.asList(
            nTv, nAv, nIso
        );
    }
    
    // Allowed camera setting getters
    
    public List<CaptureSetting> getAvailableAv()
    {
        if (connected())
        {
            return this.av.getAvailableSettings();
        }
        
        return new ArrayList<>();
    }
    
    public List<CaptureSetting> getAvailableTv()
    {
        if (connected())
        {
            return this.tv.getAvailableSettings();
        }
        
        return new ArrayList<>();
    }
    
    public List<CaptureSetting> getAvailableISO()
    {
        if (connected())
        {
            return this.iso.getAvailableSettings();
        }
        
        return new ArrayList<>();
    }
    
    public List<CaptureSetting> getAvailableEV()
    {
        if (connected())
        {
            return this.ev.getAvailableSettings();
        }
        
        return new ArrayList<>();
    }
    
    // Individual settings getters
    
    public ShutterSpeed getTv()
    {
        return tv;
    }
    
    public ExposureCompensation getEv()
    {
        return ev;
    }
    
    public FNumber getAv()
    {
        return av;
    }
    
    public ISO getISO()
    {
        return iso;
    }
}
