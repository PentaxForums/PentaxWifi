/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi.gui.helpers;

import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureSetting;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Scanner;
import javax.swing.table.DefaultTableModel;
import pentaxwifi.gui.MainGui;

/**
 *
 * @author Adam
 */
public class CameraSettingTableModel extends DefaultTableModel implements java.io.Serializable
{
    public CameraSettingTableModel (Object[][] o, String[] s)
    {
        super(o, s);
    }
    
    /**
     * Ricoh's classes cannot be serialized, so we must do it manually.
     * Saves the model to a text file.
     * @param filePath
     * @param m
     * @return 
     */
    public static boolean serialize(String filePath, CameraSettingTableModel m)
    {
        String output = "";
        
        // Save model as string, row by row
        try
        {
            for (int row = 0; row < m.getRowCount(); row++)
            {
                output = output 
                    + m.getValueAt(row, 0).toString() + ","
                    + m.getValueAt(row, 1).toString() + ","
                    + m.getValueAt(row, 2).toString() + ","
                    + m.getValueAt(row, 3).toString() + "\n";
            }
            
            FileOutputStream fileOut =
                new FileOutputStream(filePath);
            fileOut.write(output.getBytes());
            fileOut.close();
            
            return true;
        }
        catch (IOException i)
        {
           i.printStackTrace();
        }

        return false;
    }
    
    /**
     * Reads the data saved in a text file into the existing model
     * @param existing
     * @param filePath
     * @param avs
     * @param tvs
     * @param isos
     * @param evs
     * @return 
     */
    public boolean unserialize(String filePath, List<CaptureSetting> avs, List<CaptureSetting> tvs, List<CaptureSetting> isos, List<CaptureSetting> evs)
    {        
        try
        {
           Scanner input = new Scanner(new File(filePath)).useDelimiter("\n");
           
           // Empty out existing model
           while (this.getRowCount() > 0)
           {
                this.removeRow(0);
           }
           
           // Split each line by comma
           while (input.hasNext())
           {             
               String[] fragments = input.next().split(",");
               
               if (fragments.length == 4)
               {
                   ComboItem av = MainGui.DEFAULT_COMBO_ITEM;
                   
                   for (CaptureSetting s : avs)
                   {
                       if (s.getValue().toString().equals(fragments[0].trim()))
                       {
                           av = new ComboItem(s.getValue().toString(), (Object) s);
                           break;
                       }
                   }
                   
                   ComboItem tv = MainGui.DEFAULT_COMBO_ITEM;
                   
                   for (CaptureSetting s : tvs)
                   {
                       if (s.getValue().toString().equals(fragments[1]))
                       {
                           tv = new ComboItem(s.getValue().toString(), (Object) s);
                           break;
                       }
                   }
                   
                   ComboItem iso = MainGui.DEFAULT_COMBO_ITEM;
                   
                   for (CaptureSetting s : isos)
                   {
                       if (s.getValue().toString().equals(fragments[2]))
                       {
                           iso = new ComboItem(s.getValue().toString(), (Object) s);
                           break;
                       }
                   }
                   
                   ComboItem ev = MainGui.DEFAULT_COMBO_ITEM;
                   
                   for (CaptureSetting s : evs)
                   {
                       if (s.getValue().toString().equals(fragments[3]))
                       {
                           ev = new ComboItem(s.getValue().toString(), (Object) s);
                           break;
                       }
                   }
                   
                   this.addRow(new Object[]{av, tv, iso, ev});
               }
           }
           
           return true; 
        }
        catch (IOException i)
        {
           System.out.println("Failed to parse save file.");
        }
      
        return false;
    }    
    
    /**
     * Cells not editable
     * @param row
     * @param column
     * @return 
     */
    @Override
    public boolean isCellEditable(int row, int column)
    {
       return false;
    }
}
