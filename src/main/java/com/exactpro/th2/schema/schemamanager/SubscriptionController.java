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
package com.exactpro.th2.schema.schemamanager;

import com.exactpro.th2.schema.schemamanager.repository.RepositoryUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import rx.Subscription;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class SubscriptionController {

    private ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping("/subscriptions/schema/{name}")
    public ResponseEntity<ResponseBodyEmitter> subscribe(@PathVariable(name="name") String schemaName) {

        final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);
        ResponseBodyEmitter eventEmitter = new ResponseBodyEmitter(-1L);

        String sessionId = String.format("%016x", (new Random()).nextLong());

        executor.execute(() -> {

                SchemaEventRouter router = SchemaEventRouter.getInstance();

                var ref = new Object() {
                    Subscription subscription = null;
                };
                ref.subscription = router.getObservable()
                        .filter(event -> (event.getSchema().equals(schemaName) && event.getEventType().equals(RepositoryUpdateEvent.EVENT_TYPE)))
                        .observeOn(Schedulers.io())
                        .subscribe(event -> {

                            try {
                                eventEmitter.send(event.getEventBody() + "\n");
                                logger.info("Subscription \"{}\": sent update event {} on thread \"{}\""
                                        , sessionId
                                        , event.getEventBody()
                                        , Thread.currentThread().getName()
                                );
                            } catch (IOException e) {

                                logger.info("Subscription \"{}\": closed. Unsubscribing on thread \"{}\""
                                        , sessionId
                                        , Thread.currentThread().getName()
                                );

                                if (!(ref.subscription == null || ref.subscription.isUnsubscribed()))
                                    ref.subscription.unsubscribe();
                                eventEmitter.completeWithError(e);
                            }
                        });

                logger.info("Subscription \"{}\": started for schema \"{}\" on thread \"{}\""
                        , sessionId
                        , schemaName
                        , Thread.currentThread().getName()
                );
        });

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.TEXT_EVENT_STREAM);
        return new ResponseEntity(eventEmitter, responseHeaders, HttpStatus.OK);
    }
}