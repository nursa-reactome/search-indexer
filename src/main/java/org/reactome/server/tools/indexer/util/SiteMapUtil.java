package org.reactome.server.tools.indexer.util;

import com.martiansoftware.jsap.*;
import org.reactome.server.graph.domain.model.DatabaseObject;
import org.reactome.server.graph.domain.model.Event;
import org.reactome.server.graph.domain.model.PhysicalEntity;
import org.reactome.server.graph.service.SchemaService;
import org.reactome.server.graph.utils.ReactomeGraphCore;
import org.reactome.server.tools.indexer.config.IndexerNeo4jConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

/**
 * Generates a sitemap based on PhysicalEntities, Events and Regulations (aprox. 687K links)
 * 1) Upload sitemapindex.txt to Website/static/
 * 2) Upload *txt.gz to Website/static/sitemap/
 * 3) Make sure owner and permissions are set.
 * 4) robots.txt is aware of the sitemapindex.xml
 * 5) Optional: Google Webmaster Tool - reindex.
 * <p>
 * Useful links: https://support.google.com/webmasters/answer/183668?hl=en&ref_topic=4581190
 * https://www.sitemaps.org/protocol.html
 *
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
public class SiteMapUtil {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");

    private final static String BASE_URL = "https://reactome.org/";
    private final static String DETAIL_URL = BASE_URL + "content/detail/";

    private Set<String> sitemapFiles = new TreeSet<>();
    private String outputPath;
    private AnnotationConfigApplicationContext neo4jContext;
    private SchemaService schemaService;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public SiteMapUtil(String outputPath) {
        this.outputPath = outputPath;
        File dirs = new File(outputPath);
        dirs.mkdirs();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public SiteMapUtil(AnnotationConfigApplicationContext neo4jContext, String outputPath) {
        this.outputPath = outputPath;
        this.schemaService = neo4jContext.getBean(SchemaService.class);
        File dirs = new File(outputPath);
        dirs.mkdirs();
    }

    public static void main(String[] args) throws Exception {
        SimpleJSAP jsap = new SimpleJSAP(SiteMapUtil.class.getName(), "A tool for generating a sitemap.",
                new Parameter[]{
                        new FlaggedOption("host", JSAP.STRING_PARSER, "localhost", JSAP.NOT_REQUIRED, 'a', "host", "The neo4j host"),
                        new FlaggedOption("port", JSAP.STRING_PARSER, "7474", JSAP.NOT_REQUIRED, 'b', "port", "The neo4j port"),
                        new FlaggedOption("user", JSAP.STRING_PARSER, "neo4j", JSAP.NOT_REQUIRED, 'c', "user", "The neo4j user"),
                        new FlaggedOption("password", JSAP.STRING_PARSER, "neo4jj", JSAP.NOT_REQUIRED, 'd', "password", "The neo4j password")
                }
        );

        JSAPResult config = jsap.parse(args);
        if (jsap.messagePrinted()) System.exit(1);

        ReactomeGraphCore.initialise(config.getString("host"), config.getString("port"), config.getString("user"), config.getString("password"), IndexerNeo4jConfig.class);

        SiteMapUtil smu = new SiteMapUtil(".");
        smu.setSchemaService(ReactomeGraphCore.getService(SchemaService.class));
        smu.generate();
    }

    /**
     * Generating massive sitemap.txt
     * Source: https://support.google.com/webmasters/answer/183668?hl=en&ref_topic=4581190
     * Warning: Cannot use sitemap.txt/xml directly
     * All formats limit a single sitemap to 50MB (uncompressed) and 50,000 URLs.
     */
    private int write(BufferedWriter bw, Class<? extends DatabaseObject> clazz) throws IOException {
        Collection<String> allOfGivenClass = schemaService.getStIdsByClass(clazz);
        int count = 0;
        for (String stId : allOfGivenClass) {
            if(stId.startsWith("R-HSA-")) {
                count++;
                bw.write(DETAIL_URL + stId);
                bw.newLine();
                if (count % 10000 == 0) {
                    bw.flush();
                }
            }
        }
        bw.flush();
        return count;
    }

    public void generate() {
        try {
            Path file = Paths.get(outputPath + File.separator + "sitemap.txt");
            file.toFile().deleteOnExit();
            BufferedWriter bw = Files.newBufferedWriter(file, Charset.forName("UTF-8"));
            int total = 0;
            total += write(bw, PhysicalEntity.class);
            total += write(bw, Event.class);

            bw.flush();
            bw.close();

            split(file);
            compress();
            mapping();

            logger.info("Sitemap generated {} lines", total);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Splitting sitemap.txt into 50000 lines files
     */
    private void split(Path file) {
        // Sitemap has a limitation of 50MB or 50.000 URLs per file.
        int count = 1;
        int linesWritten = 0;
        final int linesPerSplit = 50000;
        try {
            BufferedReader reader = Files.newBufferedReader(file, Charset.forName("UTF-8"));
            String line = reader.readLine();
            String fileName = file.getFileName().toString().replaceFirst("[.][^.]+$", "");
            while (line != null) {
                File outFile = new File(outputPath + File.separator + fileName + "_" + count + ".txt");
                Writer writer = new OutputStreamWriter(new FileOutputStream(outFile));

                while (line != null && linesWritten < linesPerSplit) {
                    writer.write(line + "\n");
                    line = reader.readLine();
                    linesWritten++;
                }
                writer.flush();
                writer.close();
                linesWritten = 0;//next file
                count++;//next file count
                sitemapFiles.add(outFile.getName());
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Compressing sitemap_XX.txt
     */
    private void compress() throws IOException {
        for (String sitemap : sitemapFiles) {
            File input = new File(outputPath + File.separator + sitemap);
            input.deleteOnExit();
            String outputGZ = outputPath + File.separator + sitemap + ".gz";
            try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(outputGZ))) {
                try (FileInputStream in = new FileInputStream(input)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    /**
     * Creating sitemapindex.xml.
     * This file shows the location of all the mapping files.
     * Format: https://www.sitemaps.org/protocol.html
     */
    private void mapping() throws IOException {
        Path file = Paths.get(outputPath + File.separator + "sitemapindex.xml");
        BufferedWriter bw = Files.newBufferedWriter(file, Charset.forName("UTF-8"));
        bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        bw.write("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String sitemap : sitemapFiles) {
            bw.write("\t<sitemap>\n");
            bw.write("\t\t<loc>" + BASE_URL + "sitemap/" + sitemap + ".gz</loc>\n");
            bw.write("\t</sitemap>\n");
        }
        bw.write("</sitemapindex>");
        bw.flush();
        bw.close();
    }

    private void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }
}
