package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.hmcts.ccd.sdk.api.HasLabel;

public enum FormValidationResult implements HasLabel {

    @JsonProperty("formValid")
    FORM_VALID("Form valid"),

    @JsonProperty("formInvalid")
    FORM_INVALID("Form invalid");

    private final String label;

    FormValidationResult(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
