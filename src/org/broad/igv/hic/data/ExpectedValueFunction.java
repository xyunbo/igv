package org.broad.igv.hic.data;

import org.broad.igv.hic.NormalizationType;

/**
 * @author jrobinso
 *         Date: 12/26/12
 *         Time: 9:30 PM
 */
public interface ExpectedValueFunction {

    double getExpectedValue(int chrIdx, int distance);

    int getLength();

    NormalizationType getType();

    String getUnit();

    int getBinSize();

    double[] getExpectedValues();
}
