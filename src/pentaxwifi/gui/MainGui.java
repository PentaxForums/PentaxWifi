/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pentaxwifi.gui;

import com.ricoh.camera.sdk.wireless.api.CameraImage;
import com.ricoh.camera.sdk.wireless.api.ImageFormat;
import com.ricoh.camera.sdk.wireless.api.setting.capture.CaptureSetting;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ExposureCompensation;
import com.ricoh.camera.sdk.wireless.api.setting.capture.FNumber;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ISO;
import com.ricoh.camera.sdk.wireless.api.setting.capture.ShutterSpeed;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;
import pentaxwifi.CameraConnectionModel;
import pentaxwifi.CameraException;
import pentaxwifi.CaptureEventListener;
import pentaxwifi.gui.helpers.ComboItem;
import pentaxwifi.FuturePhoto;
import pentaxwifi.gui.helpers.CameraSettingTableModel;

/**
 *
 * @author Adam
 */
public class MainGui extends javax.swing.JFrame implements CaptureEventListener
{
    // Camera API
    private CameraConnectionModel m;
    
    // Flag true if batch shooting in progress
    private boolean processing;
    
    // Internal state    
    private Preferences prefs;
    private String saveFilePath;
    private Boolean doTransferFiles;
    private Boolean doTransferRawFiles;
    private Boolean doTransferThumbnails;
    private Boolean doAutoReconnect;
    private int imagesTransmitting;
    private int thumbsTransmitting;
    private ScheduledExecutorService pool;
    private List<CameraImage> downloadTasks;
    private File lastThumb;
    private LiveViewGui lv;
    private Boolean initializing;
    
    // Application icon
    public static final String ICON = "resources/appicon.png";
    
    // Progress spinner
    public static final String LOADING_ICON = "resources/ajax-loader.gif";
    
    // Default path for saving photos
    public static final String DEFAULT_PATH = System.getProperty("user.dir");
    
    // Default empty dropdown entry
    public static final ComboItem DEFAULT_COMBO_ITEM = new ComboItem("---", null);
    
    // Version number
    public static final String VERSION_NUMBER = "1.0.0 Beta 5";
    public static final String SW_NAME = "Pentax Wi-Fi Tether by PentaxForums.com";
    
