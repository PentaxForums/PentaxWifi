
import pfpentaxtether.gui.MainGui;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Adam
 */
public class startGui
{
    /**
     * @param args the command line arguments
     */
    public static void main(String args[])
    {      
        /*
        System.out.println(m.getAvailableAv());
        System.out.println(m.getAvailableTv());
        System.out.println(m.getAvailableISO());
        System.out.println(m.getAvailableEV());

                        
        try {
            m.enqueuePhoto(new FuturePhoto(false, m.genCaptureSettings(FNumber.F5_6, ShutterSpeed.SS1_60)));
               m.enqueuePhoto(new FuturePhoto(false, m.genCaptureSettings(FNumber.F8_0, ShutterSpeed.SS1_125)));
            m.enqueuePhoto(new FuturePhoto(false, m.genCaptureSettings(FNumber.F11, ShutterSpeed.SS1_1250)));
        } catch (CameraException ex) {
            Logger.getLogger(MainGui.class.getName()).log(Level.SEVERE, null, ex);
        }*/

        //System.out.println("Done!");
        //m.focus();            
        //m.captureStillImage(false);
        
        java.awt.EventQueue.invokeLater(new Runnable()
        {
            public void run() {
                new MainGui().setVisible(true);
            }
        });
    }
}
