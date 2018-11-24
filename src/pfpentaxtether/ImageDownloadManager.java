/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pfpentaxtether;

import com.ricoh.camera.sdk.wireless.api.CameraImage;
import com.ricoh.camera.sdk.wireless.api.response.ErrorCode;
import com.ricoh.camera.sdk.wireless.api.response.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class to manage image transfers from the camera
 * Allows downloads to be resumed in case of transfer errors
 * @author Adam
 */
public class ImageDownloadManager
{
    private final Map<CameraImage, FileOutputStream> toDownloadImages;
    private final Map<CameraImage, FileOutputStream> toDownloadThumbs;
    private final Map<CameraImage, File> downloadedImages;
    private final Map<CameraImage, File> downloadedThumbs;
    
    private ExecutorService downloadPool;
    private final CameraConnectionModel m;
    private int numImagesProcessing;
    private int numThumbsProcessing;
    
    // API v1.1 only reliably supports a single concurrent download
    public static final int NUM_DOWNLOAD_THREADS = 1;
    
    /**
     * Initialize state
     * @param m 
     */
    public ImageDownloadManager(CameraConnectionModel m)
    {
        this.m = m;
        
        numImagesProcessing = 0;
        numThumbsProcessing = 0;
        toDownloadImages = new HashMap<>();
        toDownloadThumbs = new HashMap<>();
        downloadedImages = new HashMap<>();
        downloadedThumbs = new HashMap<>();

        downloadPool = Executors.newFixedThreadPool(NUM_DOWNLOAD_THREADS);
    }
    
    /**
     * Aborts queued image downloads
     * @param l 
     */
    synchronized public void abortDownloading(CaptureEventListener l)
    {
        downloadPool.shutdownNow();
        downloadPool = Executors.newFixedThreadPool(NUM_DOWNLOAD_THREADS);
        numThumbsProcessing = 0;
        numImagesProcessing = 0;
                
        ensureStreamsClosed();        
        
        // Optional, but there's no point in re-downloading thumbnails after an abort
        clearThumbQueue();

        if (l != null)
        {
            new Thread(() -> {
                l.imageDownloaded(null, null, false);
            }).start();
        }
    }
    
    /**
     * Ensures that all open data streams are closed after an abort
     */
    private void ensureStreamsClosed()
    {
        List<FileOutputStream> toCheck = new LinkedList<>();
        
        toCheck.addAll(this.toDownloadImages.values());
        toCheck.addAll(this.toDownloadThumbs.values());
        
        toCheck.stream().filter((s) -> (s != null)).forEachOrdered((s) ->
        {
            try
            {
                s.close();
            }
            catch (IOException ex)
            {
                
            }
        });
    }
    
    /**
     * Empties the thumb download queue
     */
    synchronized public void clearThumbQueue()
    {
        if (this.numThumbsProcessing == 0)
        {
            this.toDownloadThumbs.clear();
        }
        else
        {
            System.out.println("Thumbs downloading, cannot clear queue.");
        }
    }
    
    /**
     * Empties the image download queue
     */
    synchronized public void clearImageQueue()
    {
        if (this.numImagesProcessing == 0)
        {
            this.toDownloadImages.clear();
        }
        else
        {
            System.out.println("Images downloading, cannot clear queue.");
        }
    }
    
    /**
     * Downloads enqueued images
     * @param savePath
     * @param l 
     */
    public void processImageDownloadQueue(String savePath, CaptureEventListener l)
    {
        doProcessImageDownloadQueue(savePath, false, l);
    }
    
    /**
     * Downloads enqueued thumbnails
     * @param savePath
     * @param l 
     */
    public void processThumbDownloadQueue(String savePath, CaptureEventListener l)
    {
        doProcessImageDownloadQueue(savePath, true, l);
    }
    
