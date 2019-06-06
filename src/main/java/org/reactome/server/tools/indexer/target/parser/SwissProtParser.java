package org.reactome.server.tools.indexer.target.parser;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.reactome.server.tools.indexer.target.model.Target;
import org.reactome.server.tools.indexer.target.model.TargetResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */

@SuppressWarnings("ALL")
public class SwissProtParser {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    private static String GZ_FILE_URL = "ftp://ftp.uniprot.org/pub/databases/uniprot/current_release/knowledgebase/taxonomic_divisions/uniprot_sprot_human.dat.gz";
    private static String GZ_FILE = "/tmp/uniprot_sprot_human.dat.gz";
    private static String SWISS_PROT_FILE = "/tmp/uniprot_sprot_human.dat";
    private static String SIMPLIFIED_FILE = "/tmp/uniprot_sprot_human_simplified.txt";

    private static SwissProtParser instance;
    private List<Target> targets = new ArrayList<>();

    private SwissProtParser() {}

    public static SwissProtParser getInstance() {
        if (instance == null) {
            instance = new SwissProtParser();
        }
        return instance;
    }

    public static void main(String[] args) throws Exception {
        SwissProtParser s = new SwissProtParser();
        s.downloadFile();
        s.decompressGZIP();
        s.grep();
        s.parse();
        s.deleteFiles();
    }

    public List<Target> getTargets() {
        if(!targets.isEmpty()) {
            return targets;
        }
        downloadFile();
        decompressGZIP();
        grep();
        parse();
        deleteFiles();
        return targets;
    }

    private void downloadFile() {
        try {
            FileUtils.copyURLToFile(new URL(GZ_FILE_URL), new File(GZ_FILE));
        } catch (IOException e) {
            logger.error("Swissprot file can't be downloaded", e);
        }
    }

    private void decompressGZIP() {
        try {
            GzipCompressorInputStream in = new GzipCompressorInputStream(new FileInputStream(GZ_FILE));
            IOUtils.copy(in, new FileOutputStream(SWISS_PROT_FILE));
        } catch (IOException e) {
            logger.error("Could not decompress " + GZ_FILE, e);
        }
    }

    /**
     * Grep the necessary information and save in the simplified file
     */
    private void grep() {
        final String command = "cat " + SWISS_PROT_FILE + " | grep '^AC\\|^ID\\|^GN\\|^\\/\\/'";
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
        pb.inheritIO();
        pb.redirectOutput(new File(SIMPLIFIED_FILE));
        final File errorFile = new File("/tmp/redirectError.err");
        errorFile.deleteOnExit();
        pb.redirectError(errorFile);
        try {
            Process p = pb.start();
            p.waitFor();
            if (p.exitValue() != 0 || errorFile.length() > 0) {
                logger.error("Couldn't create simplified file. Reason: " + FileUtils.readFileToString(errorFile, Charset.defaultCharset()).trim());
            }
        } catch (InterruptedException e) {
            logger.error("Current thread is interrupted", e);
        } catch (IOException e) {
            logger.error("I/O error occurred", e);
        }
    }

    private void parse() {
        List<String> lines = null;

        try {
            lines = FileUtils.readLines(new File(SIMPLIFIED_FILE), Charset.defaultCharset());
        } catch (IOException e) {
            logger.error("Couldn't read the normalised file", e);
        }

        StringBuffer entry = new StringBuffer();
        for (String line : lines) {
            Target target = new Target(TargetResource.UNIPROT.name());
            if (!line.startsWith("//")) {
                entry.append(line);
                entry.append("\n");
                continue;
            }

            Pattern idPattern = Pattern.compile("(^ID\\s+)(.+)(?:Reviewed)", Pattern.MULTILINE);
            Matcher idMatcher = idPattern.matcher(entry.toString());
            while (idMatcher.find()) {
                target.setIdentifier(idMatcher.group(2).trim());
            }

            Pattern acPattern = Pattern.compile("(^AC\\s+)(.+)", Pattern.MULTILINE);
            Matcher acMatcher = acPattern.matcher(entry.toString());
            while (acMatcher.find()) {
                target.setAccessions(Arrays.asList(acMatcher.group(2).split(";")));
            }

            Pattern gnPattern = Pattern.compile("(^GN\\s+)(.+)", Pattern.MULTILINE);
            Matcher gnMatcher = gnPattern.matcher(entry.toString());
            String gn = "";
            while (gnMatcher.find()) {
                gn += gnMatcher.group(2);
            }

            Pattern namePattern = Pattern.compile("(Name=)(.+?)[;]");
            Matcher nameMatcher = namePattern.matcher(gn);
            while (nameMatcher.find()) {
                String name = nameMatcher.group(2).replaceAll("\\{.*?}", "").replaceAll("\\s+;", ";").replaceAll("\\s+,", ";").replaceAll(",", ";");
                target.addGene(name);
            }

            Pattern synonymPattern = Pattern.compile("(Synonyms=)(.+?)[;]");
            Matcher synonymMatcher = synonymPattern.matcher(gn);
            while (synonymMatcher.find()) {
                String cleanSynonyms = synonymMatcher.group(2).replaceAll("\\{.*?}", "").replaceAll("\\s+;", ";").replaceAll("\\s+,", ";").replaceAll(",", ";");
                target.setSynonyms(Arrays.asList(cleanSynonyms.split(";")));
            }

            targets.add(target);
            entry = new StringBuffer();
        }
    }

    private void deleteFiles() {
        try {
            FileUtils.forceDeleteOnExit(new File(GZ_FILE));
            FileUtils.forceDeleteOnExit(new File(SWISS_PROT_FILE));
            FileUtils.forceDeleteOnExit(new File(SIMPLIFIED_FILE));
        } catch (IOException e) {
            logger.error("Couldn't delete files on exit", e);
        }
    }
}



