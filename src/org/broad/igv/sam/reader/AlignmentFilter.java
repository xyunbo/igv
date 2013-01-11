package org.broad.igv.sam.reader;

import org.broad.igv.sam.Alignment;

/**
 * @author jrobinso
 *         Date: 1/10/13
 *         Time: 10:14 PM
 */
public interface AlignmentFilter {
    boolean filterAlignment(Alignment alignment);
}
