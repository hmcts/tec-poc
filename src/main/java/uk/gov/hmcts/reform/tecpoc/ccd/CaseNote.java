package uk.gov.hmcts.reform.tecpoc.ccd;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;

import static uk.gov.hmcts.ccd.sdk.type.FieldType.TextArea;

/**
 * One stored case note, shown on the Notes tab. Populated by {@link TecCaseView}.
 */
@Getter
@Setter
public class CaseNote {

    @CCD(label = "Created by")
    private String createdBy;

    @CCD(label = "Created on")
    private LocalDateTime createdOn;

    @CCD(
        label = "Note",
        typeOverride = TextArea
    )
    private String note;
}
