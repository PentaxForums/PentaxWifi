/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi;

import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureSetting;
import java.util.List;

/**
 *
 * @author Adam
 */
public class FuturePhoto
{    
    public Boolean focus;
    public List<CaptureSetting> settings;   
    
    public FuturePhoto(Boolean focus, List<CaptureSetting> settings)
    {
        this.focus = focus;
        this.settings = settings;
    }
}
