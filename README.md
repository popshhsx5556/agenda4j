# agenda4j

`agenda4j` is a MongoDB-backed Java job scheduler split into three modules:

- `agenda4j-core`: public scheduling API and contracts
- `agenda4j-mongo`: MongoDB job store and runtime engine
- `agenda4j-spring-boot-starter`: Spring Boot auto-configuration

Current status: **0.1.x is evolving**. API and behavior can still change before `1.0.0`.

## Key Features

- Distributed execution support - safely run across multiple nodes with coordinated job locking
- Priority-based dispatching - built-in priority levels with numeric priority support
- Spring Boot auto-configuration - seamless integration with automatic lifecycle management
- Daily fixed-time scheduling - schedule jobs at a specific time each day
- Timezone-aware recurrence - schedule recurring jobs with explicit timezone support
- Rich repeat syntax - supports human-readable intervals, cron expressions, and numeric seconds
- Typed handler contract - strongly-typed `JobHandler<T>` with automatic payload deserialization


`agenda4j` is a MongoDB-backed Java job scheduler inspired by the
popular Node.js project "agenda".

It brings distributed scheduling, priority dispatching, and rich
recurrence support to the Java ecosystem with strong typing and
Spring Boot integration.

## Offers

- Modular design: choose `agenda4j-core`, `agenda4j-mongo`, and `agenda4j-spring-boot-starter` based on your stack
- MongoDB persistence and runtime engine with distributed-safe claim/lock behavior
- One-time, immediate, and recurring scheduling APIs with fluent builder style
- Flexible cancellation with disable/delete modes and query-based selection

## Installation

### Spring Boot starter (recommended)

```xml
<dependency>
  <groupId>io.github.harutostudio</groupId>
  <artifactId>agenda4j-spring-boot-starter</artifactId>
  <version>0.1.1</version>
</dependency>
```

### Without Spring Boot

```xml
<dependency>
  <groupId>io.github.harutostudio</groupId>
  <artifactId>agenda4j-core</artifactId>
  <version>0.1.1</version>
</dependency>
<dependency>
  <groupId>io.github.harutostudio</groupId>
  <artifactId>agenda4j-mongo</artifactId>
  <version>0.1.1</version>
</dependency>
```

## Example Usage

### With Spring Boot

1. Add `agenda4j-spring-boot-starter`.
2. Provide one or more `JobHandler<?>` beans.
3. Configure `application.yml`.

### Minimal `application.yml`

```yaml
agenda:
  enabled: true
  worker-id: api-node-a
  process-every: 5s
  default-lock-lifetime: 30s
  max-concurrency: 20
  default-concurrency: 5
  lock-limit: 0
  batch-size: 5
  max-retry-count: 5
  cleanup-finished-jobs: true
  ensure-indexes-on-startup: false
```

`ensure-indexes-on-startup` is `false` by default. For production, prefer managing indexes by migration scripts.

### Spring `JobHandler` example

```java
import io.agenda4j.JobHandler;
import org.springframework.stereotype.Component;

@Component
public class SendEmailJobHandler implements JobHandler<SendEmailPayload> {
    @Override
    public String name() {
        return "send-email";
    }

    @Override
    public Class<SendEmailPayload> dataClass() {
        return SendEmailPayload.class;
    }

    @Override
    public void execute(SendEmailPayload data) {
        // send email
    }
}
```

```java
import io.agenda4j.Agenda;
import io.agenda4j.JobBuilder;
import org.springframework.stereotype.Service;

@Service
public class JobSchedulerService {
    private final Agenda agenda;

    public JobSchedulerService(Agenda agenda) {
        this.agenda = agenda;
    }

    public void scheduleExamples() {
        agenda.now("send-email", new SendEmailPayload("u1", "welcome"));

        agenda.schedule("send-email", java.time.Instant.now().plusSeconds(60),
                new SendEmailPayload("u1", "reminder")).save();

        agenda.every(
                "send-email",
                "0 */5 * * * *",
                new SendEmailPayload("u1", "digest"),
                JobBuilder.RepeatOptions.defaults()
        );
    }
}
```

### Without Spring

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.agenda4j.JobHandler;
import io.agenda4j.config.AgendaProperties;
import io.agenda4j.core.JobHandlerRegistry;
import io.agenda4j.internal.mongo.MongoAgenda;
import io.agenda4j.internal.mongo.MongoJobStore;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.time.Duration;
import java.util.List;

public class PlainJavaExample {
    public static void main(String[] args) {
        MongoClient client = MongoClients.create("mongodb://localhost:27017");
        MongoTemplate template = new MongoTemplate(
                new SimpleMongoClientDatabaseFactory(client, "agenda4j")
        );

        AgendaProperties props = new AgendaProperties();
        props.setWorkerId("node-a");
        props.setProcessEvery(Duration.ofSeconds(5));
        props.setDefaultLockLifetime(Duration.ofSeconds(30));
        props.setMaxConcurrency(10);
        props.setDefaultConcurrency(3);

        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(new SendEmailJobHandler()));
        MongoJobStore store = new MongoJobStore(template, new ObjectMapper());
        MongoAgenda agenda = new MongoAgenda(props, store, registry, new ObjectMapper());

        agenda.start();
        agenda.now("send-email", new SendEmailPayload("u1", "hello"));
    }
}
```

## MongoDB Index Requirements

Collection: `scheduled_jobs`

```javascript
db.scheduled_jobs.createIndex({ nextRunAt: 1, lockUntil: 1, priority: -1 }, { name: "idx_due_claim" });
db.scheduled_jobs.createIndex({ name: 1, uniqueKey: 1 }, { name: "idx_name_uniqueKey" });
db.scheduled_jobs.createIndex(
  { name: 1 },
  { name: "ux_single_name", unique: true, partialFilterExpression: { type: "SINGLE" } }
);
```

## Build

```bash
mvn -q -pl agenda4j-core,agenda4j-mongo,agenda4j-spring-boot-starter -am compile
mvn -q test
```

## Governance and Docs

- License: Apache License 2.0 (`LICENSE`)
- Notice: `NOTICE`
- Changelog: `CHANGELOG.md`
- Release process: `RELEASE.md`
- Contribution guide: `CONTRIBUTING.txt`
- Security policy: `SECURITY.txt`
- Code of conduct: `CODE_OF_CONDUCT.txt`

## Limitations (0.1.x)

- Public API is still stabilizing.
- Mongo lock/cancel semantics are covered by integration tests but may still evolve.
- Backward compatibility is best effort until `1.0.0`.
