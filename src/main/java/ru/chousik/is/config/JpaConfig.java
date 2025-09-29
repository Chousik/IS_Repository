package ru.chousik.is.config;

import jakarta.persistence.EntityManagerFactory;
import org.eclipse.persistence.config.PersistenceUnitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.vendor.EclipseLinkJpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableJpaRepositories(basePackages = "ru.chousik.is.repository")
public class JpaConfig {
    @Bean
    public EclipseLinkJpaVendorAdapter jpaVendorAdapter() {
        EclipseLinkJpaVendorAdapter adapter = new EclipseLinkJpaVendorAdapter();
        adapter.setShowSql(false);
        adapter.setGenerateDdl(false);
        return adapter;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            EclipseLinkJpaVendorAdapter jpaVendorAdapter
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(PersistenceUnitProperties.WEAVING, "false");
        props.put(PersistenceUnitProperties.LOGGING_LEVEL, "INFO");

        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("ru.chousik.is.entity");
        emf.setJpaVendorAdapter(jpaVendorAdapter);
        emf.setJpaPropertyMap(props);
        return emf;
    }

    @Bean
    public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();

    }
}
