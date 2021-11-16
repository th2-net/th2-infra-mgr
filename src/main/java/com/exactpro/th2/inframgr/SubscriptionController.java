/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.inframgr;

import com.exactpro.th2.inframgr.repository.RepositoryUpdateEvent;
import com.exactpro.th2.inframgr.statuswatcher.StatusCache;
import com.exactpro.th2.inframgr.statuswatcher.StatusUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rx.schedulers.Schedulers;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

@Controller
public class SubscriptionController {

    private static final long SESSION_TIMEOUT = 60 * 1000;

    private final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Autowired
    private StatusCache statusCache;

    private static final Map<String, EventSubscription> subscriptions = new ConcurrentHashMap<>();

    private static final ForkJoinPool pool = new ForkJoinPool(8);

    private static class EventSubscription {

        String schema;

        SseEmitter emitter;
    }

    private void sendEvent(SchemaEvent event, SseEmitter emitter, String subscriptionId) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getEventType())
                    .data(event.getEventBody())
                    .id(event.getEventKey())
            );
            logger.info("Subscription \"{}\": sent update event {} on thread \"{}\""
                    , subscriptionId
                    , event.getEventBody()
                    , Thread.currentThread().getName()
            );
        } catch (Exception e) {
            logger.warn("Subscription \"{}\": exception sending event on thread \"{}\" ({})"
                    , subscriptionId
                    , Thread.currentThread().getName()
                    , e.getMessage()
            );
        }
    }

    private void processEvent(SchemaEvent event) {

        // filter out and send event to all subscriptions to this event schema
        try {
            pool.submit(() -> {
                subscriptions.entrySet().stream()
                        .filter(entry -> event.getSchema().equals(entry.getValue().schema))
                        .parallel()
                        .forEach(entry -> sendEvent(event, entry.getValue().emitter, entry.getKey()));
            }).get();
        } catch (Exception e) {
            logger.warn("Exception processing events", e);
        }
    }

    @PostConstruct
    public void startEventProcessor() {

        executor.execute(() -> {

            SchemaEventRouter.getInstance().getObservable()
                    .onBackpressureBuffer()
                    .filter(event -> event instanceof RepositoryUpdateEvent || event instanceof StatusUpdateEvent)
                    .observeOn(Schedulers.computation())
                    .subscribe(event -> this.processEvent(event));
        });
    }

    private SseEmitter setupEmitter(String subscriptionId) {
        SseEmitter eventEmitter = new SseEmitter(SESSION_TIMEOUT);

        eventEmitter.onCompletion(() -> {
            int activeSubscriptions;
            EventSubscription sub;
            synchronized (subscriptions) {
                sub = subscriptions.remove(subscriptionId);
                activeSubscriptions = subscriptions.size();
            }
            if (sub != null) {
                logger.info("Subscription \"{}\": Unsubscribed on thread \"{}\", {} active subscriptions left"
                        , subscriptionId
                        , Thread.currentThread().getName()
                        , activeSubscriptions
                );
            }
        });
        eventEmitter.onError(t -> {
            int activeSubscriptions;
            EventSubscription sub;
            synchronized (subscriptions) {
                sub = subscriptions.remove(subscriptionId);
                activeSubscriptions = subscriptions.size();
            }
            if (sub != null) {
                logger.error("Subscription \"{}\": Unsubscribed on thread \"{}\", {} active subscriptions left ({})"
                        , subscriptionId
                        , Thread.currentThread().getName()
                        , activeSubscriptions
                        , t.getMessage()
                );
            }
        });

        return eventEmitter;
    }

    @GetMapping("/subscriptions/schema/{name}")
    public SseEmitter subscribe(@PathVariable(name = "name") String schemaName) {

        // insert dummy subscription to generate unique subscription ID
        EventSubscription dummy = new EventSubscription();
        String subscriptionId;
        do {
            subscriptionId = String.format("%016x", (new Random()).nextLong());
        } while (subscriptions.putIfAbsent(subscriptionId, dummy) != null);


        SseEmitter eventEmitter = setupEmitter(subscriptionId);

        // send current known deployment statuses
        try {
            List<StatusUpdateEvent> statusUpdateEvents = statusCache.getStatuses(schemaName);
            if (statusUpdateEvents != null) {
                for (StatusUpdateEvent event : statusUpdateEvents) {
                    eventEmitter.send(SseEmitter.event()
                            .name(event.getEventType())
                            .data(event.getEventBody())
                            .id(event.getEventKey())
                    );
                }
            }
        } catch (IOException e) {
            logger.warn("Subscription \"{}\": exception sending component statuses on thread \"{}\" ({})"
                    , subscriptionId
                    , Thread.currentThread().getName()
                    , e.getMessage()
            );
        }

        // subscriber to events
        EventSubscription subscription = new EventSubscription();
        subscription.schema = schemaName;
        subscription.emitter = eventEmitter;

        int activeSubscriptions;
        synchronized (subscriptions) {
            subscriptions.put(subscriptionId, subscription);
            activeSubscriptions = subscriptions.size();
        }

        logger.info("Subscription \"{}\": started for schema \"{}\" on thread \"{}\". there are {} active subscriptions"
                , subscriptionId
                , schemaName
                , Thread.currentThread().getName()
                , activeSubscriptions);

        return eventEmitter;
    }
}
