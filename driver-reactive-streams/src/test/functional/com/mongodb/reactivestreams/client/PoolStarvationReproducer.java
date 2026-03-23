/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.reactivestreams.client;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.connection.TransportSettings;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionPoolListener;
import io.netty.channel.nio.NioEventLoopGroup;
import org.bson.Document;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class PoolStarvationReproducer {

    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(30);
    private static final MongoNamespace NAMESPACE = new MongoNamespace("test", "nettyDeadlockReproducer");

    private MongoCollection<Document> getMongoCollection(MongoClient mongoClient) {
        MongoDatabase database = mongoClient.getDatabase(NAMESPACE.getDatabaseName());
        MongoCollection<Document> collection = database.getCollection(NAMESPACE.getCollectionName());
        collection.drop();
        Mono.from(database.createCollection(NAMESPACE.getCollectionName())).block();
        return collection;
    }

    static Stream<Arguments> transportSettings() {
        return Stream.of(
                Arguments.of("Netty", TransportSettings.nettyBuilder()
                        .eventLoopGroup(new NioEventLoopGroup(10))
                        .build()),
                Arguments.of("Async", TransportSettings.asyncBuilder()
                        .executorService(Executors.newFixedThreadPool(10))
                        .build())
        );
    }

    private MongoClientSettings.Builder getMongoClientSettingsBuilder(final TransportSettings transportSettings,
                                                                      final int maxPoolSize,
                                                                      final int maxWaitSeconds) {
        return MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("mongodb://localhost:27017, localhost:27018, localhost:27019"))
                .applyToConnectionPoolSettings(builder ->
                        builder.maxSize(maxPoolSize)
                                .maxWaitTime(maxWaitSeconds, TimeUnit.SECONDS)
                                .addConnectionPoolListener(new ConnectionPoolListener() {
                                    @Override
                                    public void connectionCheckedOut(final ConnectionCheckedOutEvent event) {
                                        System.err.println("[POOL] checked out connId=" + event.getConnectionId()
                                                + " thread=" + Thread.currentThread().getName());
                                    }
                                    @Override
                                    public void connectionCheckedIn(final ConnectionCheckedInEvent event) {
                                        System.err.println("[POOL] checked in  connId=" + event.getConnectionId()
                                                + " thread=" + Thread.currentThread().getName());
                                    }
                                }))
                .addCommandListener(new CommandListener() {
                    @Override
                    public void commandStarted(final CommandStartedEvent event) {
                        System.err.println("[CMD_STARTED] " + Thread.currentThread().getName()
                                + " " + event.getCommandName() + " reqId=" + event.getRequestId()
                                + " cmd=" + event.getCommand().toJson());
                    }
                    @Override
                    public void commandSucceeded(final CommandSucceededEvent event) {
                        System.err.println("[CMD_SUCCEEDED] " + Thread.currentThread().getName()
                                + " " + event.getCommandName() + " reqId=" + event.getRequestId()
                                + " " + event.getElapsedTime(TimeUnit.MILLISECONDS) + "ms"
                                + " resp=" + event.getResponse().toJson());
                    }
                    @Override
                    public void commandFailed(final CommandFailedEvent event) {
                        System.err.println("[CMD_FAILED] " + Thread.currentThread().getName()
                                + " " + event.getCommandName() + " reqId=" + event.getRequestId()
                                + " " + event.getElapsedTime(TimeUnit.MILLISECONDS) + "ms");
                    }
                })
                .transportSettings(transportSettings);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transportSettings")
    public void testChangeStreamConnectionPoolExhaustion(String transportName, TransportSettings transportSettings) throws InterruptedException {
        ChangeStreamsStartResult result = null;
        try {
            try (MongoClient mongoClient = MongoClients.create(getMongoClientSettingsBuilder(transportSettings, 10, 20).build())) {
                MongoCollection<Document> collection = getMongoCollection(mongoClient);
                int numChangeStreams = 10;
                int numberOfEvents = 20;
                int totalExpected = numChangeStreams * numberOfEvents;
                result = startChangeStreams(numChangeStreams, totalExpected, collection);
                triggerEvents(numberOfEvents);

                result.eventsProcessed.await(20, TimeUnit.SECONDS);
                // Verify pool is not exhausted — a simple find should succeed
                Mono.from(collection.find(new Document("test", true)).first())
                    .block(TIMEOUT_DURATION);

                System.err.println("Processed " + result.processedCount.get() + " out of " + totalExpected + " events");
            }
        } finally {
            if (result != null) {
                for (Disposable d : result.disposables) {
                    d.dispose();
                }
            }
        }
    }

    private static void triggerEvents(final int numberOfEvents) {
        // Use a separate client to inject data into the collection
        try (MongoClient dataInjector = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString("mongodb://localhost:27017,localhost:27018,localhost:27019"))
                .build())) {
            MongoCollection<Document> injectorCollection = dataInjector
                    .getDatabase(NAMESPACE.getDatabaseName())
                    .getCollection(NAMESPACE.getCollectionName());
            for (int i = 0; i < numberOfEvents; i++) {
                Mono.from(injectorCollection.insertOne(new Document("event", i)))
                        .block(TIMEOUT_DURATION);
            }
        }
    }

    private static ChangeStreamsStartResult startChangeStreams(final int numChangeStreams,
                                                                                  final int totalExpected,
                                                                                  final MongoCollection<Document> collection) throws InterruptedException {
        CountDownLatch eventsProcessed = new CountDownLatch(totalExpected);
        AtomicInteger processedCount = new AtomicInteger(0);
        List<Disposable> disposables = new ArrayList<>();
        for (int i = 0; i < numChangeStreams; i++) {
            Disposable d = Flux.from(collection.watch().maxAwaitTime(1, TimeUnit.SECONDS))
                    .publishOn(Schedulers.boundedElastic(), 1)
                    .doOnNext(changeEvent -> {
                          //NOOP
                    })
                    .subscribe();
            disposables.add(d);
        }
        Thread.sleep(2000);
        return new ChangeStreamsStartResult(eventsProcessed, processedCount, disposables);
    }

    private static class ChangeStreamsStartResult {
        public final CountDownLatch eventsProcessed;
        public final AtomicInteger processedCount;
        public final List<Disposable> disposables;

        public ChangeStreamsStartResult(final CountDownLatch eventsProcessed, final AtomicInteger processedCount,
                                        final List<Disposable> disposables) {
            this.eventsProcessed = eventsProcessed;
            this.processedCount = processedCount;
            this.disposables = disposables;
        }
    }
}