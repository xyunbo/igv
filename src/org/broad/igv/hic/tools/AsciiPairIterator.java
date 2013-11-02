/*
 * Copyright (c) 2007-2011 by The Broad Institute of MIT and Harvard.  All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL), 
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR 
 * WARRANTES OF ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING, 
 * WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
 * PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS, WHETHER 
 * OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR RESPECTIVE 
 * TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES 
 * OF ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES, 
 * ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER 
 * THE BROAD OR MIT SHALL BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT 
 * SHALL KNOW OF THE POSSIBILITY OF THE FOREGOING.
 */

package org.broad.igv.hic.tools;


import org.broad.igv.Globals;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Jim Robinson
 * @since 9/24/11
 */
public class AsciiPairIterator implements PairIterator {

    enum Format {OLD, NEW, CHRISTINE, LONG, SUMMED}

    // Map of name -> index
    private Map<String, Integer> chromosomeOrdinals;
    AlignmentPair nextPair = null;
    AlignmentPair preNext = null;
    BufferedReader reader;
    Format format = null;

    /**
     * A map of chromosome name -> chromosome string.  A private "intern" pool.  The java "intern" pool stores string
     * in perm space, which is rather limited and can cause us to run out of memory.
     */
    Map<String, String> stringInternPool = new HashMap();

    public AsciiPairIterator(String path, Map<String, Integer> chromosomeOrdinals) throws IOException {
        this.reader = org.broad.igv.util.ParsingUtils.openBufferedReader(path);
        this.chromosomeOrdinals = chromosomeOrdinals;
        advance();
    }

    public AsciiPairIterator(FileInputStream fis, Map<String, Integer> chromosomeOrdinals) throws IOException {
        this.reader = new BufferedReader(new InputStreamReader(fis));
        this.chromosomeOrdinals = chromosomeOrdinals;
        advance();
    }

    /**
     * Read the next record
     * <p/>
     * Old form:
     * str1 chr1 pos1 frag1 str2 chr2 pos2 frag2
     * 0 15 61559113 0 16 15 61559309 16
     * 16 10 26641879 16 0 9 12797549 0
     * <p/>
     * New form:
     * 0	14	100000012	16	14	100000261
     * <p/>
     * Christine's form (includes header row:  chr1	coord1	chr2	coord2	count
     * <p/>
     * 1	3017167	1	3017418	2
     * <p/>
     * Long form:
     * str1 chr1 pos1 frag1 str2 chr2 pos2 frag2 mapq1 cigar1 seq1 mapq2 cigar2 seq2 rname1 rname2
     */
    private void advance() {

        try {
            String nextLine;
            while ((nextLine = reader.readLine()) != null) {
                String[] tokens = Globals.singleTabMultiSpacePattern.split(nextLine);
                int nTokens = tokens.length;

                if (format == null) {
                    if (nTokens == 8) {
                        format = Format.OLD;
                    } else if (nTokens == 6) {
                        format = Format.NEW;
                    } else if (nTokens == 5) {
                        format = Format.CHRISTINE;
                        nextLine = reader.readLine();
                        tokens = Globals.singleTabMultiSpacePattern.split(nextLine);
                        nTokens = tokens.length;
                    } else if (nTokens == 16) {
                        format = Format.LONG;
                    } else if (nTokens == 9) {
                        format = Format.SUMMED;
                    }
                    else {
                        throw new IOException("Unexpected column count.  Check file format");
                    }
                }

                // this should be strand, chromosome, position, fragment.
                switch (format) {
                    case OLD:
                    case LONG:
                    case SUMMED:
                        String chrom1 = getInternedString(tokens[1]);
                        String chrom2 = getInternedString(tokens[5]);
                        if (chromosomeOrdinals.containsKey(chrom1) && chromosomeOrdinals.containsKey(chrom2)) {
                            int chr1 = chromosomeOrdinals.get(chrom1);
                            int chr2 = chromosomeOrdinals.get(chrom2);
                            int pos1 = Integer.parseInt(tokens[2]);
                            int pos2 = Integer.parseInt(tokens[6]);
                            int frag1 = Integer.parseInt(tokens[3]);
                            int frag2 = Integer.parseInt(tokens[7]);
                            boolean strand1 = Integer.parseInt(tokens[0]) == 0;
                            boolean strand2 = Integer.parseInt(tokens[4]) == 0;
                            nextPair = new AlignmentPair(strand1, chr1, pos1, frag1, strand2, chr2, pos2, frag2);
                            if (format == Format.SUMMED) {
                                nextPair.setScore(Float.parseFloat(tokens[8]));
                            }
                            return;
                        }
                    case NEW:
                        chrom1 = getInternedString(tokens[1]);
                        chrom2 = getInternedString(tokens[4]);
                        if (chromosomeOrdinals.containsKey(chrom1) && chromosomeOrdinals.containsKey(chrom2)) {
                            int chr1 = chromosomeOrdinals.get(chrom1);
                            int chr2 = chromosomeOrdinals.get(chrom2);
                            int pos1 = Integer.parseInt(tokens[2]);
                            int pos2 = Integer.parseInt(tokens[5]);
                            boolean strand1 = Integer.parseInt(tokens[0]) == 0;
                            boolean strand2 = Integer.parseInt(tokens[3]) == 0;
                            nextPair = new AlignmentPair(strand1, chr1, pos1, -1, strand2, chr2, pos2, -1);
                            return;
                        }
                    case CHRISTINE:
                        // TODO -- use aliases
                        chrom1 = getInternedString("chr" + tokens[0]);
                        chrom2 = getInternedString("chr" + tokens[2]);
                        if (chromosomeOrdinals.containsKey(chrom1) && chromosomeOrdinals.containsKey(chrom2)) {



                            int chr1 = chromosomeOrdinals.get(chrom1);
                            int chr2 = chromosomeOrdinals.get(chrom2);
                            int pos1 = Integer.parseInt(tokens[1]);
                            int pos2 = Integer.parseInt(tokens[3]);
                            boolean strand1 = true;
                            boolean strand2 = true;
                            nextPair = new AlignmentPair(strand1, chr1, pos1, -1, strand2, chr2, pos2, -1);
                            return;
                        }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        nextPair = null;

    }

    /**
     * Replace "aString" with a stored equivalent object, if it exists.  If it does not store it.  The purpose
     * of this class is to avoid running out of memory storing zillions of equivalent string.
     *
     * @param aString
     * @return
     */
    private String getInternedString(String aString) {
        String s = stringInternPool.get(aString);
        if (s == null) {
            s = new String(aString); // THe "new" will break any dependency on larger strings if this is a "substring"
            stringInternPool.put(aString, s);
        }
        return s;
    }

    public boolean hasNext() {
        return preNext != null || nextPair != null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public AlignmentPair next() {
        if (preNext == null) {
            AlignmentPair p = nextPair;
            advance();
            return p;
        } else {
            AlignmentPair p = preNext;
            preNext = null;
            return p;
        }
    }

    @Override
    public void push(AlignmentPair pair) {
        if (preNext != null) {
            throw new RuntimeException("Cannot push more than one alignment pair back on stack");
        } else {
            preNext = pair;
        }
    }

    public void remove() {
        // Not implemented
    }

    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

}
