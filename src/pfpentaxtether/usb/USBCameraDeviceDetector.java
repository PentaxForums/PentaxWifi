/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pfpentaxtether.usb;

import com.ricoh.camera.sdk.wireless.api.CameraDevice;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Adam
 */
public class USBCameraDeviceDetector
{
    private static USBInterface instance;
    public static Class<?> PF_USB_BRIDGE = PFRicohUSBSDKBridge.class;
    
    /**
     * Constructs the interface instance
     * @param cls
     * @return
     */
    private static USBInterface getInstance(Class<?> cls)
    {
        if (instance == null)
        {
            if(USBInterface.class.isAssignableFrom(cls))
            {
                try
                {
                    Constructor<?> ctor = cls.getConstructor();
                    instance = (USBInterface) ctor.newInstance();
                }
                catch (Exception e)
                {
                    System.err.println("Invalid interface requested");
                    System.err.println(e);
                }
            }
            else
            {
                System.err.println("Invalid interface requested");
            }
        }
        return instance;
    }
    
    /**
     * Returns device interfaces
     * @param cls
     * @return 
     */
    public static List<CameraDevice> detect(Class<?> cls)
    {
        USBInterface iface = getInstance(cls);
        
        if (iface != null)
        {
            return iface.detectDevices();
        }
        
        return new ArrayList<>();
    }
}
