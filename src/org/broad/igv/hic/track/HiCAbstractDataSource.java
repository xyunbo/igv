package org.broad.igv.hic.track;

import org.broad.igv.hic.HiC;
import org.broad.igv.renderer.DataRange;
import org.broad.igv.track.WindowFunction;

import java.awt.*;
import java.util.Collection;
import java.util.Collections;

/**
 * @author jrobinso
 *         Date: 8/26/13
 *         Time: 10:33 AM
 */
public abstract class HiCAbstractDataSource implements HiCDataSource {
    String name;
    Color color = new Color(97, 184, 209);
    Color altColor = color;
    DataRange dataRange;HiC hic;

    public HiCAbstractDataSource(HiC hic, String name) {
        this.name = name;
        this.hic = hic;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Color getColor() {
        return color;
    }

    @Override
    public Color getAltColor() {
        return color;
    }

    @Override
    public boolean isLog() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setDataRange(DataRange dataRange) {
        this.dataRange = dataRange;
    }

    @Override
    public DataRange getDataRange() {
        if(dataRange == null) {
            initDataRange();
        }
        return dataRange;
    }

    protected abstract void initDataRange();

    @Override
    public Collection<WindowFunction> getAvailableWindowFunctions() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public void setAltColor(Color selectedColor) {
        this.altColor = selectedColor;
    }
}
