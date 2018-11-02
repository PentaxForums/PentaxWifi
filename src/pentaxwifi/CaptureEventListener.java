/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi;

import com.ricoh.camera.sdk.wireless.api.CameraImage;
import java.io.File;

/**
 * Interface for downloaded file listener
 * @author Adam
 */
public interface CaptureEventListener
{  
    /**
     * Fires when an image capture ends.  
     * @param captureOk status of the capture
     * @param remaining remaining captures, if batch processing is on
     */
    public void imageCaptureComplete(boolean captureOk, int remaining);
    
    /**
     * Camera has disconnected
     */
    public void disconnect();
    
    /**
     * File has completed downloading
     * @param i
     * @param f
     * @param isThumbnail 
     */
    public void imageDownloaded(CameraImage i, File f, boolean isThumbnail);
    
    /**
     * File has been stored on camera (use to start downloading)
     * @param i 
     */
    public void imageStored(CameraImage i);
}
