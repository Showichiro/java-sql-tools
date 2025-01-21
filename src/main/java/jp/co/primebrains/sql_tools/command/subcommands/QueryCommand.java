package jp.co.primebrains.sql_tools.command.subcommands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.sql.DataSource;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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

    @Option(names = { "-f", "--format" }, description = "Output format (csv/excel/yaml)", defaultValue = "csv")
    private OutputFormat format;

    @Option(names = { "-p", "--params" }, description = "Query parameters in key=value format (e.g. -p age=30 -p name=John)", split = ",")
    private Map<String, String> params = new LinkedHashMap<>();

    private enum OutputFormat {
        csv, excel, yaml
    }

    @Parameters(arity = "1..*", description = "SQL file paths")
    private List<File> sqlFiles;

    @Autowired
    private DatabaseConfig databaseConfig;

    @Autowired
    private SQLFileService sqlFileService;

    private NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Executes the query command workflow, processing SQL files and exporting results.
     *
     * This method performs the following key steps:
     * 1. Loads database configuration from the specified configuration file
     * 2. Creates the output directory if it does not exist
     * 3. Sets up a database connection using the loaded configuration
     * 4. Processes each provided SQL file, executing queries and exporting results
     *
     * @return 0 if all SQL files are processed successfully, 1 if an error occurs
     * @throws Exception if there are issues with configuration loading, database connection, or file processing
     */
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

    /**
     * Sets up a database connection using the specified database configuration.
     *
     * Retrieves database properties for the given database key from the configuration, creates a DataSource
     * with the specified connection details, and initializes a NamedParameterJdbcTemplate for executing
     * parameterized database queries.
     *
     * @throws IllegalArgumentException if no database configuration is found for the specified database key
     * @see DatabaseConfig
     * @see NamedParameterJdbcTemplate
     */
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

        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * Processes a SQL file by executing its SQL statements and exporting the results.
     *
     * This method performs the following steps for each SQL statement in the provided file:
     * 1. Parse SQL statements from the input file
     * 2. Generate a unique output filename using the base SQL filename, current timestamp, and statement index
     * 3. Execute the SQL query and retrieve results
     * 4. Export the query results to the specified output format (CSV, Excel, or YAML)
     *
     * @param sqlFile The SQL file containing SQL statements to be processed
     * @throws Exception If any errors occur during SQL file parsing, query execution, or result export
     *                   including file access issues, database connection problems, or export failures
     *
     * @see SQLFileService#parseSqlFile(String)
     * @see #executeQuery(String)
     * @see OutputFormat
     */
    private void processSqlFile(File sqlFile) throws Exception {
        List<String> sqlStatements = sqlFileService.parseSqlFile(sqlFile.getPath());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String baseFileName = sqlFile.getName().replaceAll("\\.sql$", "");

        for (int i = 0; i < sqlStatements.size(); i++) {
            String sql = sqlStatements.get(i);
            String outputFileName = String.format("%s_%s_%d.%s",
                    baseFileName, timestamp, i + 1,
                    getFileExtension(format));
            File outputFile = new File(outputDir, outputFileName);

            List<List<String>> results = executeQuery(sql);

            switch (format) {
                case csv:
                    exportToCsv(results, outputFile);
                    break;
                case excel:
                    exportToExcel(results, outputFile);
                    break;
                case yaml:
                    exportToYaml(results, outputFile);
                default:
                    break;
            }
        }
    }

    /**
     * Determines the appropriate file extension for a given output format.
     *
     * @param format The output format for which to retrieve the file extension
     * @return The corresponding file extension as a string
     * @throws IllegalArgumentException If an unsupported output format is provided
     */
    private String getFileExtension(OutputFormat format) {
        return switch (format) {
            case csv -> "csv";
            case excel -> "xlsx";
            case yaml -> "yaml";
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        };
    }

    /**
     * Executes a SQL query and retrieves results as a list of lists, supporting both parameterized and non-parameterized queries.
     *
     * @param sql The SQL query to execute, with optional named parameters
     * @return A list of lists representing query results, where the first list contains column headers
     *         and subsequent lists contain data rows. Returns an empty list if no results are found.
     * @throws Exception If there is an error during database connection, query execution, or result retrieval
     *
     * @implNote This method handles two query execution scenarios:
     *           1. Non-parameterized queries when no parameters are provided
     *           2. Parameterized queries using named parameters from the {@code params} map
     */
    private List<List<String>> executeQuery(String sql) throws Exception {
        List<List<String>> results = new ArrayList<>();
        String cleanSql = sql.replace(";", "");
        
        List<Map<String, Object>> rows;
        if (params.isEmpty()) {
            // Execute non-parameterized query
            rows = jdbcTemplate.getJdbcOperations().queryForList(cleanSql);
        } else {
            // Convert params Map<String, String> to Map<String, Object>
            Map<String, Object> paramMap = new LinkedHashMap<>(params);
            // Execute parameterized query
            rows = jdbcTemplate.queryForList(cleanSql, paramMap);
        }
        
        if (!rows.isEmpty()) {
            // Add headers from first row's keys
            List<String> headers = new ArrayList<>(rows.get(0).keySet());
            results.add(headers);
            
            // Add data rows
            for (Map<String, Object> row : rows) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    Object value = row.get(header);
                    values.add(value != null ? value.toString() : "");
                }
                results.add(values);
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

    /**
     * Exports query results to an Excel file using Apache POI.
     *
     * @param results A list of lists representing rows and columns of query results
     * @param outputFile The target Excel file to write the results
     * @throws Exception If there are issues creating or writing to the Excel file
     *
     * @implNote This method creates an Excel workbook with a single sheet named "Query Results"
     * and populates it with the provided data. Each list in the results represents a row,
     * and each element in the row list represents a cell value.
     */
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

    /**
     * Exports query results to a YAML file with structured metadata.
     *
     * @param results A list of lists representing query results, where the first row contains column headers
     * @param outputFile The target file to write the YAML export
     * @throws Exception If an error occurs during YAML file generation
     *
     * @implNote This method converts query results into a structured YAML format with:
     * - Total number of rows
     * - Column names
     * - Data rows as key-value mappings
     * - Generation timestamp
     */
    private void exportToYaml(List<List<String>> results, File outputFile) throws Exception {
        if (results.isEmpty()) {
            return;
        }

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // ヘッダー行を取得
        List<String> headers = results.get(0);

        // データ行をMap形式に変換
        List<Map<String, String>> dataRows = new ArrayList<>();
        for (int i = 1; i < results.size(); i++) {
            List<String> row = results.get(i);
            Map<String, String> rowMap = new LinkedHashMap<>();

            for (int j = 0; j < headers.size() && j < row.size(); j++) {
                rowMap.put(headers.get(j), row.get(j));
            }

            dataRows.add(rowMap);
        }

        // 結果をYAMLとして出力
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("query_result", new LinkedHashMap<String, Object>() {
            {
                put("total_rows", dataRows.size());
                put("columns", headers);
                put("rows", dataRows);
                put("generated_at", LocalDateTime.now().toString());
            }
        });

        yamlMapper.writeValue(outputFile, output);
    }
}
