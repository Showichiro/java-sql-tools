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
        boolean inPlSqlBlock = false;
        boolean inQuote = false;
        char quoteChar = 0;
        int lineNumber = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmedLine = line.trim();
                
                // Skip empty lines and comments
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("--")) {
                    continue;
                }
                
                // Process the line character by character
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    
                    // Handle quotes
                    if ((c == '\'' || c == '"') && (i == 0 || line.charAt(i - 1) != '\\')) {
                        if (!inQuote) {
                            inQuote = true;
                            quoteChar = c;
                        } else if (c == quoteChar) {
                            inQuote = false;
                        }
                    }
                    
                    // Check for PL/SQL block start/end
                    if (!inQuote) {
                        if (trimmedLine.toLowerCase().startsWith("begin") || 
                            trimmedLine.toLowerCase().startsWith("declare")) {
                            inPlSqlBlock = true;
                        } else if (trimmedLine.toLowerCase().equals("end;")) {
                            inPlSqlBlock = false;
                        }
                    }
                    
                    currentStatement.append(c);
                    
                    // Check for statement end
                    if (c == ';' && !inQuote && !inPlSqlBlock) {
                        String sql = currentStatement.toString().trim();
                        if (!sql.isEmpty()) {
                            sqlStatements.add(sql);
                        }
                        currentStatement = new StringBuilder();
                    }
                }
                
                // Add newline for better formatting
                currentStatement.append("\n");
            }
            
            // Handle the last statement if it doesn't end with a semicolon
            String finalStatement = currentStatement.toString().trim();
            if (!finalStatement.isEmpty()) {
                // Warn about missing semicolon
                System.out.printf("Warning: Statement ending at line %d does not end with a semicolon. Adding it automatically.%n", lineNumber);
                finalStatement += ";";
                sqlStatements.add(finalStatement);
            }
        }
        
        return sqlStatements;
    }
}
