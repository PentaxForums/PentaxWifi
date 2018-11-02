/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi.gui;

import com.ricoh.camera.sdk.wireless.api.CameraDevice;
import com.ricoh.camera.sdk.wireless.api.CameraEventListener;
import com.ricoh.camera.sdk.wireless.api.CameraImage;
import com.ricoh.camera.sdk.wireless.api.Capture;
import java.io.File;
import pentaxwifi.CameraConnectionModel;
import pentaxwifi.CaptureEventListener;

/**
 *
 * @author Adam
 */
public class GuiEventListener extends CameraEventListener
{
    private final CameraConnectionModel m;
    private final CaptureEventListener g;
    
    public GuiEventListener(CameraConnectionModel m, CaptureEventListener g)
    {
        this.m = m;
        this.g = g;
    }
    
    @Override
    public void imageStored(CameraDevice sender, CameraImage image)
    {
        System.out.printf("Image Stored. Name: %s%n", image.getName());
                
        g.imageStored(image);
    }
    
    public void imageDownloaded(CameraImage image, File f, boolean isThumbnail)
    {  
        g.imageDownloaded(image, f, isThumbnail);
    }

    @Override
    public void captureComplete(CameraDevice sender, Capture capture)
    {            
        if (sender != null && capture != null)
        {
            System.out.printf("Capture Complete. Caputure ID: %s%n", capture.getId());
        }
                
        g.imageCaptureComplete(sender != null, m.getQueueSize());           
    }

    @Override
    public void deviceDisconnected(CameraDevice sender)
    {   
        System.out.println("Device Disconnected.");

        g.disconnect();
    }
}

