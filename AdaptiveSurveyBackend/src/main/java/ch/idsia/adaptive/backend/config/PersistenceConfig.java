package ch.idsia.adaptive.backend.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Author:  Claudio "Dna" Bonesana
 * Project: AdaptiveSurvey
 * Date:    24.11.2020 13:39
 */
@Configuration
@AutoConfigureAfter(WebConfig.class)
@PropertySource("classpath:persistence.properties")
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "ch.idsia.adaptive.backend.persistence.dao")
public class PersistenceConfig {
	public static final Logger logger = LogManager.getLogger(PersistenceConfig.class);

	private final Environment env;

	@Autowired
	public PersistenceConfig(Environment env) {
		this.env = env;
	}

	private void configurePostgreSQL(DriverManagerDataSource ds) {
		logger.info("Database platform: Postgres");

		final String hostname = env.getProperty("spring.datasource.hostname", "localhost");
		final String port = env.getProperty("spring.datasource.port", "5432");
		final String schema = env.getProperty("spring.datasource.schema", "adaptive");
		final String connUrl = "jdbc:postgresql://" + hostname + ":" + port + "/" + schema;

		ds.setDriverClassName("org.postgresql.Driver");
		ds.setUrl(connUrl);
	}

	private void configureMySQL(DriverManagerDataSource ds) {
		logger.info("Database platform: MySQL");

		final String hostname = env.getProperty("spring.datasource.hostname", "localhost");
		final String port = env.getProperty("spring.datasource.port", "3306");
		final String schema = env.getProperty("spring.datasource.schema", "adaptive");
		final String connUrl = "jdbc:mysql://" + hostname + ":" + port + "/" + schema;

		ds.setDriverClassName("com.mysql.jdbc.Driver");
		ds.setUrl(connUrl);
	}

	private void configureMemory(DriverManagerDataSource ds) {
		logger.info("Database platform: Memory");

		final String schema = env.getProperty("spring.datasource.schema", "");
		final String connUrl = "jdbc:h2:mem:" + schema + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;";

		ds.setDriverClassName("org.h2.Driver");
		ds.setUrl(connUrl);
	}

	@Bean
	public DataSource dataSource() {
		DriverManagerDataSource ds = new DriverManagerDataSource();
		ds.setUsername(env.getProperty("spring.datasource.username", ""));
		ds.setPassword(env.getProperty("spring.datasource.password", ""));

		final String dbms = env.getProperty("spring.datasource.dbms", "");

		switch (dbms) {
			case "postgresql":
				configurePostgreSQL(ds);
				break;

			case "mysql":
				configureMySQL(ds);
				break;

			default:
				logger.warn("Invalid DBMS={}, using in memory", dbms);
			case "memory":
				configureMemory(ds);
				break;
		}
		return ds;
	}

	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
		LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
		em.setDataSource(dataSource());
		em.setPackagesToScan("ch.idsia.adaptive.backend.persistence.model");

		JpaVendorAdapter va = new HibernateJpaVendorAdapter();
		em.setJpaVendorAdapter(va);
		em.setJpaProperties(additionalProperties());

		return em;
	}

	private Properties additionalProperties() {
		final String dbms = env.getProperty("spring.datasource.dbms", "");

		Properties properties = new Properties();
		properties.put("hibernate.hbm2ddl.auto", env.getProperty("spring.jpa.hibernate.ddl-auto", ""));
		properties.put("hibernate.show_sql", env.getProperty("spring.jpa.show-sql", "false"));
		properties.put("hibernate.globally_quoted_identifiers", env.getProperty("spring.jpa.hibernate.globally_quoted_identifiers", "false"));

		switch (dbms) {
			case "postgresql":
				properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQL82Dialect");
				break;
			case "mysql":
				properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
				break;
			default:
			case "memory":
				properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
		}

		return properties;
	}

	@Bean
	public PlatformTransactionManager transactionManager() {
		JpaTransactionManager tm = new JpaTransactionManager();
		tm.setEntityManagerFactory(entityManagerFactory().getObject());
		return tm;
	}
}

