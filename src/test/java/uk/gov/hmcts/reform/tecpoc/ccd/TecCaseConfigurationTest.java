package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.EventPayload;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.ccd.sdk.api.callback.SubmitResponse;

class TecCaseConfigurationTest {

    private TecCaseRepository repository;
    private TecCaseConfiguration configuration;

    @BeforeEach
    void setUp() {
        repository = mock(TecCaseRepository.class);
        configuration = new TecCaseConfiguration(
            repository,
            mock(BatchCaseRepository.class)
        );
    }

    @Test
    void verifyFormValidationMapsCommentToEventMetadataDescription() {
        TecCase data = new TecCase();
        data.setFormValidationResult(FormValidationResult.FORM_VALID);
        data.setFormValidationComment("  Looks complete  ");

        SubmitResponse<CaseState> response = verifyFormValidation(1L, data);

        verify(repository).recordFormValidation(1L, FormValidationResult.FORM_VALID);
        assertThat(response.getEventMetadata()).isNotNull();
        assertThat(response.getEventMetadata().getDescription()).isEqualTo("Looks complete");
    }

    @Test
    void verifyFormValidationOmitsEventMetadataWhenCommentBlank() {
        TecCase data = new TecCase();
        data.setFormValidationResult(FormValidationResult.FORM_INVALID);
        data.setFormValidationComment("   ");

        SubmitResponse<CaseState> response = verifyFormValidation(2L, data);

        verify(repository).recordFormValidation(2L, FormValidationResult.FORM_INVALID);
        assertThat(response.getEventMetadata()).isNull();
    }

    @Test
    void verifyFormValidationOmitsEventMetadataWhenCommentNull() {
        TecCase data = new TecCase();
        data.setFormValidationResult(FormValidationResult.FORM_VALID);

        SubmitResponse<CaseState> response = verifyFormValidation(3L, data);

        verify(repository).recordFormValidation(3L, FormValidationResult.FORM_VALID);
        assertThat(response.getEventMetadata()).isNull();
    }

    @Test
    void draftOotRejectionEmailPrefillsNotifyDraftWhenFormInvalid() {
        TecCase data = new TecCase();
        data.setFormValidationResult(FormValidationResult.FORM_INVALID);

        AboutToStartOrSubmitResponse<TecCase, CaseState> response = draftOotRejectionEmail(data);

        assertThat(response.getData().getOotRejectionEmail())
            .isEqualTo(TecCaseConfiguration.OOT_REJECTION_EMAIL_DRAFT)
            .contains("((respondent_name))")
            .contains("Your application has been rejected because it is not valid.");
    }

    @Test
    void draftOotRejectionEmailKeepsAnEditedEmail() {
        TecCase data = new TecCase();
        data.setFormValidationResult(FormValidationResult.FORM_INVALID);
        data.setOotRejectionEmail("Dear ((respondent_name)),\n\nEdited.");

        AboutToStartOrSubmitResponse<TecCase, CaseState> response = draftOotRejectionEmail(data);

        assertThat(response.getData().getOotRejectionEmail()).isEqualTo("Dear ((respondent_name)),\n\nEdited.");
    }

    @Test
    void draftOotRejectionEmailClearsDraftWhenFormValid() {
        TecCase data = new TecCase();
        data.setFormValidationResult(FormValidationResult.FORM_VALID);
        data.setOotRejectionEmail(TecCaseConfiguration.OOT_REJECTION_EMAIL_DRAFT);

        AboutToStartOrSubmitResponse<TecCase, CaseState> response = draftOotRejectionEmail(data);

        assertThat(response.getData().getOotRejectionEmail()).isNull();
    }

    @Test
    void verifyFormValidationRecordsRejectionEmailWhenCommentBlank() {
        TecCase data = new TecCase();
        data.setFormValidationResult(FormValidationResult.FORM_INVALID);
        data.setOotRejectionEmail("  " + TecCaseConfiguration.OOT_REJECTION_EMAIL_DRAFT + "  ");

        SubmitResponse<CaseState> response = verifyFormValidation(8L, data);

        assertThat(response.getEventMetadata().getDescription())
            .isEqualTo(TecCaseConfiguration.OOT_REJECTION_EMAIL_DRAFT);
    }

