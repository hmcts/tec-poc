package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.ccd.sdk.api.EventPayload;
import uk.gov.hmcts.ccd.sdk.api.callback.SubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;

class TecCaseConfigurationLinkBatchCaseTest {

    private TecCaseRepository repository;
    private BatchCaseRepository batchCaseRepository;
    private TecCaseConfiguration configuration;

    @BeforeEach
    void setUp() {
        repository = mock(TecCaseRepository.class);
        batchCaseRepository = mock(BatchCaseRepository.class);
        configuration = new TecCaseConfiguration(repository, batchCaseRepository);
    }

    @Test
    void linkBatchCasePersistsRegistrationFkWhenBatchExists() {
        when(batchCaseRepository.exists(222L)).thenReturn(true);
        when(batchCaseRepository.find(222L)).thenReturn(batch(BatchOperation.REGISTRATION));
        when(repository.findBatchCaseReference(111L)).thenReturn(null);

        TecCase data = linkPayload("222", BatchOperation.REGISTRATION);

        SubmitResponse<CaseState> response = linkBatchCase(111L, data);

        verify(repository).linkBatchCase(111L, 222L);
        verify(batchCaseRepository, never()).linkPcnCase(222L, 111L);
        assertThat(response.getState()).isNull();
    }

    @Test
    void linkBatchCaseAcceptsHyphenatedReference() {
        when(batchCaseRepository.exists(1755000000000000L)).thenReturn(true);
        when(batchCaseRepository.find(1755000000000000L)).thenReturn(batch(BatchOperation.REGISTRATION));
        when(repository.findBatchCaseReference(111L)).thenReturn(null);

        TecCase data = new TecCase();
        data.setBatchLinkCase(CaseLink.builder()
            .caseReference("1755-0000-0000-0000")
            .build());
        data.setBatchLinkType(BatchOperation.REGISTRATION);

        linkBatchCase(111L, data);

        verify(repository).linkBatchCase(111L, 1755000000000000L);
    }

    @Test
    void linkBatchCaseWritesJoinTableForNonRegistrationWithoutTouchingFk() {
        when(batchCaseRepository.exists(222L)).thenReturn(true);
        when(batchCaseRepository.find(222L)).thenReturn(batch(BatchOperation.WARRANT_AUTH_REQUESTS));

        TecCase data = linkPayload("222", BatchOperation.WARRANT_AUTH_REQUESTS);
        // Must not send batchCase — that field is registration Case details only.
        data.setBatchCase(CaseLink.builder()
            .caseReference("999")
            .caseType(BatchCaseConfiguration.CASE_TYPE)
            .build());

        linkBatchCase(111L, data);

        verify(batchCaseRepository).linkPcnCase(222L, 111L);
        verify(repository, never()).linkBatchCase(111L, 222L);
        verify(repository, never()).linkBatchCase(111L, 999L);
    }

    @Test
    void linkBatchCaseTransferRequestMovesToReferForEnforcement() {
        when(batchCaseRepository.exists(222L)).thenReturn(true);
        when(batchCaseRepository.find(222L)).thenReturn(batch(BatchOperation.TRANSFER_REQUEST));

        SubmitResponse<CaseState> response = linkBatchCase(
            111L,
            linkPayload("222", BatchOperation.TRANSFER_REQUEST)
        );

        verify(batchCaseRepository).linkPcnCase(222L, 111L);
        assertThat(response.getState()).isEqualTo(CaseState.REFER_FOR_ENFORCEMENT);
    }

    @Test
    void linkBatchCaseCaseClosureRequestsMovesToClosed() {
        when(batchCaseRepository.exists(222L)).thenReturn(true);
        when(batchCaseRepository.find(222L)).thenReturn(batch(BatchOperation.CASE_CLOSURE_REQUESTS));

        SubmitResponse<CaseState> response = linkBatchCase(
            111L,
            linkPayload("222", BatchOperation.CASE_CLOSURE_REQUESTS)
        );

        verify(batchCaseRepository).linkPcnCase(222L, 111L);
        assertThat(response.getState()).isEqualTo(CaseState.CLOSED);
    }

    @Test
    void linkBatchCaseWarrantAuthRequestsLeavesStateUnchanged() {
        when(batchCaseRepository.exists(222L)).thenReturn(true);
        when(batchCaseRepository.find(222L)).thenReturn(batch(BatchOperation.WARRANT_AUTH_REQUESTS));

        SubmitResponse<CaseState> response = linkBatchCase(
            111L,
            linkPayload("222", BatchOperation.WARRANT_AUTH_REQUESTS)
        );

        verify(batchCaseRepository).linkPcnCase(222L, 111L);
        assertThat(response.getState()).isNull();
    }

    @Test
    void linkBatchCaseRejectsMissingBatchLinkCase() {
        TecCase data = new TecCase();
        data.setBatchLinkType(BatchOperation.REGISTRATION);

        assertThatThrownBy(() -> linkBatchCase(111L, data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchLinkCase.CaseReference is required");
    }

    @Test
    void linkBatchCaseRejectsMissingBatchLinkType() {
        TecCase data = new TecCase();
        data.setBatchLinkCase(CaseLink.builder().caseReference("222").build());

        assertThatThrownBy(() -> linkBatchCase(111L, data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchLinkType is required");
    }

    @Test
    void linkBatchCaseRejectsTypeMismatch() {
        when(batchCaseRepository.exists(222L)).thenReturn(true);
        when(batchCaseRepository.find(222L)).thenReturn(batch(BatchOperation.REGISTRATION));

        TecCase data = linkPayload("222", BatchOperation.WARRANT_AUTH_REQUESTS);

        assertThatThrownBy(() -> linkBatchCase(111L, data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match batch operation");
    }

    @Test
    void linkBatchCaseRejectsRegistrationAlreadyLinkedElsewhere() {
        when(batchCaseRepository.exists(222L)).thenReturn(true);
        when(batchCaseRepository.find(222L)).thenReturn(batch(BatchOperation.REGISTRATION));
        // Current registration only. An older registration's batch does not block this link.
        when(repository.findBatchCaseReference(111L)).thenReturn(888L);

        TecCase data = linkPayload("222", BatchOperation.REGISTRATION);

        assertThatThrownBy(() -> linkBatchCase(111L, data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already linked to batch case 888");
    }

    @Test
    void linkBatchCaseRejectsMissingBatch() {
        when(batchCaseRepository.exists(999L)).thenReturn(false);

        TecCase data = linkPayload("999", BatchOperation.REGISTRATION);

        assertThatThrownBy(() -> linkBatchCase(111L, data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No TEC_BATCH case found");
    }

    @Test
    void parseCaseReferenceStripsHyphens() {
        assertThat(TecCaseConfiguration.parseCaseReference("1755-0000-0000-0001"))
            .isEqualTo(1755000000000001L);
    }

    private static TecCase linkPayload(String batchRef, BatchOperation type) {
        TecCase data = new TecCase();
        data.setBatchLinkCase(CaseLink.builder()
            .caseReference(batchRef)
            .caseType(BatchCaseConfiguration.CASE_TYPE)
            .build());
        data.setBatchLinkType(type);
        return data;
    }

    private static BatchCase batch(BatchOperation operation) {
        BatchCase batch = new BatchCase();
        batch.setOperation(operation);
        return batch;
    }

    @SuppressWarnings("unchecked")
    private SubmitResponse<CaseState> linkBatchCase(long caseReference, TecCase data) {
        return (SubmitResponse<CaseState>) ReflectionTestUtils.invokeMethod(
            configuration,
            "linkBatchCase",
            new EventPayload<>(caseReference, data, null)
        );
    }
}
