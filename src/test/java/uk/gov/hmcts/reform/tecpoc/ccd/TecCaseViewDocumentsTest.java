package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.ccd.sdk.type.Document;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

class TecCaseViewDocumentsTest {

    @Test
    void shouldMapStoredDocumentsOntoAllDocumentsWithCategory() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant createdAt = Instant.parse("2026-09-02T12:00:00Z");

        List<ListValue<Document>> allDocuments = TecCaseView.toAllDocuments(List.of(
            new TecCaseDocument(
                id,
                "hearingDocuments",
                "http://localhost:4455/cases/documents/" + id,
                "http://localhost:4455/cases/documents/" + id + "/binary",
                "hearing-note.pdf",
                createdAt
            )
        ));

        assertThat(allDocuments).hasSize(1);
        assertThat(allDocuments.get(0).getId()).isEqualTo(id.toString());
        Document document = allDocuments.get(0).getValue();
        assertThat(document.getCategoryId()).isEqualTo("hearingDocuments");
        assertThat(document.getFilename()).isEqualTo("hearing-note.pdf");
        assertThat(document.getUrl()).endsWith(id.toString());
        assertThat(document.getBinaryUrl()).endsWith("/binary");
        assertThat(document.getUploadTimestamp()).isEqualTo(createdAt.atZone(java.time.ZoneOffset.UTC).toLocalDateTime());
    }
}
