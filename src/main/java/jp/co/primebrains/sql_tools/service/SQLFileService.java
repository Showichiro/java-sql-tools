package jp.co.primebrains.sql_tools.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class SQLFileService {
    public List<String> parseSqlFile(String filePath) throws IOException {
        List<String> sqlStatements = new ArrayList<>();
        StringBuilder currentStatement = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip comments and empty lines
                if (line.trim().startsWith("--") || line.trim().isEmpty()) {
                    continue;
                }

                currentStatement.append(line).append(" ");

                if (line.trim().endsWith(";")) {
                    sqlStatements.add(currentStatement.toString().trim());
                    currentStatement = new StringBuilder();
                }
            }

            // Add the last statement if it doesn't end with a semicolon
            if (currentStatement.length() > 0) {
                sqlStatements.add(currentStatement.toString().trim());
            }
        }

        return sqlStatements;
    }
}
