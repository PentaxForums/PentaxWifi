/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pfpentaxtether.gui.helpers;

import java.awt.Component;

/**
 *
 * @author Adam
 */
public class ComboItem extends Component
{
    private final String key;
    private final Object value;

    public ComboItem(String key, Object value)
    {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString()
    {
        return key;
    }

    public String getKey()
    {
        return key;
    }

    public Object getValue()
    {
        return value;
    } 
}
