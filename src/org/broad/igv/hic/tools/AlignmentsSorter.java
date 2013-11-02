package org.broad.igv.hic.tools;

import net.sf.samtools.util.CloseableIterator;
import net.sf.samtools.util.SortingCollection;
import org.apache.tools.ant.taskdefs.Java;
import org.broad.igv.Globals;
import org.broad.igv.util.ParsingUtils;

import java.io.*;
import java.util.Comparator;

/**
 * Sorts an ascii alignment pair file in a pair invariant way, in other words chr pair 1-10 is equivalent to 10-1.
 *
 * @author jrobinso
 * @date Aug 17, 2010
 */
public class AlignmentsSorter {

    static enum Format {OLD, NEW, CHRISTINE}

    public static void sort(String inputFile, String outputFile, String tmpdir) throws IOException {

        int maxRecordsInRam = 1000000;

        Format format = getFormat(inputFile);

        int chr1 = -1;
        int chr2 = -1;
        boolean includesHeaderRow = false;
        switch (format) {
            case OLD:
                chr1 = 1;
                chr2 = 5;
                break;
            case NEW:
                chr1 = 1;
                chr2 = 4;
                break;
            case CHRISTINE:
                chr1 = 0;
                chr2 = 2;
                includesHeaderRow = true;
        }

        PairComparator comp = new PairComparator(chr1, chr2);

        if(tmpdir == null) {
            tmpdir = System.getProperty("java.io.tmpdir");
        }

        SortingCollection<String> cltn =
                SortingCollection.newInstance(String.class, new Codec(), comp, maxRecordsInRam, new File(tmpdir));


        // Read input and add to sorting collection
        BufferedReader br = null;
        String headerRow = "";
        try {
            br = ParsingUtils.openBufferedReader(inputFile);

            if (includesHeaderRow) headerRow = br.readLine();

            String nextLine;
            while ((nextLine = br.readLine()) != null) {
                try {
                    cltn.add(nextLine);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        } finally {
            if (br != null) try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

        }
        cltn.doneAdding();

        // Print results of sort to output file
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
            if (includesHeaderRow) pw.println(headerRow);

            CloseableIterator<String> iter = cltn.iterator();
            while (iter.hasNext()) {
                String rec = iter.next();
                pw.println(rec);

            }
        } finally {
            if (pw != null) pw.close();
        }


    }

    static class Codec implements SortingCollection.Codec<String> {

        PrintWriter os;
        BufferedReader is;

        public void setOutputStream(OutputStream outputStream) {
            os = new PrintWriter(outputStream);
        }

        public void setInputStream(InputStream inputStream) {
            is = new BufferedReader(new InputStreamReader(inputStream), 100);
        }

        public void encode(String rec) {
            os.println(rec);
            os.flush();
        }

        public String decode() {
            String rec = null;
            try {
                rec = is.readLine();
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            return rec;
        }

        public SortingCollection.Codec<String> clone() {
            Codec clone = new Codec();
            clone.is = is;
            clone.os = os;
            return clone;

        }
    }

    static Format getFormat(String file) throws IOException {

        BufferedReader reader = null;

        reader = ParsingUtils.openBufferedReader(file);

        String nextLine = reader.readLine();

        String[] tokens = Globals.singleTabMultiSpacePattern.split(nextLine);
        int nTokens = tokens.length;

        if (nTokens == 8) {
            return Format.OLD;
        } else if (nTokens == 6) {
            return Format.NEW;
        } else if (nTokens == 5) {
            return Format.CHRISTINE;
        } else {
            throw new IOException("Unexpected column count.  Check file format");
        }
    }

    static class PairComparator implements Comparator<String> {

        int chr1Col;
        int chr2Col;

        PairComparator(int chr1Col, int chr2Col) {
            this.chr1Col = chr1Col;
            this.chr2Col = chr2Col;
        }

        public int compare(String rec1, String rec2) {
            return getPairChr(rec1).compareTo(getPairChr(rec2));
        }

        private String getPairChr(String rec) {
            String[] tokens1 = Globals.singleTabMultiSpacePattern.split(rec);
            String ca = tokens1[chr1Col];
            String cb = tokens1[chr2Col];
            return ca.compareTo(cb) < 0 ? ca + "_" + cb : ca + "_" + cb;
        }
    }

}

