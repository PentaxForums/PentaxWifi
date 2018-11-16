/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pfpentaxtether;

import com.ricoh.camera.sdk.wireless.api.CameraDevice;
import com.ricoh.camera.sdk.wireless.api.CameraDeviceDetector;
import com.ricoh.camera.sdk.wireless.api.CameraEventListener;
import com.ricoh.camera.sdk.wireless.api.CameraImage;
import com.ricoh.camera.sdk.wireless.api.CameraStatus;
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
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import pfpentaxtether.usb.PFRicohUSBSDKBridge;
import pfpentaxtether.usb.USBCameraDeviceDetector;

/**
 * Model for camera connections through the Pentax Wi-Fi SDK
 * @author Adam
 */
public class CameraConnectionModel
{
    private CameraDevice cam;
    
    private CaptureSetting tv;
    private CaptureSetting av;
    private CaptureSetting ev;
    private CaptureSetting iso;
    // TODO - also track other state supported by the SDK, such as WB and CI

    private final Deque<FuturePhoto> imageQueue;
    private final Map<Capture, Boolean> captureState;
    private final List<CameraImage> capturedImages;

    private boolean queueLocked;
    private final ImageDownloadManager dm;
    private ScheduledExecutorService capturePool;
    
    // Track the most recently queued photo so we can roll back in case of failure
    private FuturePhoto p;
    
    // Time to re-check status in blocked capture thread (ms)
    public static final int WAIT_INTERVAL = 250;
    
    // Base timeout for an image capture (ms)
    public static final int MIN_TIMEOUT = 20000;
    
    // Keepalive interval (ms)
    public static final int KEEPALIVE = 45000;
    
    // Connection retry time (ms)
    public static final int STARTUP_RETRY = 5000;
        
    // Camera status constants
    public static final int CAMERA_OK = 1;
    public static final int CAMERA_BUSY = 2;
    public static final int CAMERA_UNKNOWN = 3;
    public static final int CAMERA_DISCONNECTED = 4;
        
    /**
     * Creates the model with empty state 
     */
    public CameraConnectionModel()
    {
        this.imageQueue = new LinkedList<>();
        this.capturedImages = new ArrayList<>();
        this.captureState = new HashMap<>();
        this.queueLocked = false;
        this.capturePool = Executors.newScheduledThreadPool(1);
        this.dm = new ImageDownloadManager(this);
    }
    
    /**
     * Returns a reference to the image download manager
     * @return 
     */
    public ImageDownloadManager getDownloadManager()
    {
        return dm;
    }
    
