/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi.usb;

import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureSetting;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ExposureCompensation;
import com.ricoh.camera.sdk.wireless.api.setting.capture.FNumber;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ISO;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ShutterSpeed;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic class for instantiating FNumbers, ShutterSpeeds, etc. from a string
 * @author Adam
 * @param <T>
 */
public class USBCameraSetting <T extends CaptureSetting> extends CaptureSetting
{
    public static final String LIST_DELIM = "\\|";
    
    /**
     * Returns a USBCameraSetting of a specific type, with the passed settings
     * @param <T>
     * @param current
     * @param available
     * @param cls
     * @return 
     */
    public static <T extends CaptureSetting> USBCameraSetting<T> getUSBSetting(String current, String available, Class<T> cls)
    {
        // Get current setting
        T cur = getMatchingSetting(current.trim(), cls);
        
        // Extract available settings
        List<CaptureSetting> settings = new ArrayList<>();
        
        for (String s : available.split(LIST_DELIM))
        {            
            T cand = getMatchingSetting(s.trim(), cls);
            
            if (cand != null)
            {
                settings.add(cand);
            }
        }
        
        // Return the new object
        if (cur != null)
        {
            return new USBCameraSetting<>(cur, settings);
        }
        
        return null;
    }
    
    /**
     * Creates a new instance of this class with the specified settings
     * @param current
     * @param availableSettings 
     */
    private USBCameraSetting(T current, List<CaptureSetting> availableSettings)
    {
        super(current.getName(), current.getValue());
              
        this.availableSettings.addAll(availableSettings);
    }
    
    /**
     * String representation for the settings, for debugging
     * @return 
     */
    public String toStringDebug()
    {
        return this.toString() + " Available: " + this.availableSettings.toString();
    }
    
    /**
     * Test function
     * @param args 
     */
    public static void main(String[] args)
    {
        System.out.println(getUSBSetting("2.8", "1.2|22|4.0|8.0", FNumber.class).toStringDebug());
        System.out.println(getUSBSetting("1/100", "1/100|1/250|20|30|1.5", ShutterSpeed.class).toStringDebug());
        System.out.println(getUSBSetting("0.3", "-0.7|1.0|0.0|0.3", ExposureCompensation.class).toStringDebug());
        System.out.println(getUSBSetting("100", "100|200|1600|3200|12800", ISO.class).toStringDebug());
    }
    
    /**
     * Match a string to the corresponding setting value
     * @param <T>
     * @param candidate
     * @param cls
     * @return 
     */
    public static <T extends CaptureSetting> T getMatchingSetting(String candidate, Class<T> cls)
    {        
        
        // Get all of FNumber's public static fields (aperture values)
        Field[] declaredFields = cls.getDeclaredFields();
        List<Field> staticFields = new ArrayList<>();
        
        for (Field field : declaredFields)
        {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && java.lang.reflect.Modifier.isPublic(field.getModifiers())) {
                staticFields.add(field);
            }
        }
        
        // Attempt to match the string value to a field value
        for (Field f : staticFields)
        {
            T test;
            
            try
            {
                test = (T) f.get(cls);
            }
            catch (IllegalArgumentException | IllegalAccessException ex)
            {
                Logger.getLogger(USBCameraSetting.class.getName()).log(Level.SEVERE, null, ex);
                return null;
            }
            
            if (test != null && test.getValue().toString().equals(candidate))
            {
                //System.out.println(candidate + " parsed as " + test.getValue().toString());
                
                return test;
            }            
        }
        
        return null;
    }    
}