    @Test
    void verifyFormValidationIgnoresRejectionEmailWhenFormValid() {
        TecCase data = new TecCase();
        data.setFormValidationResult(FormValidationResult.FORM_VALID);
        data.setOotRejectionEmail(TecCaseConfiguration.OOT_REJECTION_EMAIL_DRAFT);

        SubmitResponse<CaseState> response = verifyFormValidation(9L, data);

        assertThat(response.getEventMetadata()).isNull();
    }

    @Test
    void reviewOotRefusalDecisionOverturnRevokesTheCase() {
        TecCase data = new TecCase();
        data.setOotRefusalReviewDecision(OotRefusalReviewDecision.OVERTURN);

        SubmitResponse<CaseState> response = reviewOotRefusalDecision(4L, data);

        assertThat(response.getState()).isEqualTo(CaseState.CASE_REVOKED_LA_REFUSAL_OVERTURNED);
    }

    @Test
    void reviewOotRefusalDecisionUpholdReturnsToIssuedWarrantWhenOneIsActive() {
        when(repository.findWarrantAuthorisations(5L)).thenReturn(List.of(
            new TecCaseWarrantAuthorisation(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                LocalDate.of(2026, 9, 22),
                LocalDate.of(2027, 9, 22),
                WarrantAuthorisationStatus.ACTIVE
            )
        ));
        TecCase data = new TecCase();
        data.setOotRefusalReviewDecision(OotRefusalReviewDecision.UPHOLD);

        SubmitResponse<CaseState> response = reviewOotRefusalDecision(5L, data);

        assertThat(response.getState()).isEqualTo(CaseState.WARRANT_AUTHORISATION_ISSUED);
    }

    @Test
    void reviewOotRefusalDecisionUpholdStaysPendingWhenNoActiveWarrant() {
        when(repository.findWarrantAuthorisations(6L)).thenReturn(List.of());
        TecCase data = new TecCase();
        data.setOotRefusalReviewDecision(OotRefusalReviewDecision.UPHOLD);

        SubmitResponse<CaseState> response = reviewOotRefusalDecision(6L, data);

        assertThat(response.getState()).isEqualTo(CaseState.PENDING_REFUSAL_DECISION);
    }