    /**
     * Downloads the specified type of enqueued photo
     * @param savePath
     * @param isThumbnail
     * @param l 
     */
    private void doProcessImageDownloadQueue(String savePath, boolean isThumbnail, CaptureEventListener l)
    {
        if (this.getNumProcessing(isThumbnail) == 0)
        {
            Map<CameraImage, FileOutputStream> target = isThumbnail ? toDownloadThumbs : toDownloadImages;
            
            // Order images by date
            List<CameraImage> lst = new ArrayList<>(target.keySet());
            Collections.sort(lst, 
                (CameraImage o1, CameraImage o2) -> o1.getDateTime().compareTo(o2.getDateTime())
            );
            
            lst.forEach((i) ->
            {
                doDownloadImage(savePath, i, isThumbnail, l);
            }); 
        }
    }
    
    /**
     * Gets the total number of images waiting to download
     * @return 
     */
    public int getNumEnqueuedAll()
    {
        return getNumEnqueued(true) + getNumEnqueued(false);
    }
    
    /**
     * Gets the number of images waiting to download
     * @param isThumbnail
     * @return 
     */
    public int getNumEnqueued(boolean isThumbnail)
    {
        if (isThumbnail)
        {
            return this.toDownloadThumbs.size();
        }
        else
        {
            return this.toDownloadImages.size();
        }
    }
    
    /**
     * Gets the number of all images currently being downloaded
     * @return 
     */
    synchronized public int getNumProcessingAll()
    {
        return getNumProcessing(true) + getNumProcessing(false);
    }
    
    /**
     * Gets the number of images currently being downloaded
     * @param isThumbnail
     * @return 
     */
    synchronized public int getNumProcessing(boolean isThumbnail)
    {
        if (isThumbnail)
        {
            return numThumbsProcessing;
        }
        else
        {
            return numImagesProcessing;
        }
    }
    
    /**
     * Decrement task counter
     * @param isThumbnail
     */
    synchronized private void decNumProcessing(boolean isThumbnail)
    {
        if (isThumbnail)
        {
            // Boundary check in case of undefined thread interrupt in abort
            if (this.numThumbsProcessing > 0)
            {
                this.numThumbsProcessing -= 1;
            }
        }
        else
        {
            if (this.numImagesProcessing > 0)
            {
                this.numImagesProcessing -= 1;
            }
        }
    }
    
    /**
     * Increment task counter
     * @param isThumbnail
     */
    synchronized private void incNumProcessing(boolean isThumbnail)
    {
        if (isThumbnail)
        {
            this.numThumbsProcessing += 1;
        }
        else
        {
            this.numImagesProcessing += 1;
        }
    }
    
    /**
     * Immediately downloads an image
     * @param savePath
     * @param i
     * @param l 
     */
    public void downloadImage(String savePath, CameraImage i, CaptureEventListener l)
    {
        doDownloadImage(savePath, i, false, l);
    }
    
    /**
     * Immediately downloads a thumbnail
     * @param savePath
     * @param i
     * @param l 
     */
    public void downloadThumb(String savePath, CameraImage i, CaptureEventListener l)
    {
        // TODO - this must be enqueued with a higher priority - add to queue then pop in thread
        doDownloadImage(savePath, i, true, l);
    }
    
