/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pfpentaxtether.usb;

import com.ricoh.camera.sdk.wireless.api.CameraDevice;
import com.ricoh.camera.sdk.wireless.api.CameraDeviceDetector;
import com.ricoh.camera.sdk.wireless.api.CameraEventListener;
import com.ricoh.camera.sdk.wireless.api.CameraImage;
import com.ricoh.camera.sdk.wireless.api.CameraStatus;
import com.ricoh.camera.sdk.wireless.api.CameraStorage;
import com.ricoh.camera.sdk.wireless.api.Capture;
import com.ricoh.camera.sdk.wireless.api.CaptureState;
import com.ricoh.camera.sdk.wireless.api.ImageFormat;
import com.ricoh.camera.sdk.wireless.api.ImageType;
import com.ricoh.camera.sdk.wireless.api.response.Result;
import com.ricoh.camera.sdk.wireless.api.response.Error;
import com.ricoh.camera.sdk.wireless.api.response.ErrorCode;
import com.ricoh.camera.sdk.wireless.api.response.Response;

import com.ricoh.camera.sdk.wireless.api.response.StartCaptureResponse;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureMethod;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureSetting;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ExposureCompensation;
import com.ricoh.camera.sdk.wireless.api.setting.capture.FNumber;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ISO;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ShutterSpeed;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Date; 
import java.text.SimpleDateFormat; 

/**
 *
 * @author Adam
 */
public final class PFRicohUSBSDKBridge implements USBInterface
{
    private Process conn;
    private PrintWriter p;
    private InputStream in;
    private boolean connected;
    private boolean waiting;
    private CameraStatus lastStatus;
        
    // Commands
    public static final String GET_DEVICE_INFO = "0";
    public static final String CONNECT = "1";
    public static final String CAPTURE = "2";
    
    public static final String GET_STATUS = "5";
    
    public static final String GET_APERTURE = "9";
    public static final String SET_APERTURE = "10";
    
    public static final String GET_EXPOSURE_COMPENSATION = "11";
    public static final String SET_EXPOSURE_COMPENSATION = "12";
        
    public static final String GET_ISO = "13";
    public static final String SET_ISO = "14";
    
    public static final String GET_SHUTTER_SPEED = "18";
    public static final String SET_SHUTTER_SPEED = "19";  
    
    // TODO
    public static final String GET_CAPTURE_METHOD = "38";
    
    public static final String GET_NUM_EVENTS = "1000";
    public static final String GET_NEXT_EVENT = "1001";

