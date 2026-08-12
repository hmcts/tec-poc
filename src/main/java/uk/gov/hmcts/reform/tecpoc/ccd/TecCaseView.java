package uk.gov.hmcts.reform.tecpoc.ccd;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.CaseView;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;

@Component
@RequiredArgsConstructor
public class TecCaseView implements CaseView<TecCase, CaseState> {

    private final TecCaseRepository repository;

    @Override
    public TecCase getCase(CaseViewRequest<CaseState> request) {
        return repository.find(request.caseRef());
    }
}
