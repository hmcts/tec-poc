package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.EventPayload;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.ccd.sdk.api.callback.SubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.Document;

class BatchUploadJourneyTest {

    private BatchCaseRepository repository;
    private BatchCaseConfiguration configuration;

    @BeforeEach
    void setUp() {
        repository = mock(BatchCaseRepository.class);
        configuration = new BatchCaseConfiguration(repository, mock(TecCaseRepository.class));
    }

    @Test
    void applyValidationPlaceholderSetsExcludedCount() {
        BatchCase data = new BatchCase();

        BatchUploadJourney.applyValidationPlaceholder(data);

        assertThat(data.getExcludedPcnCount()).isEqualTo(23);
    }

    @Test
    void midEventPopulatesValidationPlaceholder() {
        BatchCase data = new BatchCase();
        data.setOperation(BatchOperation.WARRANT_AUTH_REQUESTS);
        CaseDetails<BatchCase, BatchCaseState> details = CaseDetails.<BatchCase, BatchCaseState>builder()
            .data(data)
            .build();

        AboutToStartOrSubmitResponse<BatchCase, BatchCaseState> response =
            configuration.populateValidationPlaceholder(details, null);

        assertThat(response.getData().getExcludedPcnCount()).isEqualTo(23);
    }

    @Test
    void confirmationUsesWarrantAuthWordingAndCaseReference() {
        BatchCase data = new BatchCase();
        data.setOperation(BatchOperation.WARRANT_AUTH_REQUESTS);
        data.setExcludedPcnCount(23);

        assertThat(BatchUploadJourney.confirmationHeader(data))
            .isEqualTo("# Warrant auth requests submitted");
        assertThat(BatchUploadJourney.confirmationBody(data, 1_704_123_456_789_012L))
            .contains("Case number: 1704123456789012")
            .contains("PCN warrant authorisations")
            .contains("23 PCNs have not been included")
            .doesNotContain("Manage cases");
    }

    @Test
    void applySubmitDefaultsFillsPocMetadata() {
        BatchCase data = new BatchCase();
        data.setOperation(BatchOperation.REGISTRATION);

        BatchUploadJourney.applySubmitDefaults(42L, data);

        assertThat(data.getReceivedVia()).isEqualTo(BatchReceivedVia.UPLOAD);
        assertThat(data.getReceivedAt()).isNotNull();
        assertThat(data.getLocalAuthority()).isEqualTo(LocalAuthority.MANCHESTER_CITY_COUNCIL);
        assertThat(data.getCaseAccessCategory()).isEqualTo("manchesterCityCouncil");
        assertThat(data.getSubmitterEmail()).isEqualTo("la.submitter@example.com");
        assertThat(data.getPcnCount()).isEqualTo(200);
        assertThat(data.getFileIdentifier()).isEqualTo("RUP00042");
        assertThat(data.getBatchIdentifier()).isEqualTo("RUP000042");
    }

    @Test
    void uploadBatchPersistsBatchAttachesDocumentAndReturnsConfirmation() {
        BatchCase data = new BatchCase();
        data.setBatchTypeSelection(BatchTypeOption.WARRANT_AUTH_REQUESTS);
        data.setBatchStatementOfTruth(List.of(BatchStatementOfTruthAgreement.BELIEVE_TRUE));
        data.setExcludedPcnCount(23);
        Document document = Document.builder()
            .url("http://localhost:4455/cases/documents/" + UUID.randomUUID())
            .binaryUrl("http://localhost:4455/cases/documents/" + UUID.randomUUID() + "/binary")
            .filename("warrant-auth-batch.csv")
            .categoryId("inputs")
            .build();
        data.setBatchFileDocument(document);

        EventPayload<BatchCase, BatchCaseState> event = eventPayload(99L, data);

        @SuppressWarnings("unchecked")
        SubmitResponse<BatchCaseState> response =
            (SubmitResponse<BatchCaseState>) ReflectionTestUtils.invokeMethod(
                configuration,
                "uploadBatch",
                event
            );

        verify(repository).create(eq(99L), eq(data));
        verify(repository).insertDocument(
            eq(99L),
            eq("inputs"),
            eq(document.getUrl()),
            eq(document.getBinaryUrl()),
            eq(document.getFilename())
        );
        assertThat(response.getState()).isEqualTo(BatchCaseState.QUEUED_FOR_PROCESSING);
        assertThat(response.getConfirmationHeader()).isEqualTo("# Warrant auth requests submitted");
        assertThat(response.getConfirmationBody())
            .contains("Case number: 99")
            .doesNotContain("Manage cases");
        assertThat(data.getOperation()).isEqualTo(BatchOperation.WARRANT_AUTH_REQUESTS);
        assertThat(data.getBatchTypeSelection()).isNull();
        assertThat(data.getFileIdentifier()).isEqualTo("RUP00099");
        assertThat(data.getBatchIdentifier()).isEqualTo("RUP000099");
        assertThat(data.getSubmitterEmail()).isEqualTo("la.submitter@example.com");
        assertThat(data.getReceivedVia()).isEqualTo(BatchReceivedVia.UPLOAD);
    }

