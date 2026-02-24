package io.agenda4j.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agenda4j.Agenda;
import io.agenda4j.JobHandler;
import io.agenda4j.core.JobHandlerRegistry;
import io.agenda4j.internal.mongo.MongoAgenda;
import io.agenda4j.internal.mongo.MongoJobStore;
import io.agenda4j.internal.mongo.ScheduledJobDocument;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

/**
 * Spring Boot auto-configuration entrypoint for Agenda components.
 */
@AutoConfiguration
@ConditionalOnClass({Agenda.class, MongoTemplate.class})
@EnableConfigurationProperties(AgendaProperties.class)
@ConditionalOnProperty(prefix = "agenda", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgendaConfig {

    @Bean
    @ConditionalOnMissingBean
    protected MongoJobStore mongoJobStore(MongoTemplate mongoTemplate, AgendaProperties props, ObjectMapper objectMapper) {
        return new MongoJobStore(mongoTemplate, objectMapper, resolveCollectionName(props));
    }

    @Bean
    @ConditionalOnMissingBean
    protected AgendaMongoIndexConfig agendaMongoIndexConfig(MongoTemplate mongoTemplate, AgendaProperties props) {
        return new AgendaMongoIndexConfig(mongoTemplate, resolveCollectionName(props));
    }

    @Bean
    @ConditionalOnMissingBean
    public JobHandlerRegistry jobHandlerRegistry(ObjectProvider<List<JobHandler<?>>> handlersProvider) {
        List<JobHandler<?>> handlers = handlersProvider.getIfAvailable(List::of);
        return new JobHandlerRegistry(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    public Agenda agenda(AgendaProperties props, MongoJobStore jobStore, JobHandlerRegistry registry, ObjectMapper om) {
        return new MongoAgenda(props, jobStore, registry, om);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgendaLifecycle agendaLifecycle(Agenda agenda) {
        return new AgendaLifecycle(agenda);
    }

    @Bean
    @ConditionalOnProperty(prefix = "agenda", name = "ensure-indexes-on-startup", havingValue = "true")
    public org.springframework.beans.factory.SmartInitializingSingleton agendaIndexesInitializer(AgendaMongoIndexConfig indexConfig) {
        return indexConfig::ensureIndexes;
    }

    private static String resolveCollectionName(AgendaProperties props) {
        String configured = props.getCollectionName();
        if (configured == null || configured.isBlank()) {
            return ScheduledJobDocument.DEFAULT_COLLECTION;
        }
        return configured.trim();
    }
}
