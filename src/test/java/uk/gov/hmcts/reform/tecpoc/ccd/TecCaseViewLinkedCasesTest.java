package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;

class TecCaseViewLinkedCasesTest {

    private TecCaseRepository repository;
    private TecCaseView view;

    @BeforeEach
    void setUp() {
        repository = mock(TecCaseRepository.class);
        view = new TecCaseView(repository);
    }

    @Test
    void getCaseLeavesCaseLinksEmptyWhenBatchLinked() {
        TecCase stored = new TecCase();
        stored.setLocalAuthority(LocalAuthority.WESTMINSTER);
        stored.setBatchCase(CaseLink.builder()
            .caseReference("1755000000000099")
            .caseType(BatchCaseConfiguration.CASE_TYPE)
            .build());

        when(repository.find(111L)).thenReturn(stored);
        when(repository.findDocuments(111L)).thenReturn(List.of());
        when(repository.findWarrantAuthorisations(111L)).thenReturn(List.of());
        when(repository.findGeneralApplications(111L)).thenReturn(List.of());

        TecCase result = view.getCase(new CaseViewRequest<>(111L, CaseState.CASE_ISSUED));

        assertThat(result.getCaseLinks()).isEmpty();
        assertThat(result.getFlagLauncher()).isNotNull();
        assertThat(result.getCaseFlags()).isNotNull();
        assertThat(result.getParties()).isEmpty();
        assertThat(result.getStatusDisplay()).isEqualTo("Case Issued");
        assertThat(result.getOotApplicationDecisionDisplay()).isNull();
    }

    @Test
    void getCaseShowsRefusedOotDecisionWhilePendingRefusalDecision() {
        TecCase stored = new TecCase();
        when(repository.find(222L)).thenReturn(stored);
        when(repository.findDocuments(222L)).thenReturn(List.of());
        when(repository.findWarrantAuthorisations(222L)).thenReturn(List.of());
        when(repository.findGeneralApplications(222L)).thenReturn(List.of());

        TecCase result = view.getCase(
            new CaseViewRequest<>(222L, CaseState.PENDING_REFUSAL_DECISION)
        );

        assertThat(result.getOotApplicationDecisionDisplay()).isEqualTo("Refused");
        assertThat(result.getOotRefusalReviewDecision()).isNull();
    }
}
