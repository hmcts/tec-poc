package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;
import uk.gov.hmcts.ccd.sdk.type.LinkReason;

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
        assertThat(result.getFlagLauncher()).isNull();
        assertThat(result.getCaseFlags()).isNull();
        assertThat(result.getParties()).isEmpty();
        assertThat(result.getStatusDisplay()).isEqualTo("Case Issued");
        assertThat(result.getOotApplicationDecisionDisplay()).isNull();
    }

    @Test
    void getCaseLinksPrecedingRegistrationWhenSuffixIsGreaterThanZero() {
        TecCase stored = new TecCase();
        stored.setPenaltyChargeNumber("AB0531612A1");
        when(repository.find(333L)).thenReturn(stored);
        when(repository.findCaseReferenceByPenaltyChargeNumber("AB0531612A0"))
            .thenReturn(1791411192748416L);
        when(repository.findDocuments(333L)).thenReturn(List.of());
        when(repository.findWarrantAuthorisations(333L)).thenReturn(List.of());
        when(repository.findGeneralApplications(333L)).thenReturn(List.of());

        TecCase result = view.getCase(new CaseViewRequest<>(333L, CaseState.PENDING_CASE_ISSUED));

        assertThat(result.getCaseLinks()).hasSize(1);
        assertThat(result.getCaseLinks().get(0).getId()).isEqualTo("1791411192748416");
        CaseLink link = result.getCaseLinks().get(0).getValue();
        assertThat(link.getCaseReference()).isEqualTo("1791411192748416");
        assertThat(link.getCaseType()).isEqualTo(TecCaseConfiguration.CASE_TYPE);
        assertThat(link.getReasonForLink()).hasSize(1);
        LinkReason reason = link.getReasonForLink().get(0).getValue();
        assertThat(reason.getReason()).isEqualTo("CLRC007");
        assertThat(reason.getDescription()).isEqualTo("Previous registration");
    }

    @Test
    void getCaseLeavesCaseLinksEmptyWhenSuffixIsZero() {
        TecCase stored = new TecCase();
        stored.setPenaltyChargeNumber("AB0531612A0");
        when(repository.find(444L)).thenReturn(stored);
        when(repository.findDocuments(444L)).thenReturn(List.of());
        when(repository.findWarrantAuthorisations(444L)).thenReturn(List.of());
        when(repository.findGeneralApplications(444L)).thenReturn(List.of());

        TecCase result = view.getCase(new CaseViewRequest<>(444L, CaseState.PENDING_CASE_ISSUED));

        assertThat(result.getCaseLinks()).isEmpty();
        assertThat(TecCaseView.precedingPenaltyChargeNumber("AB0531612A0")).isNull();
        assertThat(TecCaseView.precedingPenaltyChargeNumber("AB0531612A2")).isEqualTo("AB0531612A1");
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
