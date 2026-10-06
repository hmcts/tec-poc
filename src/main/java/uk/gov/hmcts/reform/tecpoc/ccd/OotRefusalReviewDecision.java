package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.hmcts.ccd.sdk.api.HasLabel;

public enum OotRefusalReviewDecision implements HasLabel {

    @JsonProperty("uphold")
    UPHOLD("Uphold decision"),

    @JsonProperty("overturn")
    OVERTURN("Overturn decision");

    private final String label;

    OotRefusalReviewDecision(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
