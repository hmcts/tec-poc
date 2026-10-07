package uk.gov.hmcts.reform.tecpoc.ccd;

import uk.gov.hmcts.ccd.sdk.api.HasLabel;

public enum GeneralApplicationType implements HasLabel {

    ADJOURN("Adjourn"),
    SET_ASIDE("Set aside"),
    SOMETHING_ELSE("Something else");

    private final String label;

    GeneralApplicationType(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
