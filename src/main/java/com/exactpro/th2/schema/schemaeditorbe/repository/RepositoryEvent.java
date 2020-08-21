package com.exactpro.th2.schema.schemaeditorbe.repository;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class RepositoryEvent {
    private String branch;
    private String commitRef;
}
