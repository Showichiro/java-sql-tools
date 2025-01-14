package jp.co.primebrains.sql_tools.command.subcommands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.sql.DataSource;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.opencsv.CSVWriter;

import jp.co.primebrains.sql_tools.config.DatabaseConfig;
import jp.co.primebrains.sql_tools.service.SQLFileService;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Component
@Slf4j
@Command(name = "query", mixinStandardHelpOptions = true, description = "Execute SQL query and export results")
public class QueryCommand implements Callable<Integer> {

    @Option(names = { "-h", "--help" }, description = "show this help", usageHelp = true)
    boolean showHelp;

    @Option(names = { "-d", "--database" }, description = "Database configuration key", defaultValue = "default")
    private String databaseKey;

    @Option(names = { "-c", "--config" }, description = "Path to database configuration file", required = true)
    private File configFile;

    @Option(names = { "-o", "--output-dir" }, description = "Output directory", defaultValue = "./output")
    private File outputDir;

    @Option(names = { "-f", "--format" }, description = "Output format (csv/excel)", defaultValue = "csv")
    private String format;

    @Parameters(arity = "1..*", description = "SQL file paths")
    private List<File> sqlFiles;

    @Autowired
    private DatabaseConfig databaseConfig;

    @Autowired
    private SQLFileService sqlFileService;

    private JdbcTemplate jdbcTemplate;

    @Override
    public Integer call() throws Exception {
        try {

            // Load database configuration
            databaseConfig.loadConfig(configFile);

            // Create output directory if it doesn't exist
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // Setup database connection
            setupDatabaseConnection();

            // Process each SQL file
            for (File sqlFile : sqlFiles) {
                processSqlFile(sqlFile);
            }

            return 0;
        } catch (Exception e) {
            log.error("Error: " + e.getMessage(), e);
            return 1;
        }
    }

    private void setupDatabaseConnection() {
        DatabaseConfig.DatabaseProperties dbProps = databaseConfig.getDatabases().get(databaseKey);
        if (dbProps == null) {
            throw new IllegalArgumentException("Database configuration not found for key: " + databaseKey);
        }

        DataSource dataSource = DataSourceBuilder.create()
                .url(dbProps.getUrl())
                .username(dbProps.getUsername())
                .password(dbProps.getPassword())
                .build();

        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    private void processSqlFile(File sqlFile) throws Exception {
        List<String> sqlStatements = sqlFileService.parseSqlFile(sqlFile.getPath());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String baseFileName = sqlFile.getName().replaceAll("\\.sql$", "");

        for (int i = 0; i < sqlStatements.size(); i++) {
            String sql = sqlStatements.get(i);
            String outputFileName = String.format("%s_%s_%d.%s",
                    baseFileName, timestamp, i + 1,
                    format.toLowerCase().equals("excel") ? "xlsx" : "csv");
            File outputFile = new File(outputDir, outputFileName);

            List<List<String>> results = executeQuery(sql);

            if ("csv".equalsIgnoreCase(format)) {
                exportToCsv(results, outputFile);
            } else if ("excel".equalsIgnoreCase(format)) {
                exportToExcel(results, outputFile);
            }
        }
    }

    private List<List<String>> executeQuery(String sql) throws Exception {
        List<List<String>> results = new ArrayList<>();

        try (Connection conn = jdbcTemplate.getDataSource().getConnection();
                var stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql.replace(";", ""))) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Add headers
            List<String> headers = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                headers.add(metaData.getColumnName(i));
            }
            results.add(headers);

            // Add data rows
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getString(i));
                }
                results.add(row);
            }
        }

        return results;
    }

    private void exportToCsv(List<List<String>> results, File outputFile) throws Exception {
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputFile))) {
            for (List<String> row : results) {
                writer.writeNext(row.toArray(new String[0]));
            }
        }
    }

    private void exportToExcel(List<List<String>> results, File outputFile) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Query Results");

            for (int i = 0; i < results.size(); i++) {
                Row row = sheet.createRow(i);
                List<String> rowData = results.get(i);

                for (int j = 0; j < rowData.size(); j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(rowData.get(j));
                }
            }

            try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                workbook.write(fileOut);
            }
        }
    }
}
