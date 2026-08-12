package uk.gov.hmcts.reform.tecpoc.http;

import uk.gov.hmcts.reform.tecpoc.ccd.CaseState;

public record CreateTecCaseResponse(long caseReference, CaseState state) {
}
