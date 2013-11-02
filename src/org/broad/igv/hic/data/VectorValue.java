package org.broad.igv.hic.data;

/**
 * @author jrobinso
 *         Date: 10/28/13
 *         Time: 7:03 PM
 */
public class VectorValue {

    int bin;
    float value;

    public VectorValue(int bin, float value) {
        this.bin = bin;
        this.value = value;
    }

    public int getBin() {
        return bin;
    }

    public float getValue() {
        return value;
    }
}