    /**
     * Immediately starts the download of a single image
     * @param i
     * @param savePath
     * @param isThumbnail
     * @param l 
     */
    private void doDownloadImage(String savePath, CameraImage i, boolean isThumbnail, CaptureEventListener l)
    {        
        incNumProcessing(isThumbnail);
        
        this.downloadPool.submit(
            new Thread(() -> 
            {
                FileOutputStream outputStream = null;
                File f = null;
                boolean error = false;

                try
                {
                    if (savePath == null)
                    {
                        f = File.createTempFile(isThumbnail ? "thumb" : "image", i.getName());
                        
                        f.deleteOnExit();
                    }
                    else
                    {
                        f = new File(savePath + "/" + i.getName());
                        
                        // Remove the file if it already exists
                        if (f.exists())
                        {
                            f.delete();
                        }
                    }
                    
                    Response response;
                    outputStream = new FileOutputStream(f);
                    
                    // Ensure that the output stream is closed in case of interruption
                    if (isThumbnail)
                    {
                        toDownloadThumbs.put(i, outputStream);
                        response = i.getThumbnail(outputStream);
                                                
                        if (!response.getErrors().isEmpty())
                        {                   
                            // This is not a temporary error
                            if (ErrorCode.IMAGE_NOT_FOUND == response.getErrors().get(0).getCode())
                            {
                                toDownloadThumbs.remove(i);
                            }
                            
                            throw new IOException("Failed to download file: " + response.getErrors().get(0).getMessage());
                        }
                        else
                        {      
                            downloadedThumbs.put(i, f);
                            toDownloadThumbs.remove(i);
                        }
                    }
                    else
                    {
                        toDownloadImages.put(i, outputStream);
                        response = i.getData(outputStream);
                        
                        if (!response.getErrors().isEmpty())
                        {
                            // This is not a temporary error
                            if (ErrorCode.IMAGE_NOT_FOUND == response.getErrors().get(0).getCode())
                            {
                                toDownloadImages.remove(i);
                            }
                            
                            throw new IOException("Failed to download file: " + response.getErrors().get(0).getMessage());
                        }
                        else
                        {                     
                            downloadedImages.put(i, f);
                            toDownloadImages.remove(i);
                        }
                    }

                    decNumProcessing(isThumbnail);
                    if (l != null)
                    {
                        final CameraImage img = i;
                        final File fil = f;
                        final boolean isThumb = isThumbnail;
                        
                        new Thread(() -> {
                            l.imageDownloaded(img, fil, isThumb);    
                        }).start();
                    }
                }
                catch (IOException e)
                {     
                    decNumProcessing(isThumbnail);
                    if (l != null)
                    {
                        final CameraImage img = i;
                        final boolean isThumb = isThumbnail;
                        
                        new Thread(() -> {
                            l.imageDownloaded(img, null, isThumb);    
                        }).start();    
                    }
                    
                    error = true;
                    
                    System.out.println(e.toString());
                }
                // If the executor is shut down, there is no guarantee this will be reached
                finally
                {
                    if (outputStream != null)
                    {
                        try
                        {
                            outputStream.close();
                        }
                        catch (IOException e)
                        {
                            //do nothing
                        }
                    }
                    
                    // On error, delete the incomplete file after stream is closed
                    if (error)
                    {
                        if (f != null)
                        {                            
                            f.delete();
                        }
                    }
                }
            })
        );
    }
    
    /**
     * Enqueues an image download
     * @param i 
     */
    public void enqueueImage(CameraImage i)
    {
        doEnqueueImage(i, false);
    }
    
    /**
     * Enqueues a thumb download
     * @param i 
     */
    public void enqueueThumb(CameraImage i)
    {
        doEnqueueImage(i, true);
    }
            
    /**
     * Queues an image for later download
     * @param i
     * @param isThumbnail 
     */
    private void doEnqueueImage(CameraImage i, boolean isThumbnail)
    {    
        Map<CameraImage, FileOutputStream> dest = (isThumbnail ? this.toDownloadThumbs : this.toDownloadImages);
        
        if (!dest.containsKey(i))
        {
            dest.put(i, null);
        }
    }
    
    /**
     * Gets the image file corresponding to a downloaded camera image
     * @param i
     * @return 
     */
    public File getDownloadedImage(CameraImage i)
    {
        return this.downloadedImages.get(i);
    }
    
    /**
     * Gets the thumbnail corresponding to a downloaded camera image
     * @param i
     * @return 
     */
    public File getDownloadedThumb(CameraImage i)
    {
        return this.downloadedThumbs.get(i);
    }    
}
