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
    public boolean connectCamera(int index);
    
    public boolean disconnectCamera(int index);
        
    public boolean isConnected();
    
    public boolean connect();
                
    public void disconnect();
    
    public StartCaptureResponse capture(boolean focus);
    
    //public CaptureSetting getSetting(CaptureSetting s);
    
    public boolean getSettings(List<CaptureSetting> s);
    
    public boolean setSettings(List<CaptureSetting> s);
    
    //public boolean setSetting(CaptureSetting s);
    
    public int getNumEvents();
                
    public boolean processCallBacks(CameraDevice c, List<CameraEventListener> l);

    public CameraStatus getStatus();
    
    public List<CameraDevice> detectDevices();
    
    public boolean focus();
    
    public boolean isBusy();
    
    public boolean startLiveView(int port);
    
    public boolean stopLiveView();
}
