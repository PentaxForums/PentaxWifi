/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pfpentaxtether;

/**
 *
 * @author Adam
 */
public class CameraException extends Exception 
{
    private String m;
    
    public CameraException(String m)
    {
        this.m = m;
    }
    
    @Override
    public String toString()
    {
        return this.m;
    }
}
