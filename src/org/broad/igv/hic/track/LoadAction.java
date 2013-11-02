/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
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
package org.broad.igv.hic.track;

//~--- non-JDK imports --------------------------------------------------------

import org.apache.log4j.Logger;
import org.broad.igv.PreferenceManager;
import org.broad.igv.hic.HiC;
import org.broad.igv.hic.NormalizationType;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.ParsingUtils;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.Utilities;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;

/**
 * @author jrobinso
 */
public class LoadAction extends AbstractAction {

    static Logger log = Logger.getLogger(LoadAction.class);

    JFrame owner;
    HiC hic;
    boolean isRestricted = true;

    public LoadAction(String s, JFrame owner, HiC hic, boolean isRestricted) {
        super(s);
        this.owner = owner;
        this.hic = hic;
        this.isRestricted = isRestricted;
    }

    public String getGenomeDataURL(String genomeId) {
        String urlString = PreferenceManager.getInstance().getDataServerURL();
        String genomeURL = urlString.replaceAll("\\$\\$", genomeId);
        return genomeURL;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        //"http://www.broadinstitute.org/igvdata/hic/tracksMenu.xml"
        if (hic.getDataset() == null) {
            JOptionPane.showMessageDialog(owner, "File must be loaded to load tracks", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String genomeId = "hg19";
        String genomeURL = "http://www.broadinstitute.org/igvdata/hic/tracksMenu.txt"; //getGenomeDataURL(genomeId);


        LinkedHashSet<String> nodeURLs = getNodeURLs(genomeURL);

        if (nodeURLs == null || nodeURLs.isEmpty()) {
            MessageUtils.showMessage("No external datasets are available for the current genome (" + genomeId + ").");
        }

        List<ResourceLocator> locators = loadNodes(nodeURLs);
        if (locators != null) {
             hic.loadHostedTracks(locators);
        }

    }

    public static LinkedHashSet<String> getNodeURLs(String genomeURL) {

        InputStream is = null;
        LinkedHashSet<String> nodeURLs = null;
        try {
            is = ParsingUtils.openInputStreamGZ(new ResourceLocator(genomeURL));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
            nodeURLs = getResourceUrls(bufferedReader);
        } catch (IOException e) {
            log.error("Error loading genome registry file", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    log.error("Error closing input stream", e);
                }
            }
        }

        return nodeURLs;
    }

    private List<ResourceLocator> loadNodes(final LinkedHashSet<String> xmlUrls) {
        try {
          /**
             * Resource Tree
             */
            ResourceTree resourceTree = hic.getResourceTree();
            if (resourceTree == null) {
                if ((xmlUrls == null) || xmlUrls.isEmpty()) {
                    log.error("No external datasets are available from this server for the current genome");
                    resourceTree = new ResourceTree(hic, null, isRestricted);
                }
                else {
                    Document masterDocument = createMasterDocument(xmlUrls);
                    resourceTree = new ResourceTree(hic, masterDocument, isRestricted);
                }
            }
            resourceTree.showResourceTreeDialog(owner);
            LinkedHashSet<ResourceLocator> selectedLocators = resourceTree.getLocators();
            LinkedHashSet<ResourceLocator> deselectedLocators = resourceTree.getDeselectedLocators();

            List<ResourceLocator> newLoadList = new ArrayList<ResourceLocator>();

            boolean repaint = false;
            if (selectedLocators != null) {
                for (ResourceLocator locator : selectedLocators) {
                    if (locator.getType() != null && locator.getType().equals("norm")) {
                        NormalizationType option = null;
                        for (NormalizationType no : NormalizationType.values()) {
                            if (locator.getPath().equals(no.getLabel())) {
                                option = no;
                                break;
                            }
                        }
                        hic.loadCoverageTrack(option);
                    }
                    else if (locator.getType() != null && locator.getType().equals("loop")) {
                        hic.loadLoopList(locator.getPath());
                        repaint = true;

                    }
                    else newLoadList.add(locator);
                }
            }
            if (deselectedLocators != null) {
                for (ResourceLocator locator : deselectedLocators) {
                    hic.removeTrack(locator);

                    if (locator.getType() != null && locator.getType().equals("loop")) {
                        hic.setLoopsInvisible(locator.getPath());
                        repaint = true;
                    }
                }
            }
            if (repaint) owner.repaint();
            return newLoadList;


        } catch (Exception e) {
            log.error("Could not load information from server", e);
            return null;
        }
    }


    public static Document createMasterDocument(Collection<String> xmlUrls) throws ParserConfigurationException {

        StringBuffer buffer = new StringBuffer();

        Document masterDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element rootNode = masterDocument.createElement("Global");
        rootNode.setAttribute("name", "External 1-D Features");
        rootNode.setAttribute("version", "1");

        masterDocument.appendChild(rootNode);


        // Merge all documents into one xml document for processing
        for (String url : xmlUrls) {


            try {
                Document xmlDocument = readXMLDocument(url, buffer);

                if (xmlDocument != null) {
                    Element global = xmlDocument.getDocumentElement();
                    masterDocument.getDocumentElement().appendChild(masterDocument.importNode(global, true));

                }
            } catch (Exception e) {
                String message = "Cannot create an XML Document from " + url.toString();
                log.error(message, e);
                continue;
            }

        }
        if (buffer.length() > 0) {
            String message = "<html>The following urls could not be processed due to load failures:<br>" + buffer.toString();
            MessageUtils.showMessage(message);
        }

        return masterDocument;

    }

    private static Document readXMLDocument(String url, StringBuffer errors) {
        InputStream is = null;
        Document xmlDocument = null;
        try {
            is = ParsingUtils.openInputStreamGZ(new ResourceLocator(url));
            xmlDocument = Utilities.createDOMDocumentFromXmlStream(is);

            xmlDocument = resolveIncludes(xmlDocument, errors);

        } catch (SAXException e) {
            log.error("Invalid XML resource: " + url, e);
            errors.append(url + "<br><i>" + e.getMessage());
        } catch (java.net.SocketTimeoutException e) {
            log.error("Connection time out", e);
            errors.append(url + "<br><i>Connection time out");
        } catch (IOException e) {
            log.error("Error accessing " + url.toString(), e);
            errors.append(url + "<br><i>" + e.getMessage());
        } catch (ParserConfigurationException e) {
            log.error("Parser configuration error for:" + url, e);
            errors.append(url + "<br><i>" + e.getMessage());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    log.error("Error closing stream for: " + url, e);
                }
            }
        }
        return xmlDocument;
    }

    private static Document resolveIncludes(Document document, StringBuffer errors) {

        NodeList includeNodes = document.getElementsByTagName("Include");
        if (includeNodes.getLength() == 0) {
            return document;
        }

        int size = includeNodes.getLength();
        // Copy the nodes as we'll be modifying the tree.  This is neccessary!
        Node[] tmp = new Node[size];
        for (int i = 0; i < size; i++) {
            tmp[i] = includeNodes.item(i);
        }

        for (Node item : tmp) {
            NamedNodeMap nodeMap = item.getAttributes();
            if (nodeMap == null) {
                log.info("XML node " + item.getNodeName() + " has no attributes");
            } else {
                Attr path = (Attr) item.getAttributes().getNamedItem("path");
                if (path == null) {
                    log.info("XML node " + item.getNodeName() + " is missing a path attribute");
                } else {
                    Node parent = item.getParentNode();

                    //log.info("Loading node " + path.getValue());
                    Document doc = readXMLDocument(path.getValue(), errors);
                    if (doc != null) {
                        Element global = doc.getDocumentElement();
                        Node expandedNode = parent.getOwnerDocument().importNode(global, true);
                        parent.replaceChild(expandedNode, item);
                    }
                }
            }
        }


        return document;

    }

    /**
     * Returns the complete list of URLs from the master registry file.
     *
     * @param bufferedReader
     * @return
     * @throws java.net.MalformedURLException
     * @throws java.io.IOException
     */
    private static LinkedHashSet<String> getResourceUrls(BufferedReader bufferedReader)
            throws IOException {

        LinkedHashSet<String> xmlFileUrls = new LinkedHashSet();
        while (true) {
            String xmlFileUrl = bufferedReader.readLine();
            if ((xmlFileUrl == null) || (xmlFileUrl.trim().length() == 0)) {
                break;
            }
            xmlFileUrl = xmlFileUrl.trim();
            xmlFileUrls.add(xmlFileUrl);
        }

        return xmlFileUrls;
    }


}
