package com.exactpro.th2.schema.schemaeditorbe.repository;

import rx.Observable;
import rx.subjects.PublishSubject;

public class RepositoryEventRouter {

    private static volatile RepositoryEventRouter instance;
    private PublishSubject<RepositoryEvent> subject = PublishSubject.create();

    private RepositoryEventRouter() {
    }

    public static RepositoryEventRouter getInstance() {
        if (instance == null) {
            synchronized(RepositoryEventRouter.class) {
                if (instance == null)
                    instance = new RepositoryEventRouter();
            }
        }
        return instance;
    }

    public void addEvent(RepositoryEvent event) {
        subject.onNext(event);
    }

    public Observable<RepositoryEvent> getObservable() {
        return subject.asObservable();
    }
}
