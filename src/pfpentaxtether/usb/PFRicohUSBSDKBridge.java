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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
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
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
    private CameraStatus lastStatus;
    
    private ServerSocket sock;
    private ExecutorService exec;
    
    private PriorityQueue<String> q;
    
    // OS detection
    public static final String OS = System.getProperty("os.name").toLowerCase();
    public static final Boolean IS_MAC = OS.contains("mac");
    public static final Boolean IS_WIN = OS.contains("win");  
    public static final Boolean IS_UNIX = (OS.contains("aix") || OS.contains("nix") || OS.contains("nux") || OS.contains("droid")) ;
    public static final Boolean IS_64_BIT = System.getProperty("os.arch").contains("64");
    
    // Commands
    public static final String GET_DEVICE_INFO = "0";
    public static final String CONNECT = "1";
    public static final String CAPTURE = "2";
    public static final String CAPTURE_WITH_FOCUS = "23";
    
    public static final String GET_STATUS = "5";
    
    public static final String GET_APERTURE = "9";
    public static final String SET_APERTURE = "10";
    
    public static final String GET_EXPOSURE_COMPENSATION = "11";
    public static final String SET_EXPOSURE_COMPENSATION = "12";
        
    public static final String GET_ISO = "13";
    public static final String SET_ISO = "14";
    
    public static final String GET_SHUTTER_SPEED = "18";
    public static final String SET_SHUTTER_SPEED = "19";  
    
    public static final String FOCUS = "22"; 
    public static final String FOCUS_WITH_SETTING = "221"; 
    
    public static final String GET_ALL_SETTINGS = "3000";
    public static final String SET_ALL_SETTINGS = "3001";
    
    public static final String START_LV = "4000";
    public static final String STOP_LV = "4001";

    public static final String GET_CAPTURE_METHOD = "38";
    
    public static final String GET_NUM_EVENTS = "1000";
    public static final String GET_NEXT_EVENT = "1001";

    public static final String GET_THUMBNAIL = "2000";
    public static final String GET_IMAGE = "2001";
    
    public static final String DISCONNECT = "90";
    
    public static final String START_EVENTS = "5000";

    public static final String EXIT = "99";
        
    private static final int WAIT_INTERVAL = 10;
    
      
    public PFRicohUSBSDKBridge()
    {
        connected = false;
        
        q = new PriorityQueue<>((String s1, String s2) -> {
            if (s1.equals(s2))
            {
                return 0;
            }
            
            // Equal priority to all but these two
            if (!s1.equals(GET_NEXT_EVENT) && !s2.equals(GET_STATUS))
            {
                return 0;
            }
            
            // Next event goes ahead of status
            if (s1.equals(GET_NEXT_EVENT) && s2.equals(GET_STATUS))
            {
                return 1;
            }
            
            // These two go behind all others
            if (s1.equals(GET_NEXT_EVENT) || s1.equals(GET_STATUS))
            {
                return -1;
            }
            
            // All others go ahead
            return 1;
        });
        //connect();
    }
    
    
    @Override
    synchronized public boolean connect()
    {
        if (!IS_WIN && !IS_64_BIT)
        {
            System.err.println("This OS does not support USB mode yet. Supported platforms: 32/64-bit Windows, 64-bit Mac and Linux");
            return false;
        }
        
        if (!connected)
        {
            try
            {
                String cwd = new File(PFRicohUSBSDKBridge.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();

                if (IS_WIN)
                {
                    if (IS_64_BIT)
                    {
                        conn = new ProcessBuilder(cwd + "/bin/usb_interface_win64.exe").start();
                    }
                    else
                    {
                        conn = new ProcessBuilder(cwd + "/bin/win32/usb_interface_win32.exe").start();
                    }
                }
                else
                {
                    ProcessBuilder pb;

                    if (IS_MAC)
                    {
                        pb = new ProcessBuilder(cwd + "/bin/usb_interface_macos64");
                    }
                    else
                    {
                        pb = new ProcessBuilder(cwd + "/bin/usb_interface_linux64");
                    }

                    Map<String, String> env = pb.environment();
                    env.put("LD_LIBRARY_PATH", cwd + "/bin/");
                    conn = pb.start();
                }

                p = new PrintWriter(conn.getOutputStream());
                in = conn.getInputStream();

                if (!conn.isAlive())
                {
                    System.out.println("Error: USB driver failed to start.");
                    return false;
                }

                connected = true;
            }
            catch (URISyntaxException ex)
            {
                return false;
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
            sendCommand(DISCONNECT);
            sendCommand(EXIT);
            conn.destroyForcibly();
        }
    }
    
    @Override
    synchronized public boolean isConnected()
    {
        return connected;
    }
  
    
    public USBMessage sendCommand(String c)
    {
        if (!conn.isAlive())
        {
            System.err.println("USB driver has crashed.  Exiting.");
            System.exit(0);
        }
        
        this.q.add(c);
        
        //System.out.println("Add " + c + " (" + this.q.size() + ")");
        //System.out.println("Top: " + this.q.peek());

        while(!this.q.peek().equals(c))
        {
            try
            {
                Thread.sleep(WAIT_INTERVAL);
            }
            catch (InterruptedException ex)
            {
                Logger.getLogger(PFRicohUSBSDKBridge.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        //System.out.println("----> Processing " + c);
        
        if (!"".equals(c))
        {
            p.write(c);
            p.write("\n");
            p.flush();
        }
        
        USBMessage nm = new USBMessage(readUntilChar(in, USBMessage.getMessageDelim()));
        
        //System.out.println("----< Finished " + c);
        
        if (!nm.getType().equals("Status"))
        {
            System.out.println(nm.toString());
        }
        
        this.q.remove(c);
                
        return nm;
    }
    
    @Override
    synchronized public CameraStatus getStatus()
    {
        // Return a cached response if we're waiting for another call
        if (q.isEmpty() || lastStatus == null)
        {      
            USBMessage nm = this.sendCommand(GET_STATUS);

            lastStatus = new CameraStatus()
            {
                @Override
                public int getBatteryLevel()
                {
                    return Integer.parseInt(nm.getKey("BatteryLevel"));
                }

                @Override
                public Capture getCurrentCapture()
                {
                    return new Capture() {
                        @Override
                        public String getId()
                        {
                            if (nm.hasKey("ID"))
                            {
                                return nm.getKey("ID");
                            }
                                   
                            return "0";
                        }

                        @Override
                        public CaptureState getState() {
                            
                            if (nm.getKey("Status").equals("Complete"))
                            {
                                return CaptureState.COMPLETE;
                            }
                            else
                            {
                                return CaptureState.EXECUTING;
                            }
                        }

                        @Override
                        public CaptureMethod getMethod()
                        {
                            if (nm.hasKey("Method"))
                            {
                                if (nm.getKey("Method").equals(CaptureMethod.STILL_IMAGE.toString()))
                                {
                                    return CaptureMethod.STILL_IMAGE;
                                }
                                else
                                {
                                    return CaptureMethod.MOVIE;
                                }
                            }
                            
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
    public boolean processCallBacks(CameraDevice c, List<CameraEventListener> l)
    {        
        try
        {
            if (sock != null)
            {
                sock.close();
            }
            
            final ServerSocket serverSocket = new ServerSocket(0);
            sock = serverSocket;
        
            USBMessage resp = this.sendCommand(START_EVENTS + "\n" + serverSocket.getLocalPort());
                        
            if (!resp.hasError())
            {
                if (exec != null)
                {
                    exec.shutdownNow();
                }
                
                exec = Executors.newSingleThreadExecutor();
                exec.submit(
                
                    new Thread(() -> {

                        try 
                        {   
                            Socket socket = serverSocket.accept();
                            InputStream inputStream = socket.getInputStream();

                            while (true)
                            {      
                                USBMessage nm = new USBMessage(readUntilChar(inputStream, USBMessage.getMessageDelim()));

                                // System.out.println("EVENT " + serverSocket.getLocalPort() + " --->" + nm);

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
                        catch (IOException e)
                        {
                            disconnect();
                        }
                    })
                );
                
                return true;
            }
        }
        catch (IOException e)
        {
            
        }   
        
        return false;
    }
    
    /**
     *
     * @param list
     * @return
     */
    @Override
    public boolean getSettings(List<CaptureSetting> list)
    {
        if (!isConnected())
        {
            System.err.println("Not connected");  
        
            return false;
        }
        
        USBMessage nm = this.sendCommand(GET_ALL_SETTINGS);
        
        for (int i = 0; i < list.size(); i++)
        {
            CaptureSetting s = list.get(i);
            
            if (s == null)
            {
                System.err.println("Null value passed to getCaptureSettings");
            }
            
            CaptureSetting newSetting = (CaptureSetting) USBCameraSetting.getUSBSetting(nm.getKey("current" + s.getName()), nm.getKey("available" + s.getName()), s.getClass());
            
            if (newSetting != null)
            {
                list.set(i, newSetting);
            }
            else
            {
                System.err.println("Failed to get " + s.getName() + " value from camera");
                 
                return false;
            }
        }
        
        return true;
    }
    
    @Deprecated
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
                        
            return (CaptureSetting) f;
        }        
    }
    
    @Deprecated
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
        
        final USBMessage nm;

        if (focus)
        {
            nm = this.sendCommand(CAPTURE_WITH_FOCUS);
            //focus();
        }
        else
        {
            nm = this.sendCommand(CAPTURE);
        }

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
    
    @Override
    public boolean isBusy()
    {
        return this.q.size() > 0;
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
            
            //stream.mark(sb.length());   
        }
        catch(IOException e)
        {
            // Error handling
        }
        
        return sb.toString();
    }

    @Override
    public boolean focus()
    {
        return this.sendCommand(FOCUS).hasError() == false;
    }
    
    @Override
    /**
     * Unfortunately the SDK doesn't seem to properly support this yet
     */
    public boolean focus(int adjustment)
    {
        return this.sendCommand(FOCUS_WITH_SETTING + "\n" + adjustment).hasError() == false;
    }

    @Override
    synchronized public boolean setSettings(List<CaptureSetting> settingList)
    {
        if (settingList.size() == 1)
        {
            return setSetting(settingList.get(0));
        }
        
        List<CaptureSetting> l = Arrays.asList(new FNumber(), new ShutterSpeed(), new ISO(), new ExposureCompensation());

        String writeString = "\n";
        
        // Piece together the parameters
        for (CaptureSetting sOrdered : l)
        {
            String candidateVal = " ";
            
            for (CaptureSetting toSet : settingList)
            {
                if (sOrdered.getName().equals(toSet.getName()))
                {
                    candidateVal = toSet.getValue().toString();
            
                    break;
                }    
            }
            
            if (sOrdered != l.get(l.size() - 1))
            {
                candidateVal += "\n";
            }
            
            writeString += candidateVal;
        }
        
        USBMessage nm = this.sendCommand(SET_ALL_SETTINGS + writeString);
        
        if (nm.hasError())
        {
            System.err.println(nm.getError());
            return false;
        }
        
        return true;
        
    }

    @Override
    public boolean startLiveView(int port) {
        
        USBMessage nm = this.sendCommand(START_LV + "\n" + port);
        
        if (nm.hasError())
        {
            System.err.println(nm.getError());
            return false;
        }
        
        return true;
    }

    @Override
    public boolean stopLiveView() {
        USBMessage nm = this.sendCommand(STOP_LV);
        
        if (nm.hasError())
        {
            System.err.println(nm.getError());
            return false;
        }
        
        return true;
    }
}
