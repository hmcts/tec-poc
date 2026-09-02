package uk.gov.hmcts.reform.tecpoc.ccd;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.CaseView;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;

@Component
@RequiredArgsConstructor
public class TecCaseView implements CaseView<TecCase, CaseState> {

    private final TecCaseRepository repository;

    private static final String FORM_VALIDATION_NOT_RECORDED = "Not validated";

    @Override
    public TecCase getCase(CaseViewRequest<CaseState> request) {
        TecCase tecCase = repository.find(request.caseRef());
        tecCase.setTasksMarkdown(
            TecPrototypeTasks.markdownFor(request.caseRef(), request.state(), tecCase)
        );
        FormValidationResult validationResult = tecCase.getFormValidationResult();
        tecCase.setFormValidationResultDisplay(
            validationResult == null ? FORM_VALIDATION_NOT_RECORDED : validationResult.getLabel()
        );
        return tecCase;
    }
}
