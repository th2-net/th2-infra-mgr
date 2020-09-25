/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.schema.inframgr;

import com.exactpro.th2.schema.inframgr.repository.RepositoryUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rx.Subscription;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class SubscriptionController {

    private final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);
    private ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping("/subscriptions/schema/{name}")
    public SseEmitter subscribe(@PathVariable(name="name") String schemaName) {

        String sessionId = String.format("%016x", (new Random()).nextLong());

        var ref = new Object() {
            Subscription subscription = null;
        };
        SseEmitter eventEmitter = new SseEmitter(-1L);
        eventEmitter.onError(throwable -> {
            logger.info("Subscription \"{}\": Unsubscribing on thread \"{}\""
                    , sessionId
                    , Thread.currentThread().getName()
            );
            if (!(ref.subscription == null || ref.subscription.isUnsubscribed()))
                ref.subscription.unsubscribe();
        });

        executor.execute(() -> {

                SchemaEventRouter router = SchemaEventRouter.getInstance();
                ref.subscription = router.getObservable()
                        .filter(event -> (event.getSchema().equals(schemaName) && (event instanceof RepositoryUpdateEvent)))
                        .observeOn(Schedulers.io())
                        .subscribe(event -> {

                            try {
                                eventEmitter.send(SseEmitter.event()
                                        .name(event.getEventType())
                                        .data(event.getEventBody())
                                        .id(event.getEventKey())
                                        );
                                logger.info("Subscription \"{}\": sent update event {} on thread \"{}\""
                                        , sessionId
                                        , event.getEventBody()
                                        , Thread.currentThread().getName()
                                );
                            } catch (IOException e) {
                                logger.error("Subscription \"{}\": exception sending event on thread \"{}\" ({})"
                                        , sessionId
                                        , Thread.currentThread().getName()
                                        , e.getMessage()
                                );
                            }
                        });

                logger.info("Subscription \"{}\": started for schema \"{}\" on thread \"{}\""
                        , sessionId
                        , schemaName
                        , Thread.currentThread().getName()
                );
        });

        return eventEmitter;
    }
}