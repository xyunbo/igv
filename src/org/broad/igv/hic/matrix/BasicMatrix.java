package org.broad.igv.hic.matrix;

/**
 * @author jrobinso
 *         Date: 7/13/12
 *         Time: 1:05 PM
 */
public interface BasicMatrix {

    float getEntry(int row, int col);

    int getRowDimension();

    int getColumnDimension();

    float getLowerValue();

    float getUpperValue();
}