    public static final String GET_THUMBNAIL = "2000";
    public static final String GET_IMAGE = "2001";
    
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
            conn.destroyForcibly();
        }
    }
    
    @Override
    synchronized public boolean isConnected()
    {
        return connected;
    }
    
    public PFRicohUSBSDKBridge()
    {
        connected = false;
        waiting = false;
        //connect();
    }
    
    synchronized public USBMessage sendCommand(String c)
    {
        waiting = true;
        
        if (!"".equals(c))
        {
            p.write(c);
            p.write("\n");
            p.flush();
        }
        
        USBMessage nm = new USBMessage(readUntilChar(in, USBMessage.getMessageDelim()));
        
        waiting = false;
        
        return nm;
    }
    
    @Override
    synchronized public CameraStatus getStatus()
    {
        // Return a cached response if we're waiting for another call
        if (!waiting || lastStatus == null)
        {      
            USBMessage nm = this.sendCommand(GET_STATUS);

            lastStatus =  new CameraStatus() {
                @Override
                public int getBatteryLevel() {
                    return Integer.parseInt(nm.getKey("BatteryLevel"));
                }

                @Override
                public Capture getCurrentCapture() {

                    return new Capture() {
                        @Override
                        public String getId() {
                            return "0";
                        }

                        @Override
                        public CaptureState getState() {
                            return CaptureState.COMPLETE;
                        }

                        @Override
                        public CaptureMethod getMethod() {
                            return CaptureMethod.STILL_IMAGE;
                        }
                    };                
                }
            };    
        }
        
        return lastStatus;
    }
    
    @Override
    public List<CameraDevice> detectDevices()
    {
        List<CameraDevice> out = new LinkedList<>();
        
        if (!isConnected())
        {
            connect();
        }
        
        if (isConnected())
        {
            USBMessage nm = sendCommand(GET_DEVICE_INFO);
            
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
                                nm.getKey("Firmware Version" + Integer.toString(i))
                        )
                    );
                }
            }
        }
        
        return out;
    }
    
    @Override 
    public int getNumEvents()
    {
        USBMessage nm = this.sendCommand(GET_NUM_EVENTS);
        
        if (!nm.hasError())
        {
            return Integer.parseInt(nm.getKey("numEvents"));
        }
        
        return 0;
    }
    
    @Override
    public void processCallBacks(CameraDevice c, List<CameraEventListener> l)
    {
        USBMessage nm = this.sendCommand(GET_NEXT_EVENT);
        
        if (!nm.hasError())
        {
            String eventName = nm.getKey("Event");
            
            l.forEach((CameraEventListener cel) ->
            {            
                if (null != eventName)
                {
                    switch (eventName)
                    {
                        case "deviceDisconnected":
                            
                            (new Thread (() -> {
                                cel.deviceDisconnected(c);
                            })).start();
                            break;
                            
                        // TODO - maybe fire a callback for this
                        case "captureSettingsChanged":
                            break;
                            
                        case "imageStored":
                            
                            final PFRicohUSBSDKBridge br = this;
                            
                            (new Thread (() -> {
                                cel.imageStored(c, new CameraImage() {
                                    @Override
                                    public String getName()
                                    {
                                       return nm.getKey("Name");
                                    }

                                    @Override
                                    public ImageType getType()
                                    {
                                        return "StillImage".equals(nm.getKey("Type")) ? ImageType.STILL_IMAGE : ImageType.MOVIE;
                                    }

                                    @Override
                                    public ImageFormat getFormat()
                                    {    
                                        String format = nm.getKey("Format");
                                        
                                        if ("DNG".equals(format))
                                        {
                                            return ImageFormat.DNG;
                                        }
                                        else if ("JPEG".equals(format))
                                        {
                                            return ImageFormat.JPEG;
                                        }
                                        else if ("AVI".equals(format))
                                        {
                                            return ImageFormat.AVI;
                                        }
                                        else if ("MP4".equals(format))
                                        {
                                            return ImageFormat.MP4;
                                        }
                                        else if ("PEF".equals(format))
                                        {
                                            return ImageFormat.PEF;
                                        }
                                        else if ("DPOF".equals(format))
                                        {
                                            return ImageFormat.DPOF;
                                        }
                                        else if ("TIFF".equals(format))
                                        {
                                            return ImageFormat.TIFF;
                                        }
                                        else
                                        {
                                            return ImageFormat.UNKNOWN;
                                        }                                        
                                    }

                                    @Override
                                    public Date getDateTime()
                                    {
                                        Date d = new Date();
                                        
                                        d.setTime(Integer.parseInt(nm.getKey("Date")) * 1000);
                                       
                                        return d;
                                    }

                                    @Override
                                    public boolean hasThumbnail()
                                    {
                                        return "1".equals(nm.getKey("HasThumbnail"));  
                                    }

                                    @Override
                                    public CameraStorage getStorage() {
                                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                                    }

                                    @Override
                                    public Response getData(OutputStream out) throws IOException
                                    {   
                                        USBMessage nm2 = br.sendCommand(GET_IMAGE + "\n" + nm.getKey("ID"));
                                        
                                        if (!nm2.hasError())
                                        {
                                            String filePath = nm2.getKey("filePath");
                                            
                                            File f = new File(filePath);
                                            Files.copy(f.toPath(), out);
                                            f.delete();
    
                                            return new Response(Result.OK);
                                        }
                                        else
                                        {
                                             return new Response(
                                                Result.ERROR,
                                                new Error(ErrorCode.IMAGE_NOT_FOUND, "Image download error.")
                                            );
                                        }                                        
                                    }

                                    @Override
                                    public Response getThumbnail(OutputStream out) throws IOException
                                    {
                                        USBMessage nm2 = br.sendCommand(GET_THUMBNAIL + "\n" + nm.getKey("ID"));
                                        
                                        if (!nm2.hasError())
                                        {
                                            String filePath = nm2.getKey("filePath");
                                            
                                            File f = new File(filePath);
                                            Files.copy(f.toPath(), out);
                                            f.delete();
    
                                            return new Response(Result.OK);
                                        }
                                        else
                                        {
                                             return new Response(
                                                Result.ERROR,
                                                new Error(ErrorCode.IMAGE_NOT_FOUND, "Thumbnail download error.")
                                            );
                                        }                                        
                                    }
                                });
                            })).start();
                            break;
                            
                        case "captureComplete":
                            
                            (new Thread (() -> {
                                cel.captureComplete(c, new Capture() {
                                    @Override
                                    public String getId()
                                    {    
                                        return nm.getKey("ID");                                        
                                    }

                                    @Override
                                    public CaptureState getState()
                                    {    
                                        if ("Complete".equals(nm.getKey("State")))
                                        {
                                            return CaptureState.COMPLETE;
                                        }
                                        
                                        return CaptureState.EXECUTING;                                        
                                    }

                                    @Override
                                    public CaptureMethod getMethod()
                                    {    
                                        if ("Movie".equals(nm.getKey("Method")))
                                        {
                                            return CaptureMethod.MOVIE;
                                        }
                                        
                                        return CaptureMethod.STILL_IMAGE;                                        
                                    }
                                }
                                );
                            })).start();
                            break;
                            
                        default:
                            break;
                    }
                }
            });
        }
    }
    
    @Override
    public CaptureSetting getSetting(CaptureSetting s)
    {        
        if (!isConnected())
        {
            System.err.println("Not connected");  
        
            return null;
        }
        
        final USBMessage nm;
        
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
        else if ((new CaptureMethod()).getName().equals(s.getName()))
        {
            nm = this.sendCommand(GET_CAPTURE_METHOD);
        }
        else
        {
            System.err.println("Unsupported setting supplied " + s);
            
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
        
        final USBMessage nm;
        
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
        
        final USBMessage nm = this.sendCommand(CAPTURE);

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
            USBMessage nm = this.sendCommand(CONNECT);

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
            USBMessage nm = this.sendCommand(DISCONNECT);

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
        PFRicohUSBSDKBridge br = new PFRicohUSBSDKBridge();
        
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
