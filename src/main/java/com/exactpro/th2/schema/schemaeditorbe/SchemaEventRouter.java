package com.exactpro.th2.schema.schemaeditorbe;

import rx.Observable;
import rx.subjects.PublishSubject;

public class SchemaEventRouter {

    private static volatile SchemaEventRouter instance;
    private PublishSubject<SchemaEvent> subject;

    private SchemaEventRouter() {
        subject = PublishSubject.create();
    }

    public static SchemaEventRouter getInstance() {
        if (instance == null) {
            synchronized(SchemaEventRouter.class) {
                if (instance == null)
                    instance = new SchemaEventRouter();
            }
        }
        return instance;
    }

    public void addEvent(SchemaEvent event) {
        subject.onNext(event);
    }

    public Observable<SchemaEvent> getObservable() {
        return subject.asObservable();
    }
}
