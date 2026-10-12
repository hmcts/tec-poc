package uk.gov.hmcts.reform.tecpoc.ccd;

import java.time.Instant;
import java.util.UUID;

public record TecCaseNote(
    UUID id,
    Instant createdOn,
    String createdBy,
    String note
) {
}
