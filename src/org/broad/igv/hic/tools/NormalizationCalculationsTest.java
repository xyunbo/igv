package org.broad.igv.hic.tools;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author jrobinso
 *         Date: 2/6/13
 *         Time: 8:38 AM
 */
public class NormalizationCalculationsTest {


    @Test

    //   |1 2 3|   |1|   |1+4+9|      14
    //   |2 2 4| x |2| = |2+4+12| =>  18
    //   |3 4 3|   |3|   |3+8+9|      20
    public void testMatrixMultiplication() {

        NormalizationCalculations.SparseSymmetricMatrix m = new NormalizationCalculations.SparseSymmetricMatrix();

        m.set(0,0,1);
        m.set(0,1,2);
        m.set(0,2,3);
        m.set(1,1,2);
        m.set(1,2,4);
        m.set(2,2,3);

        double[] v = {1, 2, 3};

        double[] r = m.multiply(v);

        assertEquals(14, r[0], 0.000001);
        assertEquals(18, r[1], 0.000001);
        assertEquals(20, r[2], 0.000001);

    }
}
