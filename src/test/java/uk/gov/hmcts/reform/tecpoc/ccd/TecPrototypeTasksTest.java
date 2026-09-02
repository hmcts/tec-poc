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
            .contains("<strong>Verify form validation</strong>")
            .contains("Next steps")
            .contains("/cases/case-details/1788364399834478/trigger/verifyFormValidation")
            .contains("Assign to me")
            .contains("Reassign")
            .contains("Unassigned")
            .contains("Complete registration checks")
            .contains("Chase outstanding payment confirmation");
    }

    @Test
    void shouldHideVerifyTaskOnceFormValidationRecorded() {
        TecCase tecCase = new TecCase();
        tecCase.setFormValidationResult(FormValidationResult.FORM_VALID);

        String markdown = TecPrototypeTasks.markdownFor(1L, CaseState.CASE_ISSUED, tecCase);

        assertThat(markdown)
            .doesNotContain("<strong>Verify form validation</strong>")
            .contains("<strong>Review issued case</strong>");
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
