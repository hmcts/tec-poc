package uk.gov.hmcts.reform.tecpoc.ccd;

import uk.gov.hmcts.ccd.sdk.api.HasLabel;

public enum GeneralApplicationApplicant implements HasLabel {

    LOCAL_AUTHORITY("Local authority"),
    RESPONDENT("Respondent");

    private final String label;

    GeneralApplicationApplicant(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
