package uk.gov.hmcts.reform.tecpoc.ccd;

import uk.gov.hmcts.ccd.sdk.api.CCD;

public enum CaseState {

    @CCD(label = "Pending Case Issued")
    PENDING_CASE_ISSUED,

    @CCD(label = "Case Issued")
    CASE_ISSUED,

    @CCD(label = "Awaiting Respondent Response")
    AWAITING_RESPONDENT_RESPONSE,

    @CCD(label = "Closed")
    CLOSED
}
