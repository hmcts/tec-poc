package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

class TecCaseViewCaseNotesTest {

    @Test
    void toCaseNotesConvertsUtcToLondonTime() {
        UUID winterId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID summerId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        List<ListValue<CaseNote>> values = TecCaseView.toCaseNotes(List.of(
            new TecCaseNote(
                winterId,
                Instant.parse("2026-01-15T12:00:00Z"),
                "Winter clerk",
                "Winter note"
            ),
            new TecCaseNote(
                summerId,
                Instant.parse("2026-04-22T21:00:00Z"),
                "Summer clerk",
                "Summer note"
            )
        ));

        assertThat(values).hasSize(2);
        CaseNote winter = values.get(0).getValue();
        assertThat(values.get(0).getId()).isEqualTo(winterId.toString());
        assertThat(winter.getCreatedBy()).isEqualTo("Winter clerk");
        assertThat(winter.getCreatedOn()).isEqualTo(LocalDateTime.of(2026, 1, 15, 12, 0));
        assertThat(winter.getNote()).isEqualTo("Winter note");

        CaseNote summer = values.get(1).getValue();
        assertThat(summer.getCreatedBy()).isEqualTo("Summer clerk");
        assertThat(summer.getCreatedOn()).isEqualTo(LocalDateTime.of(2026, 4, 22, 22, 0));
        assertThat(summer.getNote()).isEqualTo("Summer note");
    }

    @Test
    void getCaseClearsTheDraftNoteAndShowsSavedNotes() {
        TecCaseRepository repository = mock(TecCaseRepository.class);
        TecCase stored = new TecCase();
        stored.setNote("draft that must not stay on the case");
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(repository.find(30L)).thenReturn(stored);
        when(repository.findDocuments(30L)).thenReturn(List.of());
        when(repository.findWarrantAuthorisations(30L)).thenReturn(List.of());
        when(repository.findGeneralApplications(30L)).thenReturn(List.of());
        when(repository.findCaseNotes(30L)).thenReturn(List.of(
            new TecCaseNote(id, Instant.parse("2026-01-15T12:00:00Z"), "Ada Clerk", "Kept note")
        ));

        TecCase result = new TecCaseView(repository).getCase(
            new CaseViewRequest<>(30L, CaseState.CLOSED)
        );

        assertThat(result.getNote()).isNull();
        assertThat(result.getCaseNotes()).hasSize(1);
        assertThat(result.getCaseNotes().get(0).getValue().getNote()).isEqualTo("Kept note");
        assertThat(result.getCaseNotes().get(0).getValue().getCreatedOn())
            .isEqualTo(LocalDateTime.of(2026, 1, 15, 12, 0));
    }
}
