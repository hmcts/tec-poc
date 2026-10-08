package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;

class TecCaseViewFormValidationTest {

    @Test
    void getCaseKeepsEachFormsValidationMessage() {
        TecCaseRepository repository = mock(TecCaseRepository.class);
        TecCase stored = new TecCase();
        stored.setFormValidationResult(FormValidationResult.FORM_VALID);
        Te9CaseDetails te9 = new Te9CaseDetails();
        te9.setFormValidationResultDisplay("Invalid - fields missing");
        Te7CaseDetails te7 = new Te7CaseDetails();
        te7.setFormValidationResultDisplay("Form valid");
        stored.setTe9Details(te9);
        stored.setTe7Details(te7);
        when(repository.find(1L)).thenReturn(stored);
        when(repository.findRegistrations(1L)).thenReturn(List.of());
        when(repository.findDocuments(1L)).thenReturn(List.of());
        when(repository.findWarrantAuthorisations(1L)).thenReturn(List.of());
        when(repository.findGeneralApplications(1L)).thenReturn(List.of());

        TecCase result = new TecCaseView(repository).getCase(
            new CaseViewRequest<>(1L, CaseState.AWAITING_OOT_VALIDATION)
        );

        assertThat(result.getTe9Details().getFormValidationResultDisplay())
            .isEqualTo("Invalid - fields missing");
        assertThat(result.getTe7Details().getFormValidationResultDisplay())
            .isEqualTo("Form valid");
    }

    @Test
    void getCaseDefaultsABlankFormValidationMessage() {
        TecCaseRepository repository = mock(TecCaseRepository.class);
        TecCase stored = new TecCase();
        stored.setPe3Details(new Pe3CaseDetails());
        when(repository.find(2L)).thenReturn(stored);
        when(repository.findRegistrations(2L)).thenReturn(List.of());
        when(repository.findDocuments(2L)).thenReturn(List.of());
        when(repository.findWarrantAuthorisations(2L)).thenReturn(List.of());
        when(repository.findGeneralApplications(2L)).thenReturn(List.of());

        TecCase result = new TecCaseView(repository).getCase(
            new CaseViewRequest<>(2L, CaseState.AWAITING_OOT_VALIDATION)
        );

        assertThat(result.getPe3Details().getFormValidationResultDisplay())
            .isEqualTo(TecCaseRepository.DEFAULT_FORM_VALIDATION_RESULT);
    }
}
