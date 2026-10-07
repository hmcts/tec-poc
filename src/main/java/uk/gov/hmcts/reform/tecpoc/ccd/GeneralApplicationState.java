package uk.gov.hmcts.reform.tecpoc.ccd;

import uk.gov.hmcts.ccd.sdk.api.HasLabel;

public enum GeneralApplicationState implements HasLabel {

    GEN_APP_ISSUED("Issued");

    private final String label;

    GeneralApplicationState(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
