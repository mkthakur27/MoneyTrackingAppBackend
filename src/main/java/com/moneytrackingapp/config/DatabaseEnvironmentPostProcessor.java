package com.moneytrackingapp.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies DB_URL / DATABASE_URL / SPRING_DATASOURCE_* from the process environment
 * or a local .env file, and turns postgres:// URLs into JDBC URLs.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, String> dotenv = loadDotEnv(resolveDotEnv());

        String rawUrl = first(
                getenv("DB_URL"), dotenv.get("DB_URL"),
                getenv("DATABASE_URL"), dotenv.get("DATABASE_URL"),
                getenv("SPRING_DATASOURCE_URL"), dotenv.get("SPRING_DATASOURCE_URL"));

        String username = first(
                getenv("DB_USERNAME"), dotenv.get("DB_USERNAME"),
                getenv("PGUSER"), dotenv.get("PGUSER"),
                getenv("SPRING_DATASOURCE_USERNAME"), dotenv.get("SPRING_DATASOURCE_USERNAME"));

        String password = first(
                getenv("DB_PASSWORD"), dotenv.get("DB_PASSWORD"),
                getenv("PGPASSWORD"), dotenv.get("PGPASSWORD"),
                getenv("SPRING_DATASOURCE_PASSWORD"), dotenv.get("SPRING_DATASOURCE_PASSWORD"));

        Map<String, Object> overrides = new LinkedHashMap<>();
        if (rawUrl != null) {
            ParsedUrl parsed = parse(rawUrl);
            overrides.put("spring.datasource.url", parsed.jdbcUrl());
            if (username == null) {
                username = parsed.username();
            }
            if (password == null) {
                password = parsed.password();
            }
        }
        if (username != null) {
            overrides.put("spring.datasource.username", username);
        }
        if (password != null) {
            overrides.put("spring.datasource.password", password);
        }
        if (!overrides.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("externalDatabase", overrides));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static ParsedUrl parse(String rawUrl) {
        String trimmed = rawUrl.trim();
        if (trimmed.startsWith("jdbc:")) {
            return new ParsedUrl(trimmed, null, null);
        }

        String normalized = trimmed;
        if (normalized.startsWith("postgres://")) {
            normalized = "postgresql://" + normalized.substring("postgres://".length());
        }
        if (!normalized.startsWith("postgresql://")) {
            throw new IllegalStateException("Database URL must start with jdbc:postgresql://, postgresql://, or postgres://");
        }

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Database URL could not be parsed", ex);
        }
        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().isBlank()) {
            throw new IllegalStateException("Database URL must include a host and database name");
        }

        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int separator = userInfo.indexOf(':');
            if (separator >= 0) {
                username = decode(userInfo.substring(0, separator));
                password = decode(userInfo.substring(separator + 1));
            } else {
                username = decode(userInfo);
            }
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        jdbcUrl.append(uri.getPath());
        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
            jdbcUrl.append('?').append(uri.getQuery());
        }
        return new ParsedUrl(jdbcUrl.toString(), username, password);
    }

    private static Path resolveDotEnv() {
        Path fromWorkingDirectory = Path.of(System.getProperty("user.dir", ".")).resolve(".env");
        if (Files.isRegularFile(fromWorkingDirectory)) {
            return fromWorkingDirectory;
        }
        try {
            Path codeLocation = Path.of(DatabaseEnvironmentPostProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path classesDirectory = Files.isDirectory(codeLocation) ? codeLocation : codeLocation.getParent();
            Path projectDirectory = classesDirectory.getParent();
            if (projectDirectory != null && "target".equals(String.valueOf(projectDirectory.getFileName()))) {
                projectDirectory = projectDirectory.getParent();
            }
            if (projectDirectory != null) {
                return projectDirectory.resolve(".env");
            }
        } catch (Exception ignored) {
            // Fall through to the working-directory path when the code location is unavailable.
        }
        return fromWorkingDirectory;
    }

    private static Map<String, String> loadDotEnv(Path path) {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read .env file", ex);
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = stripQuotes(trimmed.substring(separator + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char start = value.charAt(0);
            char end = value.charAt(value.length() - 1);
            if ((start == '"' && end == '"') || (start == '\'' && end == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String getenv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record ParsedUrl(String jdbcUrl, String username, String password) {
    }
}
