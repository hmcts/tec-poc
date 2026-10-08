package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TecPrototypeTasksTest {

    @Test
    void shouldRenderActiveTasksHeadingAndAssignedTaskWithNextSteps() {
        TecCase tecCase = new TecCase();

        String markdown = TecPrototypeTasks.markdownFor(1788364399834478L, CaseState.CASE_ISSUED, tecCase);

        assertThat(markdown)
            .contains("<h2 class=\"govuk-heading-m\">Active tasks</h2>")
            .contains("<strong>Validate OOT application</strong>")
            .contains("Next steps")
            .contains("/cases/case-details/1788364399834478/trigger/verifyFormValidation")
            .contains("Assign to me")
            .contains("Reassign")
            .contains("Unassigned")
            .contains("Complete registration checks")
            .doesNotContain("Edit TE9 application")
            .doesNotContain("editTe9Application")
            .contains("Chase outstanding payment confirmation");
    }

    @Test
    void shouldHideVerifyTaskOnceFormValidationRecorded() {
        TecCase tecCase = new TecCase();
        tecCase.setFormValidationResult(FormValidationResult.FORM_VALID);

        String markdown = TecPrototypeTasks.markdownFor(1L, CaseState.CASE_ISSUED, tecCase);

        assertThat(markdown)
            .doesNotContain("<strong>Validate OOT application</strong>")
            .contains("<strong>Review issued case</strong>");
    }

    @Test
    void shouldShowEditTe9ApplicationTaskWhenTe9Recorded() {
        TecCase tecCase = new TecCase();
        tecCase.setTe9Details(te9());

        String markdown = TecPrototypeTasks.markdownFor(
            1L,
            CaseState.AWAITING_RESPONDENT_RESPONSE,
            tecCase
        );

        assertThat(markdown)
            .contains("<strong>Edit TE9 application</strong>")
            .contains("/cases/case-details/1/trigger/editTe9Application")
            .doesNotContain("editPe3Application")
            .doesNotContain("editTe7Application")
            .doesNotContain("editPe2Application");
    }

    @Test
    void shouldShowEditTasksForEachFormOnTheCase() {
        TecCase tecCase = new TecCase();
        tecCase.setPe3Details(pe3());
        tecCase.setTe7Details(te7());

        String markdown = TecPrototypeTasks.markdownFor(1L, CaseState.CASE_ISSUED, tecCase);

        assertThat(markdown)
            .contains("/cases/case-details/1/trigger/editPe3Application")
            .contains("/cases/case-details/1/trigger/editTe7Application")
            .doesNotContain("editTe9Application")
            .doesNotContain("editPe2Application");
    }

    @Test
    void shouldOfferFormEditsAndValidateOotApplicationWhileAwaitingOotValidation() {
        TecCase tecCase = new TecCase();
        tecCase.setTe9Details(te9());
        tecCase.setTe7Details(te7());

        String markdown = TecPrototypeTasks.markdownFor(
            1L,
            CaseState.AWAITING_OOT_VALIDATION,
            tecCase
        );

        assertThat(markdown)
            .contains("<strong>Validate OOT application</strong>")
            .contains("/cases/case-details/1/trigger/verifyFormValidation")
            .contains("/cases/case-details/1/trigger/editTe9Application")
            .contains("/cases/case-details/1/trigger/editTe7Application")
            .doesNotContain("editPe3Application")
            .doesNotContain("editPe2Application");
    }

    @Test
    void shouldOfferPeFormEditsWhileAwaitingOotValidation() {
        TecCase tecCase = new TecCase();
        tecCase.setPe3Details(pe3());
        tecCase.setPe2Details(pe2());

        String markdown = TecPrototypeTasks.markdownFor(
            1L,
            CaseState.AWAITING_OOT_VALIDATION,
            tecCase
        );

        assertThat(markdown)
            .contains("/cases/case-details/1/trigger/verifyFormValidation")
            .contains("/cases/case-details/1/trigger/editPe3Application")
            .contains("/cases/case-details/1/trigger/editPe2Application")
            .doesNotContain("editTe9Application")
            .doesNotContain("editTe7Application");
    }

    @Test
    void shouldHideFormEditNextStepsWhileAwaitingLaOotResponse() {
        TecCase tecCase = new TecCase();
        tecCase.setTe9Details(te9());
        tecCase.setPe2Details(pe2());

        String markdown = TecPrototypeTasks.markdownFor(
            1L,
            CaseState.AWAITING_LA_OOT_RESPONSE,
            tecCase
        );

        assertThat(markdown)
            .contains("There are no active tasks for this case.")
            .doesNotContain("Edit TE9 application")
            .doesNotContain("Edit PE2 application")
            .doesNotContain("editTe9Application")
            .doesNotContain("editPe2Application");
    }

    @Test
    void shouldShowEmptyMessageWhenNoTasksApply() {
        TecCase tecCase = new TecCase();

        String markdown = TecPrototypeTasks.markdownFor(
            1L,
            CaseState.AWAITING_RESPONDENT_RESPONSE,
            tecCase
        );

        assertThat(markdown).contains("There are no active tasks for this case.");
    }

    @Test
    void shouldOfferEditsForEveryFormPresentOnTheCase() {
        TecCase tecCase = new TecCase();
        tecCase.setTe9Details(te9());
        tecCase.setPe3Details(pe3());
        tecCase.setTe7Details(te7());
        tecCase.setPe2Details(pe2());

        String markdown = TecPrototypeTasks.markdownFor(1L, CaseState.CASE_ISSUED, tecCase);

        assertThat(markdown)
            .contains("editTe9Application")
            .contains("editPe3Application")
            .contains("editTe7Application")
            .contains("editPe2Application");
    }

    private static Te9CaseDetails te9() {
        Te9CaseDetails details = new Te9CaseDetails();
        details.setForm(ApplicationForm.TE9);
        return details;
    }

    private static Pe3CaseDetails pe3() {
        Pe3CaseDetails details = new Pe3CaseDetails();
        details.setForm(ApplicationForm.PE3);
        return details;
    }

    private static Te7CaseDetails te7() {
        Te7CaseDetails details = new Te7CaseDetails();
        details.setForm(TimeExtensionForm.TE7);
        return details;
    }

    private static Pe2CaseDetails pe2() {
        Pe2CaseDetails details = new Pe2CaseDetails();
        details.setForm(TimeExtensionForm.PE2);
        return details;
    }
}
