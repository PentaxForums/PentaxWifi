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
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
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
    
    // Display liveViewFrame in imageView
    @Override
    public void liveViewFrameUpdated(CameraDevice sender, byte[] liveViewFrame)
    {
        try
        {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(liveViewFrame));
            g.liveViewImageUpdated(img);
        }
        catch (IOException ex)
        {
            Logger.getLogger(GuiEventListener.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }
}

