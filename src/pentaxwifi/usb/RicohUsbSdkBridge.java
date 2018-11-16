/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi.usb;

import com.ricoh.camera.sdk.wireless.api.CameraDevice;
import com.ricoh.camera.sdk.wireless.api.CameraDeviceDetector;
import com.ricoh.camera.sdk.wireless.api.Capture;
import com.ricoh.camera.sdk.wireless.api.CaptureState;
import com.ricoh.camera.sdk.wireless.api.response.Result;
import com.ricoh.camera.sdk.wireless.api.response.Error;
import com.ricoh.camera.sdk.wireless.api.response.ErrorCode;

import com.ricoh.camera.sdk.wireless.api.response.StartCaptureResponse;
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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Adam
 */
public final class RicohUsbSdkBridge implements USBInterface
{
    private Process conn;
    private PrintWriter p;
    private InputStream in;
    private boolean connected;
    
    // Commands
    public static final String GET_DEVICE_INFO = "0";
    public static final String CONNECT = "1";
    public static final String CAPTURE = "2";
    public static final String GET_APERTURE = "9";
    public static final String SET_APERTURE = "10";
    
    public static final String GET_EXPOSURE_COMPENSATION = "11";
    public static final String SET_EXPOSURE_COMPENSATION = "12";
        
    public static final String GET_ISO = "13";
    public static final String SET_ISO = "14";
    
    public static final String GET_SHUTTER_SPEED = "18";
    public static final String SET_SHUTTER_SPEED = "19";  
    
    
    public static final String DISCONNECT = "90";

    public static final String EXIT = "99";
    
    // Responses
    public static final String AWAITING_COMMAND = "MainMenu";
    
    
    @Override
    synchronized public boolean connect()
    {
        if (!connected)
        {
            try
            {
                conn = new ProcessBuilder("C:\\Users\\Adam\\Documents\\NetBeansProjects\\USBConn\\samples\\cli\\build\\x64\\Debug\\cli.exe").start();

                p = new PrintWriter(conn.getOutputStream());
                in = conn.getInputStream();

                connected = true;

                // Flush the stream
                //sendCommand("");
            }
            catch (IOException ex)
            {            
                System.out.println(ex.toString());
                connected = false;   
            }
        }
        
        return connected;
    }
    
    @Override
    synchronized public void disconnect()
    {
        if (connected)
        {
            connected = false;
            sendCommand(EXIT);
            conn.destroy();
        }
    }
    
    @Override
    synchronized public boolean isConnected()
    {
        return connected;
    }
    
    public RicohUsbSdkBridge()
    {
        connected = false;
        connect();
        
        /*p.write("1\n");
        p.flush();
        
        System.out.println(readUntilChar(i,'}'));
        
        p.write("2\n");
        p.flush();
        
        System.out.println(readUntilChar(i,'}'));*/
        
        /*conn.getOutputStream().write('9');
        conn.getOutputStream().write('9');
        conn.getOutputStream().write('\r');

        conn.getOutputStream().write('\n');
        conn.getOutputStream().flush();*/

    }
    
    synchronized private NetworkMessage sendCommand(String c)
    {
        if (!"".equals(c))
        {
            p.write(c);
            p.write("\n");
            p.flush();
        }
        
        NetworkMessage nm = new NetworkMessage(readUntilChar(in, NetworkMessage.getMessageDelim()));
        
        return nm;
    }
        
    @Override
    public List<CameraDevice> detectDevices()
    {
        List<CameraDevice> out = new LinkedList<>();
        
        if (isConnected())
        {
            NetworkMessage nm = sendCommand(GET_DEVICE_INFO);
            
            disconnect();
            
            if (nm.hasError())
            {
                System.err.println(nm.getError());
            }
            else if (!nm.hasKey("Detected Devices"))
            {
                System.err.println("detectDevices: could not parse response.");
            }
            else
            {
                int numDevices = Integer.parseInt(nm.getKey("Detected Devices"));
                
                for (int i = 0; i < numDevices; i++)
                {
                    out.add(
                        new USBCamera(
                                i,
                                this, 
                                nm.getKey("Manufacturer" + Integer.toString(i)),
                                nm.getKey("Model" + Integer.toString(i)),
                                nm.getKey("Serial Number" + Integer.toString(i)),
                                nm.getKey("Firmware" + Integer.toString(i))
                        )
                    );
                }
            }
        }
        
        return out;
    }
    
    @Override
    public CaptureSetting getSetting(CaptureSetting s)
    {        
        if (!isConnected())
        {
            System.err.println("Not connected");  
        
            return null;
        }
        
        final NetworkMessage nm;
        
        if ((new FNumber()).getName().equals(s.getName()))
        {
            nm = this.sendCommand(GET_APERTURE);
        }
        else if ((new ShutterSpeed()).getName().equals(s.getName()))
        {
            nm = this.sendCommand(GET_SHUTTER_SPEED);
        }
        else if ((new ISO()).getName().equals(s.getName()))
        {
            nm = this.sendCommand(GET_ISO);
        }
        else if ((new ExposureCompensation()).getName().equals(s.getName()))
        {
            nm = this.sendCommand(GET_EXPOSURE_COMPENSATION);
        }
        else
        {
            System.err.println("Unsupported setting supplied");
            
            return null;
        }
        
        if (nm.hasError())
        {
            System.err.println(nm.getError());

            return null;
        }
        else
        {
            USBCameraSetting f = USBCameraSetting.getUSBSetting(nm.getKey("current" + s.getName()), nm.getKey("available" + s.getName()), s.getClass());
            
            System.out.println(f.toStringDebug());
            
            return (CaptureSetting) f;
        }        
    }
    
