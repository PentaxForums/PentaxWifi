/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pfpentaxtether.usb;

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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import static pfpentaxtether.CameraConnectionModel.KEEPALIVE;
import pfpentaxtether.CameraException;

/**
 *
 * @author Adam
 */
public final class USBCamera implements CameraDevice
{
    private USBInterface iface;
    private String fw;
    private String model;
    private String serial;
    private String manu;
    private int index;
    private boolean connected;
    private List<CameraEventListener> listeners;
    private ScheduledExecutorService exec;
    private static final int SOCKET_BUFFER_SIZE = 50000;
    private ServerSocket sock;
    ExecutorService liveViewExec;
    
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
        
        this.addEventListener(new CameraEventListener()
        {
            @Override
            public void deviceDisconnected(CameraDevice sender)
            {   
                connected = false;
            }
        });
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
        if (!this.iface.isConnected())
        {
            if (!this.iface.connect())
            {
                return new Response(
                    Result.ERROR,
                    new Error(ErrorCode.NETWORK_ERROR, "Failed to connect")
                ); 
            }
            
        }
        
        if (this.iface.connectCamera(index) && this.iface.processCallBacks(this, listeners))
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
    public Response startLiveView()
    {            
        try
        {
            final ServerSocket serverSocket = new ServerSocket(0);
            sock = serverSocket;
                        
            if (this.iface.startLiveView(serverSocket.getLocalPort()))
            {
                if (liveViewExec != null)
                {
                    liveViewExec.shutdownNow();
                }
                
                liveViewExec = Executors.newSingleThreadExecutor();
                liveViewExec.submit(
                
                    new Thread(() -> {
                        try 
                        {   
                            Socket socket = serverSocket.accept();
                            InputStream inputStream = socket.getInputStream();

                            while (true)
                            {                        
                                // Read a fixed amount of data
                                byte[] sizeAr = new byte[4];
                                inputStream.read(sizeAr);
                                byte[] imageAr = new byte[SOCKET_BUFFER_SIZE];
                                inputStream.read(imageAr);

                                try
                                {
                                    int size = ByteBuffer.wrap(sizeAr).asIntBuffer().get();

                                    final byte[] imageAr2 = Arrays.copyOfRange(imageAr, 0, size);


                                    if (imageAr[size - 1] == -39)
                                    {                        
                                        new Thread(() -> {
                                            listeners.forEach((CameraEventListener cel) ->
                                            { 
                                                cel.liveViewFrameUpdated(this, imageAr2);
                                            });
                                        }).start();
                                    }
                                }
                                catch(java.lang.ArrayIndexOutOfBoundsException e)
                                {

                                }
                                catch(java.lang.IllegalArgumentException e)
                                {

                                }
                            }
                        }
                        catch (Exception ex)
                        {    
                            try
                            {
                                serverSocket.close();
                            } 
                            catch (IOException ex1)
                            {

                            }
                        }                
                    })
                );
                
                return new Response(
                    Result.OK
                );
            }
            else
            {
                serverSocket.close();
                
                return new Response(
                    Result.ERROR,
                    new Error(ErrorCode.NETWORK_ERROR, "Failed to start live view")
                );
            } 
        }
        catch (IOException ex)
        {
            return new Response(
                Result.ERROR,
                new Error(ErrorCode.NETWORK_ERROR, "Failed to start live view due to socket error.")
            );
        }
    }

    @Override
    public Response stopLiveView() {
        
        try 
        {
            if (sock != null)
            {
                sock.close();
            }
        } catch (IOException ex) {}
        
        if (this.iface.stopLiveView())
        {
            return new Response(
                Result.OK
            );
        }
        else
        {
            return new Response(
                Result.ERROR,
                new Error(ErrorCode.NETWORK_ERROR, "Failed to stop live view")
            );
        }    
    }

    @Override
    public Response focus() {
        if (this.iface.focus())
        {
            return new Response(
                Result.OK
            );
        }
        else
        {
            return new Response(
                Result.ERROR,
                new Error(ErrorCode.NETWORK_ERROR, "Failed to focus")
            );
        }    
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
        boolean status = this.iface.getSettings(list);
        
        if (status)
        {
            return new Response(
                Result.OK
            ); 
        }
        
        return new Response(
            Result.ERROR,
            new Error(ErrorCode.INVALID_ARGUMENT, "Error getting settings")
        );       
    }

    @Override
    public Response setCaptureSettings(List<CaptureSetting> list)
    {
        boolean status = this.iface.setSettings(list);
        
        if (status)
        {
            return new Response(
                Result.OK
            ); 
        }
        
        return new Response(
            Result.ERROR,
            new Error(ErrorCode.INVALID_ARGUMENT, "Error setting settings")
        );       
    }
    
    /*@Override
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
    }*/

    @Override
    public Response getCameraDeviceSettings(List<CameraDeviceSetting> list) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Response setCameraDeviceSettings(List<CameraDeviceSetting> list) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }    
}
