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
        tecCase.setApplicationForm(ApplicationForm.TE9);

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
        tecCase.setApplicationForm(ApplicationForm.PE3);
        tecCase.setTimeExtensionForm(TimeExtensionForm.TE7);

        String markdown = TecPrototypeTasks.markdownFor(1L, CaseState.CASE_ISSUED, tecCase);

        assertThat(markdown)
            .contains("/cases/case-details/1/trigger/editPe3Application")
            .contains("/cases/case-details/1/trigger/editTe7Application")
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
}
