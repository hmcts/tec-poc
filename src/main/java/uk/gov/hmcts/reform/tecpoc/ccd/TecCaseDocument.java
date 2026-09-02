package uk.gov.hmcts.reform.tecpoc.ccd;

import java.time.Instant;
import java.util.UUID;

public record TecCaseDocument(
    UUID id,
    String categoryId,
    String documentUrl,
    String documentBinaryUrl,
    String filename,
    Instant createdAt
) {
}
