/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pfpentaxtether.usb;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Adam
 */
public class USBMessage
{
    private static final char END_DELIM = '}';
    private static final String MAP_DELIM = ",";
    private static final String MAP_SEP = "=";

    
    private String typ;
    private String err;
    private String msg;
    private Map<String, String> m;
    
    private static final Pattern PATTERN = Pattern.compile("\\{typ:([^,]+),msg:([^,]*),err:([^,]*),data:(.*)\\}", Pattern.DOTALL);
        
    /**
     * Parse the message from input string
     * @param s 
     */
    public USBMessage (String s)
    {
        s = s.trim();
        
        typ = "";
        err = "Malformed message (" + s + ")";
        msg = "";
        m = new HashMap<>();
        
        Matcher matcher = PATTERN.matcher(s);
                
        if (matcher.find())
        {
            typ = matcher.group(1);
            msg = matcher.group(2);
            err = matcher.group(3);
            
            String mapParse = matcher.group(4);
            
            for (String chunk : mapParse.split(MAP_DELIM))
            {
                String[] kv = chunk.split(MAP_SEP);
                
                if (kv.length == 2)
                {
                    m.put(kv[0], kv[1]);
                }
            }
        }    
        
        System.out.println(this);
    }
    
    /**
     * Gets a data value
     * @param k
     * @return 
     */
    public String getKey(String k)
    {
        return this.m.get(k);
    }
    
    /**
     * Returns true if all the requested keys are present in the data map
     * @param ks
     * @return 
     */
    public boolean hasKeys(String... ks)
    {
        for (String k : ks)
        {
            if (!hasKey(k))
            {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks the data map for the presence of a single key
     * @param k
     * @return 
     */
    public boolean hasKey(String k)
    {
        return this.m.containsKey(k);
    }
    
    /**
     * Is this an error message?
     * @return 
     */
    public boolean hasError()
    {
        return !"".equals(err);
    }
    
    /**
     * Is there a message?
     * @return 
     */
    public boolean hasMessage()
    {
        return !"".equals(msg);
    }
    
    /**
     * Returns the message
     * @return 
     */
    public String getMessage()
    {
        return msg;
    }
    
    /**
     * Returns the error
     * @return 
     */
    public String getError()
    {
        return err;
    }
    
    /**
     * Returns the type
     * @return 
     */
    public String getType()
    {
        return typ;
    }
    
    /**
     * Returns the delimiter character for messages
     * @return 
     */
    public static char getMessageDelim()
    {
        return END_DELIM;
    }
    
    @Override
    public String toString()
    {
        return this.typ + " err: " + this.err + " msg: " + this.msg + " data: " + this.m.toString();
    }
}
