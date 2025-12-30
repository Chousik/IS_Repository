package ru.chousik.is.cache;

import jakarta.persistence.EntityManagerFactory;
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
@ConditionalOnProperty(prefix = "app.cache.statistics", name = "enabled", havingValue = "true")
@Slf4j
public class CacheStatisticsAspect {

    private final StatisticsImplementor statistics;

    public CacheStatisticsAspect(EntityManagerFactory entityManagerFactory) {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        this.statistics = sessionFactory.getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    @Around("@annotation(ru.chousik.is.cache.TrackCacheStats)")
    public Object logCacheStats(ProceedingJoinPoint joinPoint) throws Throwable {
        long hitsBefore = statistics.getSecondLevelCacheHitCount();
        long missesBefore = statistics.getSecondLevelCacheMissCount();
        Object result = joinPoint.proceed();
        long hitDelta = statistics.getSecondLevelCacheHitCount() - hitsBefore;
        long missDelta = statistics.getSecondLevelCacheMissCount() - missesBefore;
        log.info(
                "L2 cache stats for {} -> hits: {}, misses: {}",
                joinPoint.getSignature().toShortString(),
                hitDelta,
                missDelta);
        return result;
    }
}
