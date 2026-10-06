package uk.gov.hmcts.reform.tecpoc.ccd;

import uk.gov.hmcts.ccd.sdk.api.CCD;

public enum CaseState {

    @CCD(label = "Pending Case Issued", hint = "### Case number: ${[CASE_REFERENCE]}")
    PENDING_CASE_ISSUED,

    @CCD(label = "Case Issued", hint = "### Case number: ${[CASE_REFERENCE]}")
    CASE_ISSUED,

    @CCD(label = "Awaiting Respondent Response", hint = "### Case number: ${[CASE_REFERENCE]}")
    AWAITING_RESPONDENT_RESPONSE,

    @CCD(label = "Awaiting OOT Validation", hint = "### Case number: ${[CASE_REFERENCE]}")
    AWAITING_OOT_VALIDATION,

    @CCD(label = "Awaiting LA OOT Response", hint = "### Case number: ${[CASE_REFERENCE]}")
    AWAITING_LA_OOT_RESPONSE,

    @CCD(label = "Case Revoked In Time", hint = "### Case number: ${[CASE_REFERENCE]}")
    CASE_REVOKED_IN_TIME,

    @CCD(label = "Case Revoked OOT Accepted", hint = "### Case number: ${[CASE_REFERENCE]}")
    CASE_REVOKED_OOT_ACCEPTED,

    @CCD(label = "Case Revoked LA No Response", hint = "### Case number: ${[CASE_REFERENCE]}")
    CASE_REVOKED_LA_NO_RESPONSE,

    @CCD(label = "Pending Refusal Decision", hint = "### Case number: ${[CASE_REFERENCE]}")
    PENDING_REFUSAL_DECISION,

    @CCD(label = "Refusal Order", hint = "### Case number: ${[CASE_REFERENCE]}")
    REFUSAL_ORDER,

    @CCD(label = "Case Revoked LA Refusal Overturned", hint = "### Case number: ${[CASE_REFERENCE]}")
    CASE_REVOKED_LA_REFUSAL_OVERTURNED,

    @CCD(label = "Pending OOT Appeal Payment", hint = "### Case number: ${[CASE_REFERENCE]}")
    PENDING_OOT_APPEAL_PAYMENT,

    @CCD(label = "OOT Appeal Payment Confirmed", hint = "### Case number: ${[CASE_REFERENCE]}")
    OOT_APPEAL_PAYMENT_CONFIRMED,

    @CCD(label = "Pending OOT Appeal Decision", hint = "### Case number: ${[CASE_REFERENCE]}")
    PENDING_OOT_APPEAL_DECISION,

    @CCD(label = "OOT Appeal Refused", hint = "### Case number: ${[CASE_REFERENCE]}")
    OOT_APPEAL_REFUSED,

    @CCD(label = "Case Revoked OOT Appeal Accepted", hint = "### Case number: ${[CASE_REFERENCE]}")
    CASE_REVOKED_OOT_APPEAL_ACCEPTED,

    @CCD(label = "Warrant Authorisation Issued", hint = "### Case number: ${[CASE_REFERENCE]}")
    WARRANT_AUTHORISATION_ISSUED,

    @CCD(label = "Warrant Authorisation Expired", hint = "### Case number: ${[CASE_REFERENCE]}")
    WARRANT_AUTHORISATION_EXPIRED,

    @CCD(label = "Refer for Enforcement", hint = "### Case number: ${[CASE_REFERENCE]}")
    REFER_FOR_ENFORCEMENT,

    @CCD(label = "Closed", hint = "### Case number: ${[CASE_REFERENCE]}")
    CLOSED
}
