package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.hmcts.ccd.sdk.api.HasLabel;

/**
 * Upload batch file radio options. Labels include a short description because CCD
 * {@code FixedRadioList} has no per-option hint. Mapped to {@link BatchOperation} on submit.
 */
public enum BatchTypeOption implements HasLabel {

    @JsonProperty("registration")
    REGISTRATION(
        BatchOperation.REGISTRATION,
        "Create new PCN registrations from an uploaded batch file"
    ),

    @JsonProperty("warrantAuthRequests")
    WARRANT_AUTH_REQUESTS(
        BatchOperation.WARRANT_AUTH_REQUESTS,
        "Submit PCNs for warrant authorisation"
    ),

    @JsonProperty("warrantReissueRequests")
    WARRANT_REISSUE_REQUESTS(
        BatchOperation.WARRANT_REISSUE_REQUESTS,
        "Request reissue of warrants for PCNs"
    ),

    @JsonProperty("outOfTimeDecisions")
    OUT_OF_TIME_DECISIONS(
        BatchOperation.OUT_OF_TIME_DECISIONS,
        "Submit out-of-time application decisions"
    ),

    @JsonProperty("changeOfAddress")
    CHANGE_OF_ADDRESS(
        BatchOperation.CHANGE_OF_ADDRESS,
        "Update respondent addresses from a batch file"
    ),

    @JsonProperty("caseClosureRequests")
    CASE_CLOSURE_REQUESTS(
        BatchOperation.CASE_CLOSURE_REQUESTS,
        "Request closure of PCN cases in bulk"
    );

    private final BatchOperation operation;
    private final String description;

    BatchTypeOption(BatchOperation operation, String description) {
        this.operation = operation;
        this.description = description;
    }

    @Override
    public String getLabel() {
        return operation.getLabel() + " — " + description;
    }

    public BatchOperation toOperation() {
        return operation;
    }
}
