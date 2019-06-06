package org.reactome.server.tools.indexer;

import com.martiansoftware.jsap.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.reactome.server.tools.indexer.config.IndexerNeo4jConfig;
import org.reactome.server.tools.indexer.exception.IndexerException;
import org.reactome.server.tools.indexer.icon.exporter.IconsExporter;
import org.reactome.server.tools.indexer.icon.impl.IconIndexer;
import org.reactome.server.tools.indexer.impl.Indexer;
import org.reactome.server.tools.indexer.target.impl.TargetIndexer;
import org.reactome.server.tools.indexer.util.MailUtil;
import org.reactome.server.tools.indexer.util.SiteMapUtil;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

import static org.reactome.server.tools.indexer.util.SolrUtility.closeSolrServer;
import static org.reactome.server.tools.indexer.util.SolrUtility.getSolrClient;

/**
 * @author Guilherme S Viteri <gviteri@ebi.ac.uk>
 */
@SuppressWarnings("Duplicates")
@Component
public class Main {

    private static final String FROM = "reactome-indexer@reactome.org";
    private static final String DEF_MAIL_SMTP = "smtp.oicr.on.ca";
    private static final String DEF_SOLR_URL = "http://localhost:8983/solr/";
    private static final String DEF_SOLR_CORE = "reactome";
    private static final String MAIL_SUBJECT_SUCCESS = "[Search Indexer] The Solr indexer has been created";
    private static final String MAIL_SUBJECT_ERROR = "[SearchIndexer] The Solr indexer has thrown exception";

