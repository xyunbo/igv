/*
 * Copyright (c) 2007-2013 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.broad.igv.sam;

import org.broad.igv.PreferenceManager;
import org.broad.igv.sam.reader.AlignmentFilter;
import org.broad.igv.sam.reader.ReadGroupFilter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains "legacy" filter criteria in one place.
 *
 * @author jrobinso
 *         Date: 1/10/13
 *         Time: 10:16 PM
 */
@XmlType(name = GeneralAlignmentFilter.NAME)
@XmlAccessorType(XmlAccessType.NONE)
public class GeneralAlignmentFilter implements AlignmentFilter {

    public static final String NAME = "AlignmentFilter";

    boolean filterFailedReads;
    boolean filterSecondaryAlignments;
    ReadGroupFilter rgFilter;
    boolean showDuplicates;
    int qualityThreshold;
    int minInsertSize;

    List<AlignmentFilter> filters;

    public GeneralAlignmentFilter() {
        final PreferenceManager prefMgr = PreferenceManager.getInstance();
        filterFailedReads = prefMgr.getAsBoolean(PreferenceManager.SAM_FILTER_FAILED_READS);
        filterSecondaryAlignments = prefMgr.getAsBoolean(PreferenceManager.SAM_FILTER_SECONDARY_ALIGNMENTS);
        showDuplicates = prefMgr.getAsBoolean(PreferenceManager.SAM_SHOW_DUPLICATES);
        qualityThreshold = prefMgr.getAsInt(PreferenceManager.SAM_QUALITY_THRESHOLD);
        minInsertSize = prefMgr.getAsInt(PreferenceManager.SAM_MIN_INSERT_SIZE_FILTER);
        rgFilter = ReadGroupFilter.getFilter();
    }

    @Override
    public boolean filterAlignment(Alignment record) {
        if (!record.isMapped() || (!showDuplicates && record.isDuplicate()) ||
                (filterFailedReads && record.isVendorFailedRead()) ||
                (filterSecondaryAlignments && !record.isPrimary()) ||
                (record.getMappingQuality() < qualityThreshold) ||
                (rgFilter != null && rgFilter.filterAlignment(record))) {
            return true;
        }

        if(minInsertSize > 0 &&
                !(record.isPaired() && record.getMate().isMapped() && Math.abs(record.getInferredInsertSize()) > minInsertSize)) {
            return true;
        }

        if (filters != null) {
            for (AlignmentFilter filter : filters) {
                if (filter.filterAlignment(record)) return true;
            }
        }

        return false;

    }
}