    /**
     * Creates new form MainGui
     */
    public MainGui()
    {      
        // Initialize state
        prefs = Preferences.userRoot().node(this.getClass().getName());
        processing = false;
        m = new CameraConnectionModel();     
        pool = Executors.newScheduledThreadPool(1);
        imagesTransmitting = 0;
        thumbsTransmitting = 0;
        downloadTasks = new LinkedList<>();
        
        // Set look and feel - prefer OS
        try
        {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels())
            {
                if ("Metal".equals(info.getName()))
                {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
            
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels())
            {
                //if (javax.swing.UIManager.getSystemLookAndFeelClassName().equals(info.getName())) {
                if ("Windows".equals(info.getName()))
                {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }       
            
        }
        catch (Exception ex)
        {
            java.util.logging.Logger.getLogger(MainGui.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        
        // Initialize connection
        connect();
        m.addListener(new GuiEventListener(m, this));
        
        // Initialize GUI state
        
        // Set icon
        ImageIcon img = new ImageIcon(getClass().getClassLoader().getResource(MainGui.ICON));
        this.setIconImage(img.getImage());
        
        // Render UI
            
        initComponents();
        initLabels();     
        loadPrefs();
        
        // Empty the table
        while (this.queueTable.getModel().getRowCount() > 0)
        {
            ((CameraSettingTableModel) this.queueTable.getModel()).removeRow(0);
        }
        
        // Enable right-click delete in table
        final JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem deleteItem = new JMenuItem("Delete Selected");
        deleteItem.addActionListener((ActionEvent e) -> {
            deleteFromTable();
        });
        popupMenu.add(deleteItem);
        queueTable.setComponentPopupMenu(popupMenu);
        
         // Load table contents
        String savedTable = prefs.get("savedTable", "");
        
        if (!savedTable.equals(""))
        {
            File file = new File(savedTable);
                        
            loadTable(file);
        }
        
        // Set progress spinner
        ImageIcon loadingImg = new ImageIcon(getClass().getClassLoader().getResource(MainGui.LOADING_ICON));
        this.loaderLabelTransfer.setIcon(new ImageIcon(loadingImg.getImage()));
        this.loaderLabelTransfer.setText("");
        this.loaderLabelTransfer.setVisible(false);
        this.loaderLabelPhoto.setIcon(new ImageIcon(loadingImg.getImage()));
        this.loaderLabelPhoto.setText("");
        this.loaderLabelPhoto.setVisible(false);
        this.loaderLabelThumb.setIcon(new ImageIcon(loadingImg.getImage()));
        this.loaderLabelThumb.setText("");
        this.loaderLabelThumb.setVisible(false);
        
        getContentPane().setBackground(Color.WHITE);
        thumbnailArea.setBackground(Color.WHITE);

        // Popups for adding to queue
        
        final JPopupMenu popupMenu1 = new JPopupMenu();
        JMenuItem menuItem1 = new JMenuItem("+ Aperture - Shutter Speed (1 EV)");
        menuItem1.addActionListener((ActionEvent e) -> {
            if(adjust(avMenu, 3) && adjust(tvMenu, -3))
            {
                addQueueButtonActionPerformed(null); 
            }
        });
        JMenuItem menuItem2 = new JMenuItem("- Aperture + Shutter Speed (1 EV)");
        menuItem2.addActionListener((ActionEvent e) -> {
            if(adjust(avMenu, -3) && adjust(tvMenu, 3))
            {
                addQueueButtonActionPerformed(null); 
            }
        });
        JMenuItem menuItem3 = new JMenuItem("- ISO + Shutter Speed (1 EV)");
        menuItem3.addActionListener((ActionEvent e) -> {
            if(adjust(isoMenu, -1) && adjust(tvMenu, 3))
            {
                addQueueButtonActionPerformed(null); 
            }
        });
        JMenuItem menuItem4 = new JMenuItem("+ ISO - Shutter Speed (1 EV)");
        menuItem4.addActionListener((ActionEvent e) -> {
            if(adjust(isoMenu, 1) && adjust(tvMenu, -3))
            {
                addQueueButtonActionPerformed(null); 
            }
        });
        popupMenu1.add(menuItem1);
        popupMenu1.add(menuItem2);
        popupMenu1.add(menuItem3);
        popupMenu1.add(menuItem4);
        addQueueButton.setComponentPopupMenu(popupMenu1);
        
        // Enable right-click focus on capture
        final JPopupMenu popupMenu2 = new JPopupMenu();
        JMenuItem focusItem = new JMenuItem("Focus");
        focusItem.addActionListener((ActionEvent e) -> {
            try
            {
                m.focus();
            }
            catch (CameraException ex)
            {
                JOptionPane.showMessageDialog(null, "Failed to focus.  Is camera in MF mode?");
            }
        });
        
        popupMenu2.add(focusItem);
        captureButton.setComponentPopupMenu(popupMenu2);
    }
   
    /**
     * Checks if the selected folder path is valid
     * @param checkPath
     * @return 
     */
    private boolean validatePath(String checkPath)
    {
        File path = new File(checkPath);
        
        return path.exists() && path.isDirectory();
    }
    
    /**
     * Loads user preferences and updates UI state
     */
    private void loadPrefs()
    {
        doTransferThumbnails = prefs.getBoolean("doTransferThumbnails", true);
        doTransferFiles = prefs.getBoolean("doTransferFiles", false);
        doTransferRawFiles = prefs.getBoolean("doTransferRawFiles", false);
        saveFilePath = prefs.get("saveFilePath", DEFAULT_PATH);
        doAutoReconnect = prefs.getBoolean("doAutoReconnect", true);        
        this.timeSlider.setValue(prefs.getInt("sliderPosition", 0));
        
        Boolean min = prefs.getBoolean("minutes", false);
        
        if (min)
        {
            this.minutes.setSelected(true);
        }
        else
        {
            this.Seconds.setSelected(true);
        }
                
        if (!validatePath(saveFilePath))
        {
            saveFilePath = DEFAULT_PATH;
        }
        
        this.transferRawFiles.setSelected(doTransferRawFiles);
        this.transferFiles.setSelected(doTransferFiles);
        this.transferThumbnails.setSelected(doTransferThumbnails);
        this.autoReconnect.setSelected(doAutoReconnect);
    }
    
    @Override
    public void liveViewImageUpdated(BufferedImage img)
    {
        if (this.lv != null)
        {
            this.lv.updateImage(img);
        }
    }
    
    /**
     * Callback fired when a captured image has been saved on the camera
     * @param image
     */
    @Override
    synchronized public void imageStored(CameraImage image)
    {       
        // Raw file
        if ((image.getFormat() == ImageFormat.PEF || image.getFormat() == ImageFormat.DNG) && this.doTransferRawFiles)
        {
            // We need to postpone downloading until the end to maintain stability            
            if (processing)
            {
                this.downloadTasks.add(image);
            }
            else
            {
                processImageDownload(image);                 
            } 
        }
        // Jpeg file
        else if (image.getFormat() == ImageFormat.JPEG)
        {
            if (doTransferThumbnails)
            {
                thumbsTransmitting += 1;
            
                // Get the thumbnail
                this.m.downloadImage(null, image, true, this);
            }
            else
            {
                this.thumbnailArea.setIcon(null);
            }
            
            if (doTransferFiles)
            {
                // We need to postpone downloading until the end to maintain stability            
                if (processing)
                {
                    this.downloadTasks.add(image);
                }
                else
                {
                    processImageDownload(image);                 
                }                
            }
        }
        
        refreshTransmitting();
    }
         
    /**
     * Callback fired when an image has been downloaded to this PC
     * @param image
     * @param f
     * @param isThumbnail 
     */
    @Override
    public synchronized void imageDownloaded(CameraImage image, File f, boolean isThumbnail)
    {        
        // File transfer failed...
        if (f == null)
        {
            if (!isThumbnail)
            {
                imagesTransmitting -= 1;
            }
            
            if (image != null)
            {
                JOptionPane.showMessageDialog(this, "Failed to fetch " + (isThumbnail ? "thumbnail" : "image") + " " + image.getName());
            }
            else
            {
                JOptionPane.showMessageDialog(this, "File transfer aborted.");
            }    
        }
        // Transfer was successful
        else
        {
            // If thumbnail, display it and optionally start transferring the full image
            if (isThumbnail)
            {
                BufferedImage myPicture;
                try
                {
                    myPicture = ImageIO.read(f);
                    this.thumbnailArea.setIcon(new ImageIcon(myPicture));
                    
                     // Delete the temporary thumbnail file
                    this.m.getDownloadedThumb(image).delete();   
                    
                    this.lastThumb = f;
                }
                catch (IOException ex)
                {
                    Logger.getLogger(MainGui.class.getName()).log(Level.SEVERE, null, ex);
                }
                 
                thumbsTransmitting -= 1;
            }
            else
            {
                imagesTransmitting -= 1;
            }
            
            System.out.println("Downloaded: " + f.getAbsolutePath());
        }
        
        refreshTransmitting();
    }
    
    /**
     * Updates the state of the UI spinner
     */
    private void refreshTransmitting()
    {                
        if (imagesTransmitting > 0)
        {
            this.numImagesTransmitting.setText(
                Integer.toString(imagesTransmitting) + " file" + 
                        ((imagesTransmitting != 1) ? "s" : "") 
                        + " being copied."
            ); 
        }
        else
        {
            this.numImagesTransmitting.setText("");
        }
        
        this.loaderLabelTransfer.setVisible(imagesTransmitting > 0);
        //this.loaderLabel2.setVisible(thumbsTransmitting != 0);
    }
    
    /**
     * Callback for image capture
     * @param captureOk
     * @param remaining 
     */
    @Override
    synchronized public void imageCaptureComplete(boolean captureOk, int remaining)
    {
        if (!captureOk)
        {
            this.m.emptyQueue();
            JOptionPane.showMessageDialog(this, String.format("Shooting interrupted on frame %d.  Possible camera timeout - try reconnecting.", this.queueProgressBar.getMaximum() - remaining));
        }
        else if (processing)
        {
            this.queueProgressBar.setValue(this.queueProgressBar.getMaximum() - remaining);
        }
             
        endProcessing();
        updateBattery();
    }
    
    synchronized private void processImageDownload(CameraImage i)
    {
        this.imagesTransmitting += 1;        
        this.m.downloadImage(saveFilePath, i, false, this);
        this.refreshTransmitting();
    }
    
    synchronized private void endProcessing()
    {
        if (this.m.getQueueSize() == 0 && processing)
        {
            this.processQueueButton.setEnabled(true);
            this.queueProgressBar.setVisible(false);
            this.processing = false;
                        
            // Start all downloading at the end
            // TODO - delay until live view ends
            while (!this.downloadTasks.isEmpty())
            {
                processImageDownload(this.downloadTasks.remove(0));
            }
        }
    }
    
    synchronized private void autoReconnect()
    {
        JOptionPane pane = new JOptionPane("Please wait, attempting to automatically reconnect.  Exit program instead?",  JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
        JDialog getTopicDialog =  pane.createDialog(this, "Connection Lost");

        abortTransfer();
        
        (new Thread()
        {  
            @Override
            public void run()
            {
                while (!m.connected())
                {
                    try
                    {
                        m.connect();
                    } 
                    catch (CameraException ex)
                    {
                        System.out.print(ex.toString());
                    }
                }

                // Get rid of the popup
                pane.setValue(JOptionPane.CANCEL_OPTION);
                getTopicDialog.dispose();
            }
        }).start();

        // Show dialog
        getTopicDialog.setVisible(true);

        // Window was closed
        if(null == pane.getValue())
        {
            doExit();
        }
        else
        {
            switch(((Integer)pane.getValue()))
            {
                // Exit if requested
                case JOptionPane.OK_OPTION:
                    doExit();
                    break;
                // Not possible normally - was set by successful reconnect
                case JOptionPane.CANCEL_OPTION:  
                    break;
                default:
                    break;
            }
        }      
        
        restartLv();
    }
    
    /**
     * Callback fires if camera disconnects unexpectedly
     */
    @Override
    public void disconnect()
    {        
        if (doAutoReconnect)
        {
            autoReconnect();   
        }
        else
        {
            connectFailed();
        }
    }
    
    private void restartLv()
    {
        // Attempt to automatically restart LV on reconnect
        if (this.lv != null)
        {
            if (this.lv.isVisible())
            {
                this.startLiveViewMenuActionPerformed(null);
            }
        }
    }
    
    /**
     * Handler for failed connection
     */
    synchronized private void connectFailed()
    {
        int choice = JOptionPane.showConfirmDialog(this, "Communication with the camera failed.  Please ensure you first connect to the camera's wi-fi network.  Retry?", "Error", JOptionPane.YES_OPTION);
                
        if (choice == JOptionPane.YES_OPTION)
        {
            try
            {
                if (!m.connected())
                {
                    m.connect();
                }
            }
            catch (CameraException ex)
            {
                connectFailed();
                return;
            }
            
            restartLv();
        }
        else
        {
            doExit();
        }
    }
    
    /**
     * Handler for new connection
     */
    private void connect()
    {
        try
        {
            this.m.connect();    
            
            if (this.cameraNameLabel != null)
            {
                this.initLabels();
            }
        }
        catch (CameraException ex)
        {
            connectFailed();
        }
    }
    
    /**
     * Initializes UI state
     */
    private void initLabels()
    {
        initializing = true;
        this.cameraNameLabel.setText(this.m.getCameraModel());
        this.cameraFirmwareLabel.setText("v"+ this.m.getCameraFirmware().replace("01.", "1."));
        this.cameraSerialLabel.setText("#" + this.m.getCameraSerial());
        this.queueProgressBar.setVisible(false);
        
        DefaultComboBoxModel model = (DefaultComboBoxModel) avMenu.getModel();
        model.removeAllElements();
        model.addElement(DEFAULT_COMBO_ITEM);

        for (CaptureSetting f : this.m.getAvailableAv())
        {
            model.addElement(new ComboItem(((FNumber) f).getValue().toString(), (FNumber) f));
            
            if (f.equals(this.m.getAv()))
            {
                avMenu.setSelectedIndex(model.getSize() - 1);
            }
        }
        
        model = (DefaultComboBoxModel) tvMenu.getModel();
        model.removeAllElements();
        model.addElement(DEFAULT_COMBO_ITEM);

        for (CaptureSetting f : this.m.getAvailableTv())
        {        
            model.addElement(new ComboItem(((ShutterSpeed) f).getValue().toString(), (ShutterSpeed) f));
          
            if (f.equals(this.m.getTv()))
            {
                tvMenu.setSelectedIndex(model.getSize() - 1);
            }
        }
        
        model = (DefaultComboBoxModel) isoMenu.getModel();
        model.removeAllElements();
        model.addElement(DEFAULT_COMBO_ITEM);

        for (CaptureSetting f : this.m.getAvailableISO())
        {
            model.addElement(new ComboItem(((ISO) f).getValue().toString(), (ISO) f));
            
            if (f.equals(this.m.getISO()))
            {
                isoMenu.setSelectedIndex(model.getSize() - 1);
            }
        }
        
        model = (DefaultComboBoxModel) evMenu.getModel();
        model.removeAllElements();
        model.addElement(DEFAULT_COMBO_ITEM);

        for (CaptureSetting f : this.m.getAvailableEV())
        {
            model.addElement(new ComboItem(((ExposureCompensation) f).getValue().toString(), (ExposureCompensation) f));
        
            if (f.equals(this.m.getEv()))
            {
                evMenu.setSelectedIndex(model.getSize() - 1);
            }
        }
        
        updateBattery();
        
        refreshTransmitting();
        
        initializing = false;
    }
    
    /**
     * Displays battery state
     */
    private void updateBattery()
    {
        int battery = this.m.getCameraBattery();
        this.batteryLevel.setBackground(Color.WHITE);

        if (battery > 66)
        {
            this.batteryLevel.setForeground(Color.GREEN);
        }
        else if (battery > 33)
        {
            this.batteryLevel.setForeground(Color.YELLOW);
        }
        else
        {
            this.batteryLevel.setForeground(Color.RED);
        }
        
        this.batteryLevel.setToolTipText(String.format("Battery level: %d%%", battery));
        this.batteryLevel.setValue(battery);
        this.batteryLevel.setString("");
        this.batteryLevel.setStringPainted(true);        
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jMenuItem11 = new javax.swing.JMenuItem();
        topPanel = new javax.swing.JPanel();
        jPanel8 = new javax.swing.JPanel();
        transferFiles = new javax.swing.JCheckBox();
        selectFilePath = new javax.swing.JButton();
        viewFiles = new javax.swing.JButton();
        loaderLabelTransfer = new javax.swing.JLabel();
        numImagesTransmitting = new javax.swing.JLabel();
        loaderLabelThumb = new javax.swing.JLabel();
        transferRawFiles = new javax.swing.JCheckBox();
        thumbnailArea = new javax.swing.JLabel();
        cameraNameLabel = new javax.swing.JLabel();
        cameraFirmwareLabel = new javax.swing.JLabel();
        cameraSerialLabel = new javax.swing.JLabel();
        batteryLevel = new javax.swing.JProgressBar();
        delayPanel = new javax.swing.JPanel();
        jPanel6 = new javax.swing.JPanel();
        timeSlider = new javax.swing.JSlider();
        minutes = new javax.swing.JRadioButton();
        Seconds = new javax.swing.JRadioButton();
        exposurePanel = new javax.swing.JPanel();
        evPanel = new javax.swing.JPanel();
        evTextLabel = new javax.swing.JLabel();
        incEV = new javax.swing.JButton();
        decEV = new javax.swing.JButton();
        evMenu = new javax.swing.JComboBox<>();
        isoPanel = new javax.swing.JPanel();
        isoTextLabel = new javax.swing.JLabel();
        incISO = new javax.swing.JButton();
        decISO = new javax.swing.JButton();
        isoMenu = new javax.swing.JComboBox<>();
        tvPanel = new javax.swing.JPanel();
        secondsTextLabel = new javax.swing.JLabel();
        incTv = new javax.swing.JButton();
        decTv = new javax.swing.JButton();
        tvMenu = new javax.swing.JComboBox<>();
        avPanel = new javax.swing.JPanel();
        apertureTextLabel = new javax.swing.JLabel();
        incAv = new javax.swing.JButton();
        decAv = new javax.swing.JButton();
        avMenu = new javax.swing.JComboBox<>();
        addQueueButton = new javax.swing.JButton();
        captureButton = new javax.swing.JButton();
        loaderLabelPhoto = new javax.swing.JLabel();
        refreshButton = new javax.swing.JButton();
        delayTextLabel = new javax.swing.JLabel();
        exposureTextLabel = new javax.swing.JLabel();
        queuePanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        queueTable = new javax.swing.JTable();
        processQueueButton = new javax.swing.JButton();
        queueProgressBar = new javax.swing.JProgressBar();
        captureQueueTextLabel = new javax.swing.JLabel();
        mainMenuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        startLiveViewMenu = new javax.swing.JMenuItem();
        openMenuItem = new javax.swing.JMenuItem();
        saveMenuItem = new javax.swing.JMenuItem();
        restartMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        abortIntervalMenuItem = new javax.swing.JMenuItem();
        abortTransferMenuItem = new javax.swing.JMenuItem();
        optionsMenu = new javax.swing.JMenu();
        transferThumbnails = new javax.swing.JCheckBoxMenuItem();
        autoReconnect = new javax.swing.JCheckBoxMenuItem();
        helpMenu = new javax.swing.JMenu();
        troubleshootingMenuItem = new javax.swing.JMenuItem();
        aboutMenuItem = new javax.swing.JMenuItem();

        jMenuItem11.setText("jMenuItem11");

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle(SW_NAME);
        setBackground(new java.awt.Color(255, 255, 255));
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        topPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        topPanel.setMinimumSize(new java.awt.Dimension(684, 184));

        jPanel8.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        jPanel8.setMaximumSize(new java.awt.Dimension(470, 110));
        jPanel8.setMinimumSize(new java.awt.Dimension(470, 110));
        jPanel8.setRequestFocusEnabled(false);

        transferFiles.setText("Transfer JPG");
        transferFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                transferFilesActionPerformed(evt);
            }
        });

        selectFilePath.setText("Select Folder");
        selectFilePath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectFilePathActionPerformed(evt);
            }
        });

        viewFiles.setText("View Files");
        viewFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewFilesActionPerformed(evt);
            }
        });

        loaderLabelTransfer.setText("Spin");

        numImagesTransmitting.setText("-1");
        numImagesTransmitting.setToolTipText("");
        numImagesTransmitting.setFocusable(false);
        numImagesTransmitting.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        numImagesTransmitting.setMaximumSize(new java.awt.Dimension(180, 20));
        numImagesTransmitting.setMinimumSize(new java.awt.Dimension(180, 20));
        numImagesTransmitting.setPreferredSize(new java.awt.Dimension(180, 20));

        loaderLabelThumb.setText("Spin");
        loaderLabelThumb.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                loaderLabelThumbMouseClicked(evt);
            }
        });

        transferRawFiles.setText("Transfer RAW");
        transferRawFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                transferRawFilesActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel8Layout.createSequentialGroup()
                        .addComponent(transferFiles)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(transferRawFiles)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 107, Short.MAX_VALUE)
                        .addComponent(loaderLabelThumb)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(loaderLabelTransfer)
                        .addGap(14, 14, 14))
                    .addGroup(jPanel8Layout.createSequentialGroup()
                        .addComponent(selectFilePath)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(viewFiles)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(numImagesTransmitting, javax.swing.GroupLayout.PREFERRED_SIZE, 188, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(transferFiles)
                        .addComponent(transferRawFiles))
                    .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(loaderLabelTransfer)
                        .addComponent(loaderLabelThumb)))
                .addGap(18, 18, 18)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(selectFilePath)
                    .addComponent(viewFiles)
                    .addComponent(numImagesTransmitting, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        thumbnailArea.setBackground(new java.awt.Color(255, 255, 255));
        thumbnailArea.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        thumbnailArea.setMaximumSize(new java.awt.Dimension(182, 175));
        thumbnailArea.setMinimumSize(new java.awt.Dimension(182, 175));
        thumbnailArea.setName(""); // NOI18N
        thumbnailArea.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                thumbnailAreaMouseClicked(evt);
            }
        });

        cameraNameLabel.setFont(new java.awt.Font("Tahoma", 1, 24)); // NOI18N
        cameraNameLabel.setText("jLabel1");

        cameraFirmwareLabel.setFont(new java.awt.Font("Tahoma", 1, 24)); // NOI18N
        cameraFirmwareLabel.setText("jLabel2");

        cameraSerialLabel.setFont(new java.awt.Font("Tahoma", 1, 24)); // NOI18N
        cameraSerialLabel.setText("jLabel3");

        batteryLevel.setBackground(new java.awt.Color(0, 0, 0));
        batteryLevel.setFont(new java.awt.Font("Tahoma", 1, 15)); // NOI18N
        batteryLevel.setForeground(new java.awt.Color(0, 0, 0));
        batteryLevel.setToolTipText("Battery Level");
        batteryLevel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        batteryLevel.setMaximumSize(new java.awt.Dimension(148, 16));
        batteryLevel.setMinimumSize(new java.awt.Dimension(148, 16));
        batteryLevel.setRequestFocusEnabled(false);

        javax.swing.GroupLayout topPanelLayout = new javax.swing.GroupLayout(topPanel);
        topPanel.setLayout(topPanelLayout);
        topPanelLayout.setHorizontalGroup(
            topPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(topPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(topPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(topPanelLayout.createSequentialGroup()
                        .addComponent(cameraNameLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cameraFirmwareLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cameraSerialLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(batteryLevel, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(thumbnailArea, javax.swing.GroupLayout.PREFERRED_SIZE, 182, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        topPanelLayout.setVerticalGroup(
            topPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(topPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(topPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(thumbnailArea, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(topPanelLayout.createSequentialGroup()
                        .addGroup(topPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cameraNameLabel)
                            .addComponent(cameraFirmwareLabel)
                            .addComponent(cameraSerialLabel)
                            .addComponent(batteryLevel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(11, 11, 11)
                        .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        delayPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 775, Short.MAX_VALUE)
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        timeSlider.setMajorTickSpacing(10);
        timeSlider.setMaximum(60);
        timeSlider.setMinorTickSpacing(1);
        timeSlider.setPaintLabels(true);
        timeSlider.setPaintTicks(true);
        timeSlider.setSnapToTicks(true);
        timeSlider.setValue(0);
        timeSlider.setFocusable(false);
        timeSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                timeSliderStateChanged(evt);
            }
        });

        buttonGroup1.add(minutes);
        minutes.setText("Minutes");
        minutes.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                minutesActionPerformed(evt);
            }
        });

        buttonGroup1.add(Seconds);
        Seconds.setSelected(true);
        Seconds.setText("Seconds");
        Seconds.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SecondsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout delayPanelLayout = new javax.swing.GroupLayout(delayPanel);
        delayPanel.setLayout(delayPanelLayout);
        delayPanelLayout.setHorizontalGroup(
            delayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(delayPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(delayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(delayPanelLayout.createSequentialGroup()
                        .addComponent(timeSlider, javax.swing.GroupLayout.PREFERRED_SIZE, 573, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(delayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(Seconds)
                            .addComponent(minutes, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        delayPanelLayout.setVerticalGroup(
            delayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(delayPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(delayPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(delayPanelLayout.createSequentialGroup()
                        .addComponent(minutes)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(Seconds))
                    .addComponent(timeSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(39, 39, 39)
                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        exposurePanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        evPanel.setBackground(new java.awt.Color(224, 224, 224));

        evTextLabel.setText("EV");

        incEV.setText("+");
        incEV.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                incEVActionPerformed(evt);
            }
        });

        decEV.setText("-");
        decEV.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decEVActionPerformed(evt);
            }
        });

        evMenu.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        evMenu.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                dropDownStateChanged(evt);
            }
        });

        javax.swing.GroupLayout evPanelLayout = new javax.swing.GroupLayout(evPanel);
        evPanel.setLayout(evPanelLayout);
        evPanelLayout.setHorizontalGroup(
            evPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(evPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(evPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(evPanelLayout.createSequentialGroup()
                        .addComponent(decEV, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(incEV, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(evPanelLayout.createSequentialGroup()
                        .addComponent(evMenu, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(evTextLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        evPanelLayout.setVerticalGroup(
            evPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(evPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(evPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(evTextLabel)
                    .addComponent(evMenu, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(evPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(incEV)
                    .addComponent(decEV))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        isoPanel.setBackground(new java.awt.Color(224, 224, 224));

        isoTextLabel.setText("ISO");

        incISO.setText("+");
        incISO.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                incISOActionPerformed(evt);
            }
        });

        decISO.setText("-");
        decISO.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decISOActionPerformed(evt);
            }
        });

        isoMenu.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        isoMenu.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                dropDownStateChanged(evt);
            }
        });

        javax.swing.GroupLayout isoPanelLayout = new javax.swing.GroupLayout(isoPanel);
        isoPanel.setLayout(isoPanelLayout);
        isoPanelLayout.setHorizontalGroup(
            isoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(isoPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(isoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(isoPanelLayout.createSequentialGroup()
                        .addComponent(isoTextLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(isoMenu, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(isoPanelLayout.createSequentialGroup()
                        .addComponent(decISO, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(incISO, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        isoPanelLayout.setVerticalGroup(
            isoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(isoPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(isoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(isoTextLabel)
                    .addComponent(isoMenu, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(isoPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(incISO)
                    .addComponent(decISO))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tvPanel.setBackground(new java.awt.Color(224, 224, 224));

        secondsTextLabel.setText("sec");

        incTv.setText("+");
        incTv.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                incTvActionPerformed(evt);
            }
        });

        decTv.setText("-");
        decTv.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decTvActionPerformed(evt);
            }
        });

        tvMenu.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        tvMenu.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                dropDownStateChanged(evt);
            }
        });

        javax.swing.GroupLayout tvPanelLayout = new javax.swing.GroupLayout(tvPanel);
        tvPanel.setLayout(tvPanelLayout);
        tvPanelLayout.setHorizontalGroup(
            tvPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, tvPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tvPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(tvPanelLayout.createSequentialGroup()
                        .addComponent(tvMenu, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(secondsTextLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(tvPanelLayout.createSequentialGroup()
                        .addComponent(decTv, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(incTv, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        tvPanelLayout.setVerticalGroup(
            tvPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tvPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tvPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(secondsTextLabel)
                    .addComponent(tvMenu, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(tvPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(incTv)
                    .addComponent(decTv))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        avPanel.setBackground(new java.awt.Color(224, 224, 224));

        apertureTextLabel.setText("f/");

        incAv.setText("+");
        incAv.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                incAvActionPerformed(evt);
            }
        });

        decAv.setText("-");
        decAv.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decAvActionPerformed(evt);
            }
        });

        avMenu.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        avMenu.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                dropDownStateChanged(evt);
            }
        });

        javax.swing.GroupLayout avPanelLayout = new javax.swing.GroupLayout(avPanel);
        avPanel.setLayout(avPanelLayout);
        avPanelLayout.setHorizontalGroup(
            avPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(avPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(avPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(avPanelLayout.createSequentialGroup()
                        .addComponent(apertureTextLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(avMenu, javax.swing.GroupLayout.PREFERRED_SIZE, 97, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(avPanelLayout.createSequentialGroup()
                        .addComponent(decAv, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(incAv, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        avPanelLayout.setVerticalGroup(
            avPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(avPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(avPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(apertureTextLabel)
                    .addComponent(avMenu, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(avPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(incAv)
                    .addComponent(decAv))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        addQueueButton.setText("Add to Queue");
        addQueueButton.setToolTipText("Right-click for more options");
        addQueueButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addQueueButtonActionPerformed(evt);
            }
        });

        captureButton.setText("Capture Single Photo");
        captureButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                captureButtonActionPerformed(evt);
            }
        });

        loaderLabelPhoto.setText("Spin");
        loaderLabelPhoto.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                loaderLabelPhotoMouseClicked(evt);
            }
        });

        refreshButton.setText("Refresh Settings");
        refreshButton.setToolTipText("Sync available settings after changing camera modes.");
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout exposurePanelLayout = new javax.swing.GroupLayout(exposurePanel);
        exposurePanel.setLayout(exposurePanelLayout);
        exposurePanelLayout.setHorizontalGroup(
            exposurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(exposurePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(exposurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(exposurePanelLayout.createSequentialGroup()
                        .addComponent(avPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tvPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(isoPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(evPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(exposurePanelLayout.createSequentialGroup()
                        .addComponent(addQueueButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(loaderLabelPhoto)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(refreshButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(captureButton)))
                .addContainerGap())
        );
        exposurePanelLayout.setVerticalGroup(
            exposurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(exposurePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(exposurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(avPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tvPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(isoPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(evPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 16, Short.MAX_VALUE)
                .addGroup(exposurePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addQueueButton)
                    .addComponent(captureButton)
                    .addComponent(loaderLabelPhoto)
                    .addComponent(refreshButton))
                .addContainerGap())
        );

        delayTextLabel.setForeground(new java.awt.Color(51, 51, 255));
        delayTextLabel.setText("Delay Between Photos");

        exposureTextLabel.setForeground(new java.awt.Color(51, 51, 255));
        exposureTextLabel.setText("Exposure Settings");

        queuePanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        queueTable.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        queueTable.setModel(new pentaxwifi.gui.helpers.CameraSettingTableModel(
            new Object [][] {
                {null, null, null, null}
            },
            new String [] {
                "f/", "Seconds", "ISO", "EV"
            }
        ));
        queueTable.setFocusable(false);
        queueTable.setRowHeight(20);
        queueTable.setRowMargin(2);
        jScrollPane2.setViewportView(queueTable);

        processQueueButton.setText("Process Queue");
        processQueueButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                processQueueButtonActionPerformed(evt);
            }
        });

        queueProgressBar.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        javax.swing.GroupLayout queuePanelLayout = new javax.swing.GroupLayout(queuePanel);
        queuePanel.setLayout(queuePanelLayout);
        queuePanelLayout.setHorizontalGroup(
            queuePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(queuePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(queuePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 667, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(queuePanelLayout.createSequentialGroup()
                        .addComponent(processQueueButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(queueProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 521, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        queuePanelLayout.setVerticalGroup(
            queuePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(queuePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 301, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(queuePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(processQueueButton)
                    .addComponent(queueProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        captureQueueTextLabel.setForeground(new java.awt.Color(51, 51, 255));
        captureQueueTextLabel.setText("Capture Queue");

        fileMenu.setText("File");

        startLiveViewMenu.setText("Start Live View");
        startLiveViewMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startLiveViewMenuActionPerformed(evt);
            }
        });
        fileMenu.add(startLiveViewMenu);

        openMenuItem.setText("Open Capture Queue");
        openMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openMenuItem);

        saveMenuItem.setText("Save Capture Queue");
        saveMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveMenuItem);

        restartMenuItem.setText("Restart Camera Connection");
        restartMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                restartMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(restartMenuItem);

        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        mainMenuBar.add(fileMenu);

        editMenu.setText("Edit");

        abortIntervalMenuItem.setText("Abort Interval Shooting");
        abortIntervalMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                abortIntervalMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(abortIntervalMenuItem);

        abortTransferMenuItem.setText("Abort Image Transfer");
        abortTransferMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                abortTransferMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(abortTransferMenuItem);

        mainMenuBar.add(editMenu);

        optionsMenu.setText("Options");

        transferThumbnails.setSelected(true);
        transferThumbnails.setText("Transfer Thumbnails");
        transferThumbnails.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                transferThumbnailsActionPerformed(evt);
            }
        });
        optionsMenu.add(transferThumbnails);

        autoReconnect.setSelected(true);
        autoReconnect.setText("Auto Reconnect");
        autoReconnect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoReconnectActionPerformed(evt);
            }
        });
        optionsMenu.add(autoReconnect);

        mainMenuBar.add(optionsMenu);

        helpMenu.setText("Help");

        troubleshootingMenuItem.setText("Troubleshooting");
        troubleshootingMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                troubleshootingMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(troubleshootingMenuItem);

        aboutMenuItem.setText("About");
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutMenuItem);

        mainMenuBar.add(helpMenu);

        setJMenuBar(mainMenuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(topPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(delayTextLabel)
                        .addComponent(delayPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 699, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(queuePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(captureQueueTextLabel, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(exposureTextLabel, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(exposurePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(topPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exposureTextLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exposurePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(delayTextLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(delayPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(captureQueueTextLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(queuePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(16, 16, 16))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private int getDelaySeconds()
    {
        return this.timeSlider.getValue() * (minutes.isSelected() ? 60 : 1);
    }
    
    private void processQueueButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_processQueueButtonActionPerformed

        // Enqueue items
        CameraSettingTableModel t = (CameraSettingTableModel) this.queueTable.getModel();
                
        m.emptyQueue();
        
        try
        {
            for (int r = 0; r < t.getRowCount(); r++)
            {
                List<CaptureSetting> settings = new ArrayList<>();
                
                for (int c = 0; c < t.getColumnCount(); c++)
                {
                    try
                    {
                        if (((ComboItem) t.getValueAt(r, c)).getValue() != null)
                        {
                            settings.add( 
                                    ((CaptureSetting) ((ComboItem) t.getValueAt(r, c)).getValue())
                            );
                        }
                    }
                    catch (Exception e)
                    {
                        JOptionPane.showMessageDialog(this, String.format("Error: invalid %s setting value %s.", t.getColumnName(c), t.getValueAt(r, c).toString()));
                        return;
                    }

                }

                m.enqueuePhoto(new FuturePhoto(false, settings));    
            }
        
            if (this.m.getQueueSize() != 0)
            {
                this.processQueueButton.setEnabled(false);
                //this.jButton12.setEnabled(false);
                this.processing = true;
                
                this.queueProgressBar.setStringPainted(true);

                this.queueProgressBar.setValue(0);
                this.queueProgressBar.setMaximum(this.m.getQueueSize());
                this.queueProgressBar.setVisible(true);
                                
                m.processQueue(getDelaySeconds());
            }
        }
        catch (CameraException ex)
        {
            JOptionPane.showMessageDialog(this, ex.toString());
        }
    }//GEN-LAST:event_processQueueButtonActionPerformed

    private void addQueueButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addQueueButtonActionPerformed
        
        CameraSettingTableModel t = (CameraSettingTableModel) this.queueTable.getModel();
        
        ComboItem av = (ComboItem) this.avMenu.getSelectedItem();
        ComboItem tv = (ComboItem) this.tvMenu.getSelectedItem();
        ComboItem iso = (ComboItem) this.isoMenu.getSelectedItem();
        ComboItem ev = (ComboItem) this.evMenu.getSelectedItem();
        
        if (av.getValue() != null && tv.getValue() != null && iso.getValue() != null && ev.getValue() != null)
        {
            if (!ev.getValue().equals(ExposureCompensation.EC0_0))
            {
                JOptionPane.showMessageDialog(this, "Cannot set EV together with 3 other parameters.");
            }
        }
        
        if (av.getValue() != null || tv.getValue() != null || iso.getValue() != null || ev.getValue() != null)
        {
            t.addRow(new Object[]{av, tv, iso, ev}); 
        }
    }//GEN-LAST:event_addQueueButtonActionPerformed

    private void deleteFromTable()
    {
        CameraSettingTableModel t = (CameraSettingTableModel) this.queueTable.getModel();

        while (this.queueTable.getSelectedRow() >= 0)
        {
            t.removeRow(this.queueTable.getSelectedRow());
        }
        
        this.queueTable.setModel(t);
    }
    
    private void decAvActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decAvActionPerformed
        adjust(this.avMenu, -1);  
    }//GEN-LAST:event_decAvActionPerformed

    private void incAvActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_incAvActionPerformed
        adjust(this.avMenu, 1);
    }//GEN-LAST:event_incAvActionPerformed

    private void decTvActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decTvActionPerformed
        adjust(this.tvMenu, -1);
    }//GEN-LAST:event_decTvActionPerformed

    private void incTvActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_incTvActionPerformed
        adjust(this.tvMenu, 1);
    }//GEN-LAST:event_incTvActionPerformed

    private void decISOActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decISOActionPerformed
        adjust(this.isoMenu, -1);
    }//GEN-LAST:event_decISOActionPerformed

    private void incISOActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_incISOActionPerformed
        adjust(this.isoMenu, 1);
    }//GEN-LAST:event_incISOActionPerformed

    private void decEVActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decEVActionPerformed
        adjust(this.evMenu, 1);
    }//GEN-LAST:event_decEVActionPerformed
  
    private void incEVActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_incEVActionPerformed
        adjust(this.evMenu, -1); 
    }//GEN-LAST:event_incEVActionPerformed

    private boolean adjust(JComboBox<String> menu, int step)
    {
        int next = menu.getSelectedIndex() + step;
        
        if (next >= 1 && next < menu.getModel().getSize())
        {
            menu.setSelectedIndex(next);
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Generates a list of CaptureSettings based on UI state
     * @return 
     */
    private List <CaptureSetting> getSettings()
    {
        List <CaptureSetting> l = new ArrayList<>();
        
        Object c;
        
        try
        {
             c = ((ComboItem) this.avMenu.getSelectedItem()).getValue();
            if (c != null)
            {
                l.add((CaptureSetting) c);
            }       
        }
        catch (Exception e)
        {
            
        }    
        
        try
        {
            c = ((ComboItem) this.tvMenu.getSelectedItem()).getValue();
            if (c != null)
            {
                l.add((CaptureSetting) c);
            }
        }
        catch (Exception e)
        {
            
        } 
                
        try
        {
            c = ((ComboItem) this.isoMenu.getSelectedItem()).getValue();
            if (c != null)
            {
                l.add((CaptureSetting) c);
            }
        }
        catch (Exception e)
        {
            
        } 
        
        try
        {
            c = ((ComboItem) this.evMenu.getSelectedItem()).getValue();
            if (c != null)
            {
                if (!c.equals(ExposureCompensation.EC0_0))
                {
                    l.add((CaptureSetting) c);
                }
            }
        }
        catch (Exception e)
        {
            
        } 
        
        return l;
    }
    
    /**
     * Sends the currently selected shooting settings to the camera
     */
    private void sendSettingsToCamera()
    {
        if (!this.initializing)
        {
            (new Thread(){
                @Override
                public void run()
                {
                    try
                    {
                        m.setCaptureSettings(getSettings());
                    }
                    catch (CameraException ex)
                    {
                        System.out.println(ex.toString());
                    }
                }
            }).start();    
        }
    }
    
    private void captureButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_captureButtonActionPerformed
                
        final Component parent = this;
        
        // Delay image capture if requested
        if (getDelaySeconds() > 0)
        {
            int result = JOptionPane.showConfirmDialog(parent, 
                String.format("Capture will automatically start after %d seconds.  Cancel via menu.  Proceed?", getDelaySeconds())
            );   

            if (result != JOptionPane.YES_OPTION)
            {
                return;
            }
        }
        
        loaderLabelPhoto.setVisible(true);   
        captureButton.setEnabled(false);
        
        pool = Executors.newScheduledThreadPool(1);
        
        Runnable r = () -> 
        {
            try
            {
                m.setCaptureSettings(getSettings());
                
                m.captureStillImage(false);                
            }
            catch (CameraException ex)
            {
                // TODO - add auto reconnect functionality so this error is never shown
                JOptionPane.showMessageDialog(parent, ex.toString());
            }
            finally
            {
                captureButton.setEnabled(true);
                loaderLabelPhoto.setVisible(false);
            }
        };
        
        if (getDelaySeconds() > 0)
        {
            pool.schedule(r, getDelaySeconds(), TimeUnit.SECONDS);   
        }
        else
        {
            pool.submit(r);
        }
    }//GEN-LAST:event_captureButtonActionPerformed

    private void minutesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_minutesActionPerformed
        prefs.putBoolean("minutes", this.minutes.isSelected());
    }//GEN-LAST:event_minutesActionPerformed

    private void saveMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveMenuItemActionPerformed
        // Save menu

        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Text files", "txt", "text");
        fileChooser.setFileFilter(filter);

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            if (!saveTable(fileChooser.getSelectedFile()))
            {
                JOptionPane.showMessageDialog(this, "Error saving file.");
            }
        }
    }//GEN-LAST:event_saveMenuItemActionPerformed
    
    private boolean saveTable(File file)
    {
        if (this.queueTable != null)
        {
            return CameraSettingTableModel.serialize(file.getAbsolutePath(), (CameraSettingTableModel) this.queueTable.getModel());
        }
        
        return false;
    }
    
    private boolean loadTable(File file)
    {
        return ((CameraSettingTableModel) this.queueTable.getModel()).unserialize(file.getAbsolutePath(),
                this.m.getAvailableAv(), this.m.getAvailableTv(), this.m.getAvailableISO(), this.m.getAvailableEV());
    }
    
    private void openMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openMenuItemActionPerformed
        // Open menu
 
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Text files", "txt", "text");
        fileChooser.setFileFilter(filter);

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            if (!loadTable(fileChooser.getSelectedFile()))
            {
                JOptionPane.showMessageDialog(this, "Error loading file.");
            }
        }
    }//GEN-LAST:event_openMenuItemActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        
      
    }//GEN-LAST:event_formWindowClosed

    private void timeSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_timeSliderStateChanged
        prefs.putInt("sliderPosition", this.timeSlider.getValue());
    }//GEN-LAST:event_timeSliderStateChanged

    private void SecondsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SecondsActionPerformed
        prefs.putBoolean("minutes", this.minutes.isSelected());
    }//GEN-LAST:event_SecondsActionPerformed

    private void loaderLabelPhotoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_loaderLabelPhotoMouseClicked
        
    }//GEN-LAST:event_loaderLabelPhotoMouseClicked

    /**
     * Window closed
     */
    private void doExit()
    {
        if (this.lv != null)
        {
            this.lv.end();
            this.lv.dispose();
        }
        
        dispose();
        
        if (this.lastThumb != null)
        {
            this.lastThumb.delete();
        }
        
        try
        {
            File f = File.createTempFile("table", ".sav");
        
            if (saveTable(f))
            {
                File oldF = new File(prefs.get("savedTable", ""));
                oldF.delete();

                prefs.put("savedTable", f.getAbsolutePath());
            }
        }
        catch (IOException ex)
        {
            Logger.getLogger(MainGui.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // Tell the camera we're disconnecting
        this.m.disconnect();
        
        System.exit(0);
    }
    
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        doExit();
    }//GEN-LAST:event_formWindowClosing

    private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
        JOptionPane.showMessageDialog(this, SW_NAME + " v"+VERSION_NUMBER+".\nProject page: https://github.com/PentaxForums");
    }//GEN-LAST:event_aboutMenuItemActionPerformed

    private void loaderLabelThumbMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_loaderLabelThumbMouseClicked
        // TODO add your handling code here:
    }//GEN-LAST:event_loaderLabelThumbMouseClicked

    private void selectFilePathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectFilePathActionPerformed
        JFileChooser f = new JFileChooser();
        f.setCurrentDirectory(new File(this.saveFilePath));
        f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        f.showSaveDialog(null);
        
        if (f.getSelectedFile() != null)
        {
            String path = f.getSelectedFile().getAbsolutePath();

            if (!validatePath(path))
            {
                JOptionPane.showMessageDialog(this, "Invalid folder path chosen.");
            }
            else
            {
                prefs.put("saveFilePath", path);
                loadPrefs();
            }
        }
    }//GEN-LAST:event_selectFilePathActionPerformed

    private void transferFilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_transferFilesActionPerformed

        if (imagesTransmitting == 0)
        {
            prefs.putBoolean("doTransferFiles", this.transferFiles.isSelected());
        }
        
        loadPrefs();
    }//GEN-LAST:event_transferFilesActionPerformed

    private void thumbnailAreaMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_thumbnailAreaMouseClicked
        viewFilesActionPerformed(null);
    }//GEN-LAST:event_thumbnailAreaMouseClicked

    private void viewFilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewFilesActionPerformed
        
        try
        {
            Desktop.getDesktop().open(new File(this.saveFilePath));
        }
        catch (IOException ex)
        {
            JOptionPane.showMessageDialog(this, ex.toString());
        }
    }//GEN-LAST:event_viewFilesActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        
        doExit();
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void troubleshootingMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_troubleshootingMenuItemActionPerformed
        JOptionPane.showMessageDialog(this, 
                "If the connection to the camera in unstable, please power cycle the camera and reconnect to it."
                        + "\n\nRAW+ file transfer is known to be unreliable."
                        + "\n\nThe Ricoh SDK does not work well when there are many files on the camera, so please use a formatted card if possible."
                        + "\n\nFor help, visit PentaxForums.com.");
    }//GEN-LAST:event_troubleshootingMenuItemActionPerformed

    private void transferRawFilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_transferRawFilesActionPerformed
        
        if (imagesTransmitting == 0)
        {
            prefs.putBoolean("doTransferRawFiles", this.transferRawFiles.isSelected());
        }
        loadPrefs();
    }//GEN-LAST:event_transferRawFilesActionPerformed

    synchronized private void abortTransfer()
    {
        if (imagesTransmitting != 0 || thumbsTransmitting != 0)
        {
            this.m.abortImageDownload(this);
            this.imagesTransmitting = 0;
            this.thumbsTransmitting = 0;
            refreshTransmitting();  
        }
    }
    
    private void restartMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_restartMenuItemActionPerformed
        
        (new Thread(){
            @Override
            public void run()
            {
                //abortTransfer();
                m.disconnect();
                autoReconnect(); 
            }
        }).start();
    }//GEN-LAST:event_restartMenuItemActionPerformed

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        
        final MainGui gui = this;
        
        (new Thread(){
            @Override
            public void run()
            {
                try
                {
                    m.refreshCurrentSettings();
                    initLabels();
                }
                catch (CameraException ex)
                {
                    JOptionPane.showMessageDialog(gui, ex.toString());
                } 
            }
        }).start();
    }//GEN-LAST:event_refreshButtonActionPerformed

    private void autoReconnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoReconnectActionPerformed

        prefs.putBoolean("doAutoReconnect", this.autoReconnect.isSelected());

        loadPrefs();
    }//GEN-LAST:event_autoReconnectActionPerformed

    private void transferThumbnailsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_transferThumbnailsActionPerformed

        prefs.putBoolean("doTransferThumbnails", this.transferThumbnails.isSelected());

        loadPrefs();
    }//GEN-LAST:event_transferThumbnailsActionPerformed

    private void abortTransferMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_abortTransferMenuItemActionPerformed
        abortTransfer();
    }//GEN-LAST:event_abortTransferMenuItemActionPerformed

    private void abortIntervalMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_abortIntervalMenuItemActionPerformed
        if (this.processing)
        {
            this.m.abortQueue();
            this.m.emptyQueue();
            endProcessing();
        }
        else
        {
            pool.shutdownNow();
            captureButton.setEnabled(true);
            loaderLabelPhoto.setVisible(false);
        }
    }//GEN-LAST:event_abortIntervalMenuItemActionPerformed

    private void startLiveViewMenuActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startLiveViewMenuActionPerformed
        
        if (this.lv == null)
        {
            this.lv = new LiveViewGui(this.m);
        }
         
        try
        {
            if (this.lv.isVisible())
            {
                 this.m.stopLiveView();           
            }
            
            this.m.startLiveView();
            this.lv.setVisible(true);
        }
        catch (CameraException ex)
        {
            Logger.getLogger(MainGui.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_startLiveViewMenuActionPerformed

    private void dropDownStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_dropDownStateChanged
        if (evt.getStateChange() == ItemEvent.SELECTED)
        {
            sendSettingsToCamera();
        }
    }//GEN-LAST:event_dropDownStateChanged

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton Seconds;
    private javax.swing.JMenuItem abortIntervalMenuItem;
    private javax.swing.JMenuItem abortTransferMenuItem;
    private javax.swing.JMenuItem aboutMenuItem;
    private javax.swing.JButton addQueueButton;
    private javax.swing.JLabel apertureTextLabel;
    private javax.swing.JCheckBoxMenuItem autoReconnect;
    private javax.swing.JComboBox<String> avMenu;
    private javax.swing.JPanel avPanel;
    private javax.swing.JProgressBar batteryLevel;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JLabel cameraFirmwareLabel;
    private javax.swing.JLabel cameraNameLabel;
    private javax.swing.JLabel cameraSerialLabel;
    private javax.swing.JButton captureButton;
    private javax.swing.JLabel captureQueueTextLabel;
    private javax.swing.JButton decAv;
    private javax.swing.JButton decEV;
    private javax.swing.JButton decISO;
    private javax.swing.JButton decTv;
    private javax.swing.JPanel delayPanel;
    private javax.swing.JLabel delayTextLabel;
    private javax.swing.JMenu editMenu;
    private javax.swing.JComboBox<String> evMenu;
    private javax.swing.JPanel evPanel;
    private javax.swing.JLabel evTextLabel;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JPanel exposurePanel;
    private javax.swing.JLabel exposureTextLabel;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JButton incAv;
    private javax.swing.JButton incEV;
    private javax.swing.JButton incISO;
    private javax.swing.JButton incTv;
    private javax.swing.JComboBox<String> isoMenu;
    private javax.swing.JPanel isoPanel;
    private javax.swing.JLabel isoTextLabel;
    private javax.swing.JMenuItem jMenuItem11;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel loaderLabelPhoto;
    private javax.swing.JLabel loaderLabelThumb;
    private javax.swing.JLabel loaderLabelTransfer;
    private javax.swing.JMenuBar mainMenuBar;
    private javax.swing.JRadioButton minutes;
    private javax.swing.JLabel numImagesTransmitting;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JButton processQueueButton;
    private javax.swing.JPanel queuePanel;
    private javax.swing.JProgressBar queueProgressBar;
    private javax.swing.JTable queueTable;
    private javax.swing.JButton refreshButton;
    private javax.swing.JMenuItem restartMenuItem;
    private javax.swing.JMenuItem saveMenuItem;
    private javax.swing.JLabel secondsTextLabel;
    private javax.swing.JButton selectFilePath;
    private javax.swing.JMenuItem startLiveViewMenu;
    private javax.swing.JLabel thumbnailArea;
    private javax.swing.JSlider timeSlider;
    private javax.swing.JPanel topPanel;
    private javax.swing.JCheckBox transferFiles;
    private javax.swing.JCheckBox transferRawFiles;
    private javax.swing.JCheckBoxMenuItem transferThumbnails;
    private javax.swing.JMenuItem troubleshootingMenuItem;
    private javax.swing.JComboBox<String> tvMenu;
    private javax.swing.JPanel tvPanel;
    private javax.swing.JButton viewFiles;
    // End of variables declaration//GEN-END:variables
}
