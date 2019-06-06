package org.reactome.server.tools.indexer.icon.parser;

import jodd.util.collection.SortedArrayList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.reactome.server.tools.indexer.icon.model.Category;
import org.reactome.server.tools.indexer.icon.model.Icon;
import org.reactome.server.tools.indexer.icon.model.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class MetadataParser {
    private static final Logger parserLogger = LoggerFactory.getLogger("parserLogger");
    private static final Logger logger = LoggerFactory.getLogger("importLogger");
    private static List<String> parserMessages = new SortedArrayList<>();

    private static MetadataParser instance;
    private String iconsDir;
    private String ehldsDir;
    private List<Icon> icons = new ArrayList<>();

    private MetadataParser(String iconsDir, String ehldsDir) {
        if (StringUtils.isEmpty(iconsDir) || StringUtils.isEmpty(ehldsDir)) {
            throw new IllegalArgumentException("Icons directory or EHLDs directory can't be null or empty.");
        }
        this.iconsDir = iconsDir;
        this.ehldsDir = ehldsDir;
    }

    public static MetadataParser getInstance(String iconsDir, String ehldDir) {
        if (instance == null) {
            instance = new MetadataParser(iconsDir, ehldDir);
        }
        return instance;
    }

    private void parse() {
        long startParse = System.currentTimeMillis();
        File iconLibDir = new File(iconsDir);
        if (!iconLibDir.exists()) {
            logger.error("Cannot find folder: {}", iconsDir);
            System.exit(1);
        }

        Collection<File> files = FileUtils.listFiles(iconLibDir, new String[]{"xml"}, true);
        logger.info("Parsing " + files.size() + " icons");
        logger.info("Start unmarshalling xml metadata");
        files.forEach(xml -> {
            String stId = FilenameUtils.removeExtension(xml.getName());
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(Icon.class);
                Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
                Icon icon = (Icon) jaxbUnmarshaller.unmarshal(xml);
                icon.setId(stId.replaceAll("R-ICO-", ""));
                icon.setStId(stId);
                assignSpecies(icon);
                validateReferences(icon);
                icons.add(icon);
            } catch (JAXBException e) {
                e.printStackTrace();
                logger.error("Could not unmarshall file: {}", xml.getPath());
            } catch (InvalidObjectException e) {
                logger.error(e.getMessage());
            }
        });

        logger.info("Unmarshalling is completed");
        logger.info("Getting the EHLDs where the icon is present");
        // Get EHLDs that the icon is in.
        icons.parallelStream().forEach(icon -> icon.setEhlds(getEhlds(icon)));
        logger.info("Parsing has finished and it took {}.", (System.currentTimeMillis() - startParse) + ".ms");
        parserMessages.forEach(parserLogger::info);
    }

    /**
     * Species are set programmatically based on the given categories
     *
     * @param icon given icon
     */
    private void assignSpecies(Icon icon) {
        if (icon.getCategories() == null || icon.getCategories().isEmpty())
            throw new IllegalArgumentException("Couldn't assign species. Category is missing.");

        icon.setSpecies("Homo sapiens");
        if (icon.getCategories().contains(new Category("arrow")) || icon.getCategories().contains(new Category("compound"))) {
            icon.setSpecies("Entries without species");
        }
    }

    private void validateReferences(Icon icon) throws InvalidObjectException {
        List<Reference> references = icon.getReferences();
        if (references == null) {
            logger.error("The file " + icon.getStId() + " (category: " + icon.getCategories().toString() + ") doesn't contain any reference.");
        } else {
            for (Reference reference : references) {
                if ((reference.getDb().equalsIgnoreCase("UNIPROT") ||
                        reference.getDb().equalsIgnoreCase("KEGG") ||
                        reference.getDb().equalsIgnoreCase("MESH") ||
                        reference.getDb().equalsIgnoreCase("INTERPRO")) && reference.getId().contains(":")) {
                    throw new InvalidObjectException("Potential Invalid reference. It contains colon where it shouldn't have [File: " + icon.getStId() + " RefId: " + reference.getId() + ", RefDB: " + reference.getDb() + "]");
                }
            }
        }
    }

    /**
     * @param icon the icon
     * @return stIds where the icon is present
     */
    private List<String> getEhlds(Icon icon) {
        List<String> ehlds = new ArrayList<>();
        String escapedFileName = StringEscapeUtils.escapeXml11(icon.getStId());
        String quotedFilename = Pattern.quote(escapedFileName);
        // Grep command works differently in Linux and Mac, so we need to detect OS and apply the proper grep command
        String os = System.getProperty("os.name").toLowerCase();
        String command = "grep -i -l -###OS### 'id=\"" + quotedFilename + "\"|data-name=\"" + quotedFilename + "\"' " + ehldsDir + "/*.svg | awk -F/ '{print $NF}'";
        String grepPattern = "P"; // works on linux
        if (os.contains("mac")) grepPattern = "E"; // works on MacOS
        command = command.replace("###OS###", grepPattern);
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        try {
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String readline;
            while ((readline = reader.readLine()) != null) {
                if (readline.startsWith("R-")) ehlds.add(readline.trim().replace(".svg", ""));
            }
            p.destroyForcibly();
        } catch (IOException e) {
            e.printStackTrace();
            //parserLogger.error("Error while getting the EHLDs for the file {}/{}", group, fileNameWithoutExtension);
        }

        if (ehlds.isEmpty()) {
            if (icon.isSkip() != null && !icon.isSkip()) {
                parserMessages.add(icon.getStId() + " (" + icon.getName() + ")");
            }
        } else if (icon.isSkip() != null && icon.isSkip()) {
            parserMessages.add("This is flagged to be skipped and was found in a EHLD, please fix its metadata: " + icon.getStId());
        }

        return ehlds;
    }

    /**
     * Also invoke the parser, if icons list is Empty.
     *
     * @return icons
     */
    public List<Icon> getIcons() {
        if (icons == null || icons.isEmpty()) parse();
        return icons;
    }
}
