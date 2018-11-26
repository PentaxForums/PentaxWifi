/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pfpentaxtether.usb;

import com.ricoh.camera.sdk.wireless.api.CameraDevice;
import com.ricoh.camera.sdk.wireless.api.CameraEventListener;
import com.ricoh.camera.sdk.wireless.api.CameraStatus;
import com.ricoh.camera.sdk.wireless.api.response.Response;
import com.ricoh.camera.sdk.wireless.api.response.StartCaptureResponse;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureSetting;
import java.util.List;

/**
 *
 * @author Adam
 */
interface USBInterface
{
    /**
     * Connects to the camera based by its index in the detected camera list (usually 0)
     * @param index
     * @return 
     */
    public boolean connectCamera(int index);
    
    /**
     * Disconnects from the camera
     * @param index
     * @return 
     */
    public boolean disconnectCamera(int index);
        
    /**
     * Returns true if the connection is active
     * @return 
     */
    public boolean isConnected();
    
    /**
     * Initializes the USB driver
     * @return 
     */
    public boolean connect();
                
    /**
     * Shuts down the USB driver
     */
    public void disconnect();
    
    /**
     * Captures a photo
     * @param focus
     * @return 
     */
    public StartCaptureResponse capture(boolean focus);
        
    /**
     * Gets the settings specified by the CaptureSetting types within list s
     * - empties s and populates it with the camera's setting values for the
     * requested settings
     * @param s
     * @return 
     */
    public boolean getSettings(List<CaptureSetting> s);
    
    /**
     * Transmits the list of desired settings to the camera
     * @param s
     * @return 
     */
    public boolean setSettings(List<CaptureSetting> s);
    
    //public CaptureSetting getSetting(CaptureSetting s);
    
    //public boolean setSetting(CaptureSetting s);
               
    /**
     * Initializes a callback processor which will fire events on all listeners in list l
     * whenever camera events occur
     * @param c
     * @param l
     * @return 
     */
    public boolean processCallBacks(CameraDevice c, List<CameraEventListener> l);

    /**
     * Returns the camera's current status
     * @return 
     */
    public CameraStatus getStatus();
    
    /**
     * Returns a list of detected cameras
     * @return 
     */
    public List<CameraDevice> detectDevices();
    
    /**
     * Focuses the camera
     * @return 
     */
    public boolean focus();
    
    /**
     * Focuses the camera with a specific adjustment
     * @param adjustment
     * @return 
     */
    public boolean focus(int adjustment);
    
    /**
     * Returns true if any USB commands are pending
     * @return 
     */
    public boolean isBusy();
    
    /**
     * Starts live view, with communication on the given port, 
     *  which can be assumed to be open for TCP connections
     * 50004 bytes must be sent for each camera frame 4 (size) + 50000 (data) 
     * @param port
     * @return 
     */
    public boolean startLiveView(int port);
    
    /**
     * Stops live view
     * @return 
     */
    public boolean stopLiveView();
}
