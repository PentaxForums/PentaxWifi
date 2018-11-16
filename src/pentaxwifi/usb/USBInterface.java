/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi.usb;

import com.ricoh.camera.sdk.wireless.api.CameraDevice;
import com.ricoh.camera.sdk.wireless.api.response.StartCaptureResponse;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureSetting;
import java.util.List;

/**
 *
 * @author Adam
 */
interface USBInterface
{
    public boolean connectCamera(int index);
    
    public boolean disconnectCamera(int index);
    
    public List<CameraDevice> detectDevices();
    
    public boolean isConnected();
    
    public boolean connect();
                
    public void disconnect();
    
    public StartCaptureResponse capture(boolean focus);
    
    public CaptureSetting getSetting(CaptureSetting s);
    
    public boolean setSetting(CaptureSetting s);
}
