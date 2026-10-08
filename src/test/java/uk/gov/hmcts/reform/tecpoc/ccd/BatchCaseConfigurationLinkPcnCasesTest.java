package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.ccd.sdk.api.EventPayload;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

class BatchCaseConfigurationLinkPcnCasesTest {

    private BatchCaseRepository repository;
    private TecCaseRepository tecCaseRepository;
    private BatchCaseConfiguration configuration;

    @BeforeEach
    void setUp() {
        repository = mock(BatchCaseRepository.class);
        tecCaseRepository = mock(TecCaseRepository.class);
        configuration = new BatchCaseConfiguration(repository, tecCaseRepository);
    }

    @Test
    void linkPcnCasesPersistsRegistrationFkWhenPcnExists() {
        when(repository.find(999L)).thenReturn(batch(BatchOperation.REGISTRATION));
        when(tecCaseRepository.exists(111L)).thenReturn(true);
        when(tecCaseRepository.findBatchCaseReference(111L)).thenReturn(null);

        BatchCase data = caseLinksData("111");

        linkPcnCases(999L, data);

        verify(tecCaseRepository).linkBatchCase(111L, 999L);
        verify(repository, never()).linkPcnCase(999L, 111L);
    }

    @Test
    void linkPcnCasesWritesJoinTableForNonRegistration() {
        when(repository.find(999L)).thenReturn(batch(BatchOperation.WARRANT_AUTH_REQUESTS));
        when(tecCaseRepository.exists(111L)).thenReturn(true);

        BatchCase data = caseLinksData("111");

        linkPcnCases(999L, data);

        verify(repository).linkPcnCase(999L, 111L);
        verify(tecCaseRepository, never()).linkBatchCase(111L, 999L);
        verify(tecCaseRepository, never()).findBatchCaseReference(111L);
    }

    @Test
    void linkPcnCasesAllowsNonRegistrationWhenRegistrationFkAlreadySet() {
        when(repository.find(999L)).thenReturn(batch(BatchOperation.WARRANT_AUTH_REQUESTS));
        when(tecCaseRepository.exists(111L)).thenReturn(true);

        BatchCase data = caseLinksData("111");

        linkPcnCases(999L, data);

        verify(repository).linkPcnCase(999L, 111L);
    }

    @Test
    void linkPcnCasesRejectsMissingPcn() {
        when(repository.find(1L)).thenReturn(batch(BatchOperation.REGISTRATION));
        when(tecCaseRepository.exists(999L)).thenReturn(false);

        BatchCase data = new BatchCase();
        data.setCaseLinks(List.of(ListValue.<CaseLink>builder()
            .value(CaseLink.builder().caseReference("999").build())
            .build()));

        assertThatThrownBy(() -> linkPcnCases(1L, data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No TEC PCN case found");
    }

    @Test
    void linkPcnCasesAllowsIdempotentRelinkToSameRegistrationBatch() {
        when(repository.find(1789997265350555L)).thenReturn(batch(BatchOperation.REGISTRATION));
        // Long identity != must not reject when values are equal (CCD case refs are outside cache)
        when(tecCaseRepository.exists(1789997297857502L)).thenReturn(true);
        when(tecCaseRepository.findBatchCaseReference(1789997297857502L))
            .thenReturn(Long.valueOf(1789997265350555L));

        BatchCase data = caseLinksData("1789997297857502");

        linkPcnCases(1789997265350555L, data);

        verify(tecCaseRepository).linkBatchCase(1789997297857502L, 1789997265350555L);
    }

    @Test
    void linkPcnCasesRejectsPcnAlreadyLinkedElsewhereForRegistration() {
        when(repository.find(999L)).thenReturn(batch(BatchOperation.REGISTRATION));
        when(tecCaseRepository.exists(111L)).thenReturn(true);
        // Current registration only. An older registration's batch does not block this link.
        when(tecCaseRepository.findBatchCaseReference(111L)).thenReturn(888L);

        BatchCase data = caseLinksData("111");

        assertThatThrownBy(() -> linkPcnCases(999L, data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already linked to batch case 888");
    }

    private static BatchCase batch(BatchOperation operation) {
        BatchCase batch = new BatchCase();
        batch.setOperation(operation);
        return batch;
    }

    private static BatchCase caseLinksData(String pcnRef) {
        BatchCase data = new BatchCase();
        data.setCaseLinks(List.of(ListValue.<CaseLink>builder()
            .id(UUID.randomUUID().toString())
            .value(CaseLink.builder()
                .caseReference(pcnRef)
                .caseType(TecCaseConfiguration.CASE_TYPE)
                .reasonForLink(BatchRegistrationCaseLinks.reasonForLink())
                .build())
            .build()));
        return data;
    }

    @SuppressWarnings("unchecked")
    private void linkPcnCases(long caseReference, BatchCase data) {
        ReflectionTestUtils.invokeMethod(
            configuration,
            "linkPcnCases",
            new EventPayload<>(caseReference, data, null)
        );
    }
}