    /**
     * Attempts to determine the camera's current status
     * @return 
     */
    public int getCameraStatus()
    {
        if (!this.isConnected())
        {
            return CAMERA_DISCONNECTED;
        }
        
        if (null == this.cam)
        {
            return CAMERA_UNKNOWN;
        }
        
        // This prevents a double call to the SDK
        CameraStatus s = this.cam.getStatus();
        
        if (null == s)
        {
            return CAMERA_UNKNOWN;
        }
        
        if (null == s.getCurrentCapture() || s.getCurrentCapture().getState() == CaptureState.COMPLETE)
        {
            return CAMERA_OK;
        }
        
        return CAMERA_BUSY;
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
     * Gets all images currently on the camera.  Not downloaded unless download function is called.
     * @return 
     * @throws pfpentaxtether.CameraException 
     */
    public List<CameraImage> getAllImagesOnCamera() throws CameraException
    {
        if (isConnected())
        {
            return getCam().getImages();
        }
        else
        {
            throw new CameraException("Not connected");
        }
    }
    
    /**
     * String representation of connection state
     * @return 
     */
    @Override
    public String toString()
    {
        if (isConnected())
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
            
            // Restore uncaptured image
            if (p != null)
            {
                this.imageQueue.addFirst(p);
                p = null;
            }
            
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
    private int parseShutterSpeed(CaptureSetting ss)
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
        if (!queueLocked || !isConnected())
        {
            return;
        }
        
        if (!imageQueue.isEmpty())
        {
            // It is possible that this will never be restored if thread gets interruped
            FuturePhoto next = imageQueue.remove();
            
            // So keep track of it...
            p = next;
            
            try
            {                
                Capture c = this.captureImageWithSettings(next.focus, next.settings);
                captureState.put(c, false);
                
                int waited = 0;
                
                // Block thread until capture completes
                while (captureState.get(c) == false)
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
                        // The abort function will put the image back into the queue
                        abortQueue();
                        return;
                    }                    
                }
                
                // Capture was successful
                p = null;
                
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
     * Checks the size of the image queue
     * @return 
     */
    synchronized public int getQueueSize()
    {
        return this.imageQueue.size();
    }
    
    /**
     * Checks if the image queue is empty
     * @return 
     */
    synchronized public boolean isQueueEmpty()
    {
        return getQueueSize() == 0;
    }
    
    /**
     * Checks the status of the image queue
     * @return 
     */
    synchronized public boolean isQueueProcessing()
    {
        return this.queueLocked;
    }
    
    /**
     * Clears all queued photos
     */
    synchronized public void emptyQueue()
    {
        if (!this.queueLocked && isConnected())
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
        if (!isConnected())
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
                    // scheduleAtFixedRate does not work as consistently
                    capturePool.schedule(r, delay * i, TimeUnit.SECONDS);               
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
        List<CaptureSetting> l = Arrays.asList(new ShutterSpeed(), new ExposureCompensation(), new FNumber(), new ISO());
        
        Response r = getCam().getCaptureSettings(l);

        if (!r.getErrors().isEmpty())
        {
            throw new CameraException(r.getErrors().toString());
        } 
        
        this.tv = l.get(0);
        this.ev = l.get(1);
        this.av = l.get(2);
        this.iso = l.get(3);
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
    
    private CameraDevice getCam() throws CameraException
    {
        if (cam == null)
        {
            throw new CameraException("Camera is not connected");
        }
        
        return cam;
    }
    
    /**
     * Prepares camera settings for the next capture
     * @param settings
     * @throws CameraException 
     */
    public void setCaptureSettings(List<CaptureSetting> settings) throws CameraException
    {               
        boolean updated = false;
        
        if (isConnected())
        {  
            for (CaptureSetting s : settings)
            {
                if (s == null) continue;

                boolean doContinue = false;
                
                for (CaptureSetting current : new CaptureSetting[] {this.av, this.tv, this.ev, this.iso})
                {
                    if (s.getName().equals(current.getName()) && s.getValue().equals(current.getValue()))
                    {
                        doContinue = true;
                        break;
                    }
                }

                if (doContinue)
                {
                    System.out.println("Skipping setting " + s + " because it matches the current value");
                    continue;
                }
                
                updated = true;
                
                Response r = getCam().setCaptureSettings(Arrays.asList(s));
                
                if (r != null)
                {
                    if (r.getResult() == Result.ERROR)
                    {
                        throw new CameraException("Settings configuration FAILED: " + r.getErrors().get(0).getMessage());
                    }
                }
                else
                {
                    throw new CameraException("Settings configuration FAILED: no response from camera.");
                }
            }
        }
        else
        {
            throw new CameraException("Not connected");
        }
        
        if (updated)
        {
            this.refreshCurrentSettings();
        }
    }
    
    /**
     * Starts live view
     * @throws CameraException 
     */
    public void startLiveView() throws CameraException
    {
        if (isConnected())
        {
            Response r = getCam().startLiveView();
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
        if (isConnected())
        {
            Response r = getCam().stopLiveView();
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
     * @throws pfpentaxtether.CameraException
     */
    public final void connect() throws CameraException
    {
        disconnect();
        
        /*List<CameraDevice> detectedDevices =
            CameraDeviceDetector.detect(DeviceInterface.WLAN);*/
        
        List<CameraDevice> detectedDevices =
            USBCameraDeviceDetector.detect(USBCameraDeviceDetector.PF_USB_BRIDGE);
        
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
                
                if (!isConnected())
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
            final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
            exec.scheduleWithFixedDelay(() -> {
                try
                {                 
                    //if (null == this.cam.getStatus().getCurrentCapture() ||
                    //        this.cam.getStatus().getCurrentCapture().getState() == CaptureState.COMPLETE)
                    
                    // Only fire if the camera isn't busy
                    if(getCameraStatus() == CAMERA_OK)
                    {
                        System.out.println("Keepalive");
                        refreshCurrentSettings();
                    }
                }
                catch (CameraException e)
                {
                    // TODO - is this appropriate?
                    System.out.println("Keepalive raised exception: " + e.toString());
                    disconnect();
                    exec.shutdownNow();
                }
            }, KEEPALIVE, KEEPALIVE, TimeUnit.MILLISECONDS);
        }    
        
        if (!isConnected())
        {
            throw new CameraException("Failed to detect camera.");
        }
    }
    
    /**
     * Gets the number of images currently being downloaded
     * @return 
     */
    public int getQueueRemaining()
    {
        return 
            ((ThreadPoolExecutor) this.capturePool).getQueue().size();
    }
        
    /**
     * Disconnects from the camera
     */
    public void disconnect()
    {
        if (isConnected())
        {
            CameraDevice c = cam;
            cam = null;            
            c.disconnect(DeviceInterface.WLAN);
        }
    }
    
    /**
     * Checks if the connection was established
     * @return 
     */
    public boolean isConnected()
    {
        return cam != null;
    }
    
    /**
     * Commands the camera to focus
     * @throws CameraException 
     */
    public void focus() throws CameraException
    {
        if (isConnected())
        {        
            try
            {
                Response r = getCam().focus();

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
        if (!isConnected())
        {
            throw new CameraException("Not connected");
        }
        
        CaptureMethod captureMethod = new CaptureMethod();
        Response response =
            getCam().getCaptureSettings(
                Arrays.asList((CaptureSetting) captureMethod));
        
        if (response.getResult() == Result.ERROR)
        {
            for (CameraEventListener e : getCam().getEventListeners())
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
                response = getCam().setCaptureSettings(
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
        if (isConnected())
        {
            cam.addEventListener(e);
        }
    }
    
    public void removeListener(CameraEventListener e)
    {
        if (isConnected())
        {
            cam.removeEventListener(e);
        }
    }
    
    // Camera state getters
    
    public String getCameraModel()
    {
        if (isConnected())
        {
            return this.cam.getModel();
        }      
        
        return "";
    }    
    
    public int getCameraBattery()
    {        
        if (isConnected())
        {
            return this.cam.getStatus().getBatteryLevel();
        }
        
        return 0;
    }
    
    public String getCameraSerial()
    {
        if (isConnected())
        {
            return this.cam.getSerialNumber();
        }      
        
        return "";
    }  
    
    public String getCameraManufacturer()
    {
        if (isConnected())
        {
            return this.cam.getManufacturer();
        }      
        
        return "";
    }  
    
    public String getCameraFirmware()
    {
        if (isConnected())
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
        if (isConnected())
        {
            return this.av.getAvailableSettings();
        }
        
        return new ArrayList<>();
    }
    
    public List<CaptureSetting> getAvailableTv()
    {
        if (isConnected())
        {
            return this.tv.getAvailableSettings();
        }
        
        return new ArrayList<>();
    }
    
    public List<CaptureSetting> getAvailableISO()
    {
        if (isConnected())
        {
            return this.iso.getAvailableSettings();
        }
        
        return new ArrayList<>();
    }
    
    public List<CaptureSetting> getAvailableEV()
    {
        if (isConnected())
        {
            return this.ev.getAvailableSettings();
        }
        
        return new ArrayList<>();
    }
    
    // Individual settings getters
    
    public CaptureSetting getTv()
    {
        return tv;
    }
    
    public CaptureSetting getEv()
    {
        return ev;
    }
    
    public CaptureSetting getAv()
    {
        return av;
    }
    
    public CaptureSetting getISO()
    {
        return iso;
    }
}