    @Override
    public boolean setSetting(CaptureSetting s)
    {        
        if (!isConnected())
        {
            System.err.println("Not connected");  
        
            return false;
        }
        
        final NetworkMessage nm;
        
        if ((new FNumber()).getName().equals(s.getName()))
        {
            nm = this.sendCommand(SET_APERTURE + "\n" + s.getValue());
        }
        else if ((new ShutterSpeed()).getName().equals(s.getName()))
        {
            nm = this.sendCommand(SET_SHUTTER_SPEED + "\n" + s.getValue());
        }
        else if ((new ISO()).getName().equals(s.getName()))
        {
            nm = this.sendCommand(SET_ISO + "\n" + s.getValue());
        }
        else if ((new ExposureCompensation()).getName().equals(s.getName()))
        {
            nm = this.sendCommand(SET_EXPOSURE_COMPENSATION + "\n" + s.getValue());
        }
        else
        {
            System.err.println("Unsupported setting supplied");
            
            return false;
        }
        
        if (nm.hasError())
        {
            System.err.println(nm.getError());

            return false;
        }
        else
        {
            return true;
        }        
    }
    
    /**
     * Capture a photo
     * @param focus TODO - implement
     * @return 
     */
    @Override
    public StartCaptureResponse capture(boolean focus)
    {
        if (!isConnected())
        {
            System.err.println("Not connected");
            
            return new StartCaptureResponse(
                Result.ERROR,
                new Error(ErrorCode.NETWORK_ERROR, "Not connected"),
                null
            );
        }
        
        final NetworkMessage nm = this.sendCommand(CAPTURE);

        if (nm.hasError())
        {
            System.err.println(nm.getError());
            
            return new StartCaptureResponse(
                Result.ERROR,
                new Error(ErrorCode.UNKNOWN_VALUE, nm.getError()),
                null
            );
        }
        else
        {
            return new StartCaptureResponse(
                Result.OK,
                    new Capture() {
                        @Override
                        public String getId() {
                            return nm.getKey("CaptureID");
                        }

                        @Override
                        public CaptureState getState() {
                            return nm.getKey("CaptureState").equals("Complete") ? CaptureState.COMPLETE : CaptureState.EXECUTING; 
                        }

                        @Override
                        public CaptureMethod getMethod() {
                            return nm.getKey("CaptureMethod").equals("Movie") ? CaptureMethod.MOVIE : CaptureMethod.STILL_IMAGE;
                        }
                    }
            );
        }        
    }
    
    /**
     * Connect to a camera
     * TODO - the CLI currently always connects to the same index
     * @param index
     * @return 
     */
    @Override
    public boolean connectCamera(int index)
    {
        if (!isConnected())
        {
            System.err.println("Not connected");
            return false;
        }
        
        if (index != 0)
        {
            System.err.println("Multiple cameras are not supported.");
            return false;
        }
        else
        {       
            NetworkMessage nm = this.sendCommand(CONNECT);

            if (nm.hasError())
            {
                System.err.println(nm.getError());
                return false;
            }
            else
            {
                return true;
            }
        }
    }
    
    /**
     * Disconnect from a camera
     * TODO - the CLI currently always disconnects all indices
     * @param index
     * @return 
     */
    @Override
    public boolean disconnectCamera(int index)
    {
        if (!isConnected())
        {
            System.err.println("Not connected");
            return false;
        }
        
        if (index != 0)
        {
            System.err.println("Multiple cameras are not supported.");
            return false;
        }
        else
        {       
            NetworkMessage nm = this.sendCommand(DISCONNECT);

            if (nm.hasError())
            {
                System.err.println(nm.getError());
                return false;
            }
            else
            {
                return true;
            }
        }
    }
      
    // TODO check for if camera was DCd
     
    public static void main(String args[])
    { 
        RicohUsbSdkBridge br = new RicohUsbSdkBridge();
        
        for (CameraDevice d : br.detectDevices())
        {
            d.connect(null);
            
            List<CaptureSetting> l = Arrays.asList(new ShutterSpeed(), new ExposureCompensation(), new FNumber(), new ISO());
            
            d.getCaptureSettings(l);
            
            for (CaptureSetting s : l)
            {
                System.out.println(s);
            }
            
            d.setCaptureSettings(Arrays.asList(FNumber.F11));
            d.setCaptureSettings(Arrays.asList(ShutterSpeed.SS1_100));
            d.setCaptureSettings(Arrays.asList(ISO.ISO200));
            d.setCaptureSettings(Arrays.asList(ExposureCompensation.EC1_0));

            //d.startCapture(true);
            d.disconnect(null);
        }
        
        br.disconnect();
    }
    
    public static String readUntilChar(InputStream stream, char target)
    {
        StringBuilder sb = new StringBuilder();

        try
        {
            BufferedReader buffer = new BufferedReader(new InputStreamReader(stream));

            int r;
            while ((r = buffer.read()) != -1)
            {
                char c = (char) r;

                sb.append(c);

                if (c == target) break;
            }
            
            stream.mark(sb.length());   
        }
        catch(IOException e)
        {
            // Error handling
        }

        // TODO - handle empty string
        
        return sb.toString();
    }
}
