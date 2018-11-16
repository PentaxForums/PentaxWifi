/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi.usb;

import com.ricoh.camera.sdk.wireless.api.CameraDevice;
import com.ricoh.camera.sdk.wireless.api.CameraEventListener;
import com.ricoh.camera.sdk.wireless.api.CameraImage;
import com.ricoh.camera.sdk.wireless.api.CameraStatus;
import com.ricoh.camera.sdk.wireless.api.CameraStorage;
import com.ricoh.camera.sdk.wireless.api.Capture;
import com.ricoh.camera.sdk.wireless.api.CaptureState;
import com.ricoh.camera.sdk.wireless.api.DeviceInterface;
import com.ricoh.camera.sdk.wireless.api.response.Response;
import com.ricoh.camera.sdk.wireless.api.response.Result;
import com.ricoh.camera.sdk.wireless.api.response.Error;
import com.ricoh.camera.sdk.wireless.api.response.ErrorCode;

import com.ricoh.camera.sdk.wireless.api.response.StartCaptureResponse;
import com.ricoh.camera.sdk.wireless.api.setting.camera.CameraDeviceSetting;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureMethod;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureSetting;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ExposureCompensation;
import com.ricoh.camera.sdk.wireless.api.setting.capture.FNumber;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ISO;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ShutterSpeed;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import static pentaxwifi.CameraConnectionModel.KEEPALIVE;
import pentaxwifi.CameraException;

/**
 *
 * @author Adam
 */
public class USBCamera implements CameraDevice
{
    private USBInterface iface;
    private String fw;
    private String model;
    private String serial;
    private String manu;
    private int index;
    private boolean connected;
    private List<CameraEventListener> listeners;
    private static final int POLL_FOR_EVENTS = 1000;
    
    /**
     *
     * @param index
     * @param iface
     * @param manu
     * @param model
     * @param serial
     * @param fw
     */
    public USBCamera(int index, USBInterface iface, String manu, String model, String serial, String fw)
    {
        this.index = index;
        this.iface = iface;
        this.manu = manu;
        this.model = model;
        this.serial = serial;
        this.fw = fw;
        this.connected = false;
        this.listeners = new LinkedList<>();
        
        // Start event polling
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(() -> {

                if (this.iface.isConnected())
                {
                    this.iface.processCallBacks(this, listeners);
                }
        }, 0, POLL_FOR_EVENTS, TimeUnit.MILLISECONDS);
    }
        
    @Override
    public String getManufacturer()
    {
        return this.manu;
    }

    @Override
    public String getModel()
    {
        return this.model;
    }

    @Override
    public String getFirmwareVersion()
    {
        return this.fw;
    }

    @Override
    public String getSerialNumber()
    {
        return this.serial;
    }

    @Override
    public void addEventListener(CameraEventListener cl) {
        this.listeners.add(cl);
    }

    @Override
    public void removeEventListener(CameraEventListener cl) {
        this.listeners.remove(cl);
    }

    @Override
    public CameraEventListener[] getEventListeners() {
        return this.listeners.toArray(new CameraEventListener[this.listeners.size()]);
    }

    @Override
    public List<CameraStorage> getStorages() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public CameraStatus getStatus()
    {
        return this.iface.getStatus();
    }

    @Override
    public List<CameraImage> getImages() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Response updateImages() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    synchronized public Response connect(DeviceInterface di)
    {
        if (this.iface.isConnected())
        {
            return new Response(
                Result.ERROR,
                new Error(ErrorCode.NETWORK_ERROR, "Another camera is already connected.  Disconnect it first.")
            );
        }
        
        if (this.iface.connect())
        {
            if (this.iface.connectCamera(index))
            {
                connected = true;
                return new Response(
                    Result.OK
                );
            }
            else
            {
                return new Response(
                    Result.ERROR,
                    new Error(ErrorCode.NETWORK_ERROR, "Failed to connect")
                );
            }    
        }
        else
        {
            return new Response(
                Result.ERROR,
                new Error(ErrorCode.NETWORK_ERROR, "Failed to connect.")
            );    
        }
    }

    @Override
    synchronized public Response disconnect(DeviceInterface di)
    {    
        connected = false;
        
        if (!this.iface.isConnected())
        {
            return new Response(
                Result.ERROR,
                new Error(ErrorCode.NETWORK_ERROR, "USB not connected.")
            );
        }
        else if (!this.connected)
        {
            return new Response(
                Result.ERROR,
                new Error(ErrorCode.DEVICE_NOT_FOUND, "Camera not connected.")
            );
        }
        else
        {
            if (this.iface.disconnectCamera(index))
            {
                connected = false;
                return new Response(
                    Result.OK
                );
            }
            else
            {
                return new Response(
                    Result.ERROR,
                    new Error(ErrorCode.NETWORK_ERROR, "Failed to disconnect")
                );
            }    
        }
    }

    @Override
    synchronized public boolean isConnected(DeviceInterface di) {
        return connected;
    }

    @Override
    public Response startLiveView() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Response stopLiveView() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Response focus() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public StartCaptureResponse startCapture(boolean focus)
    {  
        return this.iface.capture(focus);
    }

    @Override
    public Response stopCapture() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Response getCaptureSettings(List<CaptureSetting> list)
    {
        for (int i = 0; i < list.size(); i++)
        {
            CaptureSetting s = list.get(i);
            
            if (s == null)
            {
                return new Response(
                    Result.ERROR,
                    new Error(ErrorCode.INVALID_ARGUMENT, "Null value passed to getCaptureSettings")
                );
            }
            
            CaptureSetting newSetting = this.iface.getSetting(s);
            
            if (newSetting != null)
            {
                list.set(i, newSetting);
            }
            else
            {
                return new Response(
                    Result.ERROR,
                    new Error(ErrorCode.NETWORK_ERROR, "Failed to get " + s.getName() + " value from camera")
                );    
            }
        }
        
        return new Response(
            Result.OK
        );        
    }

    @Override
    public Response setCaptureSettings(List<CaptureSetting> list) {
        for (int i = 0; i < list.size(); i++)
        {
            CaptureSetting s = list.get(i);
            
            if (s == null)
            {
                return new Response(
                    Result.ERROR,
                    new Error(ErrorCode.INVALID_ARGUMENT, "Null value passed to setCaptureSettings")
                );
            }
            
            if (!this.iface.setSetting(s))
            {
                return new Response(
                    Result.ERROR,
                    new Error(ErrorCode.INVALID_SETTING, "Failed to set " + s.toString())
                );    
            }
        }
        
        return new Response(
            Result.OK
        );
    }

    @Override
    public Response getCameraDeviceSettings(List<CameraDeviceSetting> list) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Response setCameraDeviceSettings(List<CameraDeviceSetting> list) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }    
}