    @Test
    void reviewOotRefusalDecisionRequiresAChoice() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> reviewOotRefusalDecision(7L, new TecCase())
        );
    }

    @Test
    void applyWarrantAuthorisationPersistsAuthorisation() {
        WarrantAuthorisation authorisation = new WarrantAuthorisation();
        authorisation.setDateOfIssue(java.time.LocalDate.of(2026, 9, 22));
        authorisation.setDateOfExpiry(java.time.LocalDate.of(2027, 9, 22));
        authorisation.setStatus(WarrantAuthorisationStatus.ACTIVE);

        TecCase data = new TecCase();
        data.setWarrantAuthorisation(authorisation);

        SubmitResponse<CaseState> response = applyWarrantAuthorisation(10L, data);

        verify(repository).insertWarrantAuthorisation(10L, authorisation);
        assertThat(response.getState()).isEqualTo(CaseState.WARRANT_AUTHORISATION_ISSUED);
    }

    @Test
    void applyWarrantAuthorisationExpiredMovesToExpiredState() {
        WarrantAuthorisation authorisation = new WarrantAuthorisation();
        authorisation.setDateOfIssue(java.time.LocalDate.of(2025, 9, 22));
        authorisation.setDateOfExpiry(java.time.LocalDate.of(2026, 9, 22));
        authorisation.setStatus(WarrantAuthorisationStatus.EXPIRED);

        TecCase data = new TecCase();
        data.setWarrantAuthorisation(authorisation);

        SubmitResponse<CaseState> response = applyWarrantAuthorisation(17L, data);

        verify(repository).insertWarrantAuthorisation(17L, authorisation);
        assertThat(response.getState()).isEqualTo(CaseState.WARRANT_AUTHORISATION_EXPIRED);
    }

    @Test
    void applyWarrantAuthorisationCancelledLeavesStateUnchanged() {
        WarrantAuthorisation authorisation = new WarrantAuthorisation();
        authorisation.setDateOfIssue(java.time.LocalDate.of(2026, 9, 22));
        authorisation.setDateOfExpiry(java.time.LocalDate.of(2027, 9, 22));
        authorisation.setStatus(WarrantAuthorisationStatus.CANCELLED);

        TecCase data = new TecCase();
        data.setWarrantAuthorisation(authorisation);

        SubmitResponse<CaseState> response = applyWarrantAuthorisation(18L, data);

        verify(repository).insertWarrantAuthorisation(18L, authorisation);
        assertThat(response.getState()).isNull();
    }

    @Test
    void setCaseStateReturnsRequestedState() {
        TecCase data = new TecCase();
        data.setTargetCaseState("CLOSED");

        SubmitResponse<CaseState> response = setCaseState(11L, data);

        assertThat(response.getState()).isEqualTo(CaseState.CLOSED);
    }

    @Test
    void setCaseStateReturnsWarrantAuthorisationIssued() {
        TecCase data = new TecCase();
        data.setTargetCaseState("WARRANT_AUTHORISATION_ISSUED");

        SubmitResponse<CaseState> response = setCaseState(14L, data);

        assertThat(response.getState()).isEqualTo(CaseState.WARRANT_AUTHORISATION_ISSUED);
    }

    @Test
    void setCaseStateReturnsReferForEnforcement() {
        TecCase data = new TecCase();
        data.setTargetCaseState("REFER_FOR_ENFORCEMENT");

        SubmitResponse<CaseState> response = setCaseState(15L, data);

        assertThat(response.getState()).isEqualTo(CaseState.REFER_FOR_ENFORCEMENT);
    }

    @Test
    void setCaseStateRequiresTarget() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> setCaseState(12L, new TecCase())
        );
    }

    @Test
    void setCaseStateRejectsUnknownState() {
        TecCase data = new TecCase();
        data.setTargetCaseState("NOT_A_STATE");

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> setCaseState(13L, data)
        );
    }

    @Test
    void setCaseStateRejectsExceptionPendingReviewOnPcn() {
        TecCase data = new TecCase();
        data.setTargetCaseState("EXCEPTION_PENDING_REVIEW");

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> setCaseState(16L, data)
        );
    }

    @SuppressWarnings("unchecked")
    private SubmitResponse<CaseState> reviewOotRefusalDecision(long caseReference, TecCase data) {
        return (SubmitResponse<CaseState>) ReflectionTestUtils.invokeMethod(
            configuration,
            "reviewOotRefusalDecision",
            new EventPayload<>(caseReference, data, null)
        );
    }

    private AboutToStartOrSubmitResponse<TecCase, CaseState> draftOotRejectionEmail(TecCase data) {
        CaseDetails<TecCase, CaseState> details = CaseDetails.<TecCase, CaseState>builder()
            .data(data)
            .build();
        return configuration.draftOotRejectionEmail(details, null);
    }

    @SuppressWarnings("unchecked")
    private SubmitResponse<CaseState> verifyFormValidation(long caseReference, TecCase data) {
        return (SubmitResponse<CaseState>) ReflectionTestUtils.invokeMethod(
            configuration,
            "verifyFormValidation",
            new EventPayload<>(caseReference, data, null)
        );
    }

    @SuppressWarnings("unchecked")
    private SubmitResponse<CaseState> applyWarrantAuthorisation(long caseReference, TecCase data) {
        return (SubmitResponse<CaseState>) ReflectionTestUtils.invokeMethod(
            configuration,
            "applyWarrantAuthorisation",
            new EventPayload<>(caseReference, data, null)
        );
    }

    @SuppressWarnings("unchecked")
    private SubmitResponse<CaseState> setCaseState(long caseReference, TecCase data) {
        return (SubmitResponse<CaseState>) ReflectionTestUtils.invokeMethod(
            configuration,
            "setCaseState",
            new EventPayload<>(caseReference, data, null)
        );
    }
}
