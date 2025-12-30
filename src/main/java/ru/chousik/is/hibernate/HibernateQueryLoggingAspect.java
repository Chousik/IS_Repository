package ru.chousik.is.hibernate;

import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.stat.spi.StatisticsImplementor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(prefix = "app.hibernate.query-logging", name = "enabled", havingValue = "true")
@Slf4j
public class HibernateQueryLoggingAspect {

    private final StatisticsImplementor statistics;

    public HibernateQueryLoggingAspect(EntityManagerFactory entityManagerFactory) {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        this.statistics = sessionFactory.getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    @Around("@annotation(ru.chousik.is.hibernate.TrackHibernateQueries)")
    public Object logQueries(ProceedingJoinPoint joinPoint) throws Throwable {
        long executedBefore = statistics.getQueryExecutionCount();
        Set<String> knownBefore = captureKnownQueries();
        Object result = joinPoint.proceed();
        long executedAfter = statistics.getQueryExecutionCount();
        Set<String> newQueries = captureKnownQueries();
        newQueries.removeAll(knownBefore);

        long executedDelta = executedAfter - executedBefore;
        if (executedDelta > 0 || !newQueries.isEmpty()) {
            log.info(
                    "Hibernate queries for {} -> count: {}, new queries: {}",
                    joinPoint.getSignature().toShortString(),
                    executedDelta,
                    newQueries);
        } else {
            log.info("Hibernate queries for {} -> no queries executed", joinPoint.getSignature().toShortString());
        }
        return result;
    }

    private Set<String> captureKnownQueries() {
        String[] queries = statistics.getQueries();
        if (queries == null || queries.length == 0) {
            return new LinkedHashSet<>();
        }
        Set<String> uniqueQueries = new LinkedHashSet<>();
        Arrays.stream(queries)
                .filter(query -> query != null && !query.isBlank())
                .forEach(uniqueQueries::add);
        return uniqueQueries;
    }
}
