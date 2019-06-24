package com.instaclustr.sstabletools;

import com.instaclustr.sstabletools.cassandra.CassandraBackend;
import com.instaclustr.sstabletools.cassandra.CassandraSchema;
import org.apache.commons.cli.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.util.Date;
import java.util.List;

/**
 * Display summary about column families.
 */
public class SummaryCollector {
    private static final String HELP_OPTION = "h";
    private static final String SCHEMA_OPTION = "s";

    private static final Options options = new Options();
    private static CommandLine cmd;

    private static void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("ic-summary", "Summary information about all column families including how much of the data is repaired", options, null);
    }

    static {
        Option optSchema = new Option(SCHEMA_OPTION, "schema", true, "Load the schema from a YAML definition instead of from disk");
        options.addOption(optSchema);
    }

    public static void main(String[] args) {
        try {
            CommandLineParser parser = new PosixParser();
            try {
                cmd = parser.parse(options, args);
            } catch (ParseException e) {
                System.err.println(e.getMessage());
                printHelp();
                System.exit(1);
            }

            if (cmd.hasOption(HELP_OPTION)) {
                printHelp();
                System.exit(0);
            }

            CassandraSchema schema = null;
            if (cmd.hasOption(SCHEMA_OPTION)) {
                File file = new File(cmd.getOptionValue(SCHEMA_OPTION));
                FileInputStream fileInputStream = new FileInputStream(file);

                schema = new Yaml().loadAs(fileInputStream, CassandraSchema.class);
            }

            TableBuilder tb = new TableBuilder();
            tb.setHeader(
                    "Keyspace",
                    "Column Family",
                    "SSTables",
                    "Disk Size",
                    "Data Size",
                    "Last Repaired",
                    "Repair %"
            );

            CassandraProxy backend = CassandraBackend.getInstance(schema);
            for (String ksName : backend.getKeyspaces()) {
                for (String cfName : backend.getColumnFamilies(ksName)) {
                    List<SSTableMetadata> metadataCollection = CassandraBackend.getInstance(schema).getSSTableMetadata(ksName, cfName);
                    long diskSize = 0;
                    long dataSize = 0;
                    long repairedAt = Long.MIN_VALUE;
                    long repaired = 0;
                    long repairedLength = 0;
                    for (SSTableMetadata metadata : metadataCollection) {
                        diskSize += metadata.diskLength;
                        dataSize += metadata.uncompressedLength;
                        if (metadata.isRepaired) {
                            repaired++;
                            repairedAt = Math.max(repairedAt, metadata.repairedAt);
                            repairedLength += metadata.uncompressedLength;
                        }
                    }
                    tb.addRow(
                            ksName,
                            cfName,
                            Integer.toString(metadataCollection.size()),
                            Util.humanReadableByteCount(diskSize),
                            Util.humanReadableByteCount(dataSize),
                            repaired > 0 ? Util.UTC_DATE_FORMAT.format(new Date(repairedAt)) : "",
                            repaired > 0 ? String.format("%d/%d %d%%", repaired, metadataCollection.size(), (int) Math.floor((repairedLength / (double) dataSize) * 100)) : ""
                    );
                }
            }

            System.out.println(tb);

            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

}