    public static void main(String[] args) throws JSAPException {

        long startTime = System.currentTimeMillis();

        SimpleJSAP jsap = new SimpleJSAP(Main.class.getName(), "A tool for generating a Solr Index.",
                new Parameter[]{
                        new FlaggedOption("neo4jHost", JSAP.STRING_PARSER, "localhost", JSAP.NOT_REQUIRED, 'a', "neo4jHost", "The neo4j host"),
                        new FlaggedOption("neo4jPort", JSAP.STRING_PARSER, "7474", JSAP.NOT_REQUIRED, 'b', "neo4jPort", "The neo4j port"),
                        new FlaggedOption("neo4jUser", JSAP.STRING_PARSER, "neo4j", JSAP.NOT_REQUIRED, 'c', "neo4jUser", "The neo4j user"),
                        new FlaggedOption("neo4jPw", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'd', "neo4jPw", "The neo4j password"),
                        new FlaggedOption("solrUrl", JSAP.STRING_PARSER, DEF_SOLR_URL, JSAP.REQUIRED, 'e', "solrUrl", "Url of the running Solr server"),
                        new FlaggedOption("solrCore", JSAP.STRING_PARSER, DEF_SOLR_CORE, JSAP.REQUIRED, 'o', "solrCore", "The Reactome solr core"),
                        new FlaggedOption("solrUser", JSAP.STRING_PARSER, "admin", JSAP.NOT_REQUIRED, 'f', "solrUser", "The Solr user"),
                        new FlaggedOption("solrPw", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'g', "solrPw", "The Solr password"),
                        new FlaggedOption("mailSmtp", JSAP.STRING_PARSER, DEF_MAIL_SMTP, JSAP.NOT_REQUIRED, 'i', "mailSmtp", "SMTP Mail host"),
                        new FlaggedOption("mailPort", JSAP.INTEGER_PARSER, "25", JSAP.NOT_REQUIRED, 'j', "mailPort", "SMTP Mail port"),
                        new FlaggedOption("mailDest", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'k', "mailDest", "Mail Destination"),
                        new FlaggedOption("ebeyexml", JSAP.BOOLEAN_PARSER, "true", JSAP.NOT_REQUIRED, 'l', "ebeyexml", "XML output file for the EBeye."),
                        new FlaggedOption("sitemap", JSAP.BOOLEAN_PARSER, "true", JSAP.NOT_REQUIRED, 'n', "sitemap", "Generates sitemap."),
                        new FlaggedOption("target", JSAP.BOOLEAN_PARSER, "true", JSAP.NOT_REQUIRED, 'p', "target", "Generates Swissprot-based target Solr core."),
                        new FlaggedOption("iconsDir", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'q', "iconsDir", "The directory where all ICONS (R-ICO-*) reside"),
                        new FlaggedOption("ehldDir", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'r', "ehldDir", "The directory where all EHLDs reside")
                }
        );

        JSAPResult config = jsap.parse(args);
        if (jsap.messagePrinted()) System.exit(1);

        //  Reactome Solr properties for solr connection ** Collection (core) has to be passed
        SolrClient solrClient = getSolrClient(config.getString("solrUser"), config.getString("solrPw"), config.getString("solrUrl"));
        AnnotationConfigApplicationContext ctx = getNeo4jContext(config.getString("neo4jHost"), config.getString("neo4jPort"), config.getString("neo4jUser"), config.getString("neo4jPw"));

        String solrCore = config.getString("solrCore"); // for reactome normal search
        String mailDest = config.getString("mailDest");
        String smtpServer = config.getString("mailSmtp");
        int smtpPort = config.getInt("mailPort");
        boolean sendmail = !StringUtils.isEmpty(mailDest);
        boolean siteMap = config.getBoolean("sitemap");
        boolean ebeyexml = config.getBoolean("ebeyexml");
        boolean target = config.getBoolean("target");
        String iconsDir = config.getString("iconsDir");
        String ehldDir = config.getString("ehldDir");

        try {
            Indexer indexer = ctx.getBean(Indexer.class);
            indexer.setSolrClient(solrClient);
            indexer.setSolrCore(solrCore);
            indexer.setEbeyeXml(ebeyexml);

            int entriesCount = indexer.index();

            if (iconsDir != null && ehldDir != null) {
                entriesCount += doIconIndexer(solrClient, solrCore, iconsDir, ehldDir);
                doIconsMappingFiles(solrClient, solrCore);
            }

            if (target) doTargetIndexer(solrClient, solrCore);
            if (siteMap) generateSitemap(ctx);

            if (sendmail) {
                MailUtil mailUtil = MailUtil.getInstance(smtpServer, smtpPort);
                long stopTime = System.currentTimeMillis();
                long ms = stopTime - startTime;
                long hour = TimeUnit.MILLISECONDS.toHours(ms);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(ms));
                long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(ms));
                // Send an notification by the end of indexing.
                mailUtil.send(FROM, mailDest, MAIL_SUBJECT_SUCCESS, "The Solr Indexer has written successfully " + entriesCount + " documents within: " + hour + "hour(s) " + minutes + "minute(s) " + seconds + "second(s) ");
            }
        } catch (IndexerException e) {
            if (sendmail) {
                MailUtil mailUtil = MailUtil.getInstance(smtpServer, smtpPort);
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String exceptionAsString = sw.toString();

                @SuppressWarnings("StringBufferReplaceableByString")
                StringBuilder body = new StringBuilder();
                body.append("The Solr Indexer has not finished properly. Please check the following exception.\n\n");
                body.append("Message: ").append(e.getMessage());
                body.append("\n");
                body.append("Cause: ").append(e.getCause());
                body.append("\n");
                body.append("Stacktrace: ").append(exceptionAsString);

                // Send an error notification by the end of indexer.
                mailUtil.send(FROM, mailDest, MAIL_SUBJECT_ERROR, body.toString());
            }
        } finally {
            closeSolrServer(solrClient);
        }
    }

    /**
     * Based on the arguments, set systemProperties and get a Neo4j context that already holds the connection with Neo4j
     *
     * @param host     neo4j host
     * @param port     neo4j port
     * @param user     neo4j user
     * @param password neo4j password
     * @return the applicationContext managed by Spring
     */
    private static AnnotationConfigApplicationContext getNeo4jContext(String host, String port, String user, String password) {
        // Set system properties that will be used by IndexerNeo4jConfig
        System.setProperty("neo4j.host", host);
        System.setProperty("neo4j.port", port);
        System.setProperty("neo4j.user", user);
        System.setProperty("neo4j.password", password);

        return new AnnotationConfigApplicationContext(IndexerNeo4jConfig.class); // Use annotated beans from the specified package
    }

    private static void generateSitemap(AnnotationConfigApplicationContext ctx) {
        SiteMapUtil smg = new SiteMapUtil(ctx, ".");
        smg.generate();
    }

    private static void doTargetIndexer(SolrClient solrClient, String solrCore) throws IndexerException {
        TargetIndexer targetIndexer = new TargetIndexer(solrClient, solrCore);
        targetIndexer.index();
    }

    private static Integer doIconIndexer(SolrClient solrClient, String solrCore, String iconsLib, String ehldDir) throws IndexerException {
        IconIndexer iconIndexer = new IconIndexer(solrClient, solrCore, iconsLib, ehldDir);
        return iconIndexer.indexIcons();
    }

    private static void doIconsMappingFiles(SolrClient solrClient, String solrCore) throws IndexerException {
        IconsExporter tsvWriter = new IconsExporter(solrClient, solrCore);
        tsvWriter.write(".");
    }
}
