package in.projecteka.datanotificationsubscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class LiquibaseDataSourceConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(LiquibaseDataSourceConfiguration.class);
    @Autowired
    private DbOptions dbOptions;

    @LiquibaseDataSource
    @Bean
    public DataSource liquibaseDataSource() {
        String url = buildUrl();
        DataSource ds = DataSourceBuilder.create()
                .username(dbOptions.getUser())
                .password(dbOptions.getPassword())
                .url(url)
                .driverClassName(gerPostgresDriver())
                .build();
        logger.info("Initialized a datasource for {}", url);
        return ds;
    }

    private String buildUrl() {
        return String.format("jdbc:postgresql://%s:%s/%s", dbOptions.getHost(), dbOptions.getPort(), dbOptions.getSchema());
    }

    private String gerPostgresDriver() {
        return "org.postgresql.Driver";
    }
}