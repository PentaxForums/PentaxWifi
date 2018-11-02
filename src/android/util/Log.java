/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package android.util;


/**
 *
 * @author Adam
 */
public class Log {
    
    public static int p(String t, String a, String b)
    {
        System.out.println("        " + t + " " + a + " " + b);
        return 0;
    }
    
    public static int i(String a, String b)
    {
       return p("INFO", a, b); 
    }
    
    public static int w(String a, String b)
    {
       return p("WARN", a, b); 
    }
    
    public static int e(String a, String b)
    {
       return p("ERRR", a, b); 
    }
    
    public static int e(String a, String b, Throwable t)
    {
       return p("ERRR", a, b + t.toString()); 
    }
    
    public static int w(String a, String b, Throwable t)
    {
       return p("WARN", a, b + t.toString()); 
    }
    
    public static int d(String a, String b)
    {
       return p("DBUG", a, b);
    }
}
