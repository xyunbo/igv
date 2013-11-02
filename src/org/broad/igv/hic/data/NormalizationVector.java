package org.broad.igv.hic.data;

import org.broad.igv.hic.HiC;
import org.broad.igv.hic.NormalizationType;

/**
 * @author jrobinso
 *         Date: 2/10/13
 *         Time: 9:19 AM
 */
public class NormalizationVector {

    NormalizationType type;
    int chrIdx;
    HiC.Unit unit;
    int resolution;
    double [] data;

    public NormalizationVector(NormalizationType type, int chrIdx, HiC.Unit unit, int resolution, double[] data) {
        this.type = type;
        this.chrIdx = chrIdx;
        this.unit = unit;
        this.resolution = resolution;
        this.data = data;
    }

    public String getKey() {
        return NormalizationVector.getKey(type , chrIdx , unit.toString(), resolution );
    }

    public double [] getData() {
        return data;
    }


    public static String getKey(NormalizationType type, int chrIdx, String unit, int resolution) {
        return type + "_"  + chrIdx + "_" + unit.toString() + "_" + resolution;
    }


    public static void main(String [] args) {
        System.out.println(getKey(NormalizationType.GW_KR, 1, "x", 0));
    }
}
