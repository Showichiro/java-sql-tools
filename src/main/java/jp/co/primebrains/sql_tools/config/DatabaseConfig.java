package jp.co.primebrains.sql_tools.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.Data;

@Data
@Component
public class DatabaseConfig {
    private Map<String, DatabaseProperties> databases = new HashMap<>();

    @Data
    public static class DatabaseProperties {
        private String url;
        private String username;
        private String password;
    }

    public void loadConfig(File configFile) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        @SuppressWarnings("unchecked")
        Map<String, Object> config = mapper.readValue(configFile, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> dbConfig = (Map<String, Map<String, String>>) config.get("databases");

        databases.clear();
        for (Map.Entry<String, Map<String, String>> entry : dbConfig.entrySet()) {
            DatabaseProperties props = new DatabaseProperties();
            props.setUrl(entry.getValue().get("url"));
            props.setUsername(entry.getValue().get("username"));
            props.setPassword(entry.getValue().get("password"));
            databases.put(entry.getKey(), props);
        }
    }
}