    @Test
    void completeBatchProcessingCompletes() {
        BatchCase data = new BatchCase();

        @SuppressWarnings("unchecked")
        SubmitResponse<BatchCaseState> response =
            (SubmitResponse<BatchCaseState>) ReflectionTestUtils.invokeMethod(
                configuration,
                "completeBatchProcessing",
                eventPayload(55L, data)
            );

        verify(repository, never()).create(anyLong(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getState()).isEqualTo(BatchCaseState.PROCESSING_COMPLETE);
    }

    @Test
    void failBatchProcessingFails() {
        BatchCase data = new BatchCase();

        @SuppressWarnings("unchecked")
        SubmitResponse<BatchCaseState> response =
            (SubmitResponse<BatchCaseState>) ReflectionTestUtils.invokeMethod(
                configuration,
                "failBatchProcessing",
                eventPayload(55L, data)
            );

        verify(repository, never()).create(anyLong(), org.mockito.ArgumentMatchers.any());
        assertThat(response.getState()).isEqualTo(BatchCaseState.PROCESSING_FAILED);
    }

    @Test
    void uploadBatchRejectsMissingDeclaration() {
        BatchCase data = new BatchCase();
        data.setBatchTypeSelection(BatchTypeOption.REGISTRATION);
        data.setBatchStatementOfTruth(List.of());

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            configuration,
            "uploadBatch",
            eventPayload(1L, data)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("facts stated in this batch request are true");

        verify(repository, never()).create(anyLong(), org.mockito.ArgumentMatchers.any());
        verify(repository, never()).insertDocument(
            anyLong(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        );
    }

    @Test
    void uploadBatchRejectsMissingBatchType() {
        BatchCase data = new BatchCase();
        data.setBatchStatementOfTruth(List.of(BatchStatementOfTruthAgreement.BELIEVE_TRUE));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
            configuration,
            "uploadBatch",
            eventPayload(1L, data)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batch type is required");

        verify(repository, never()).create(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void batchTypeLabelsAreShortForFiltersAndLongForCreateRadios() {
        assertThat(BatchOperation.WARRANT_AUTH_REQUESTS.getLabel())
            .isEqualTo("Warrant auth requests");
        assertThat(BatchTypeOption.WARRANT_AUTH_REQUESTS.getLabel())
            .startsWith("Warrant auth requests — ")
            .contains("warrant authorisation");
        assertThat(BatchTypeOption.WARRANT_AUTH_REQUESTS.toOperation())
            .isEqualTo(BatchOperation.WARRANT_AUTH_REQUESTS);

        assertThat(BatchOperation.TRANSFER_REQUEST.getLabel())
            .isEqualTo("Transfer request");
        assertThat(BatchTypeOption.values())
            .extracting(BatchTypeOption::toOperation)
            .doesNotContain(BatchOperation.TRANSFER_REQUEST);
        BatchTypeOption[] options = BatchTypeOption.values();
        assertThat(options[options.length - 1])
            .isEqualTo(BatchTypeOption.CASE_CLOSURE_REQUESTS);
    }

    @Test
    void hasAcceptedStatementOfTruthRequiresBelieveTrueOption() {
        BatchCase accepted = new BatchCase();
        accepted.setBatchStatementOfTruth(List.of(BatchStatementOfTruthAgreement.BELIEVE_TRUE));
        assertThat(BatchUploadJourney.hasAcceptedStatementOfTruth(accepted)).isTrue();

        BatchCase missing = new BatchCase();
        assertThat(BatchUploadJourney.hasAcceptedStatementOfTruth(missing)).isFalse();

        BatchCase empty = new BatchCase();
        empty.setBatchStatementOfTruth(List.of());
        assertThat(BatchUploadJourney.hasAcceptedStatementOfTruth(empty)).isFalse();
    }

    private static EventPayload<BatchCase, BatchCaseState> eventPayload(long caseReference, BatchCase data) {
        return new EventPayload<>(caseReference, data, null);
    }
}
