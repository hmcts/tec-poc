package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.FieldType;

/**
 * A general application shown on Case details. Populated by {@link TecCaseView}.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeneralApplication {

    @CCD(label = "Rank")
    private Integer rank;

    @CCD(
        label = "Applicant",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "GeneralApplicationApplicant"
    )
    private GeneralApplicationApplicant applicant;

    @CCD(label = "Date received")
    private LocalDate dateReceived;

    @CCD(
        label = "Type",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "GeneralApplicationType"
    )
    private GeneralApplicationType applicationType;

    @CCD(label = "Categories", typeOverride = FieldType.TextArea)
    private String somethingElseDetails;

    @CCD(
        label = "Hearing in the next 14 days",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo"
    )
    private YesNo within14Days;

    @CCD(label = "Fee amount received", typeOverride = FieldType.MoneyGBP, min = 0, max = 999999)
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer feeAmountReceived;

    @CCD(
        label = "Help with Fees",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo"
    )
    private YesNo appliedForHwf;

    @CCD(label = "Help with Fees reference")
    private String hwfReference;

    @CCD(
        label = "All parties consent",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo"
    )
    private YesNo allPartiesAgree;

    @CCD(
        label = "Without notice",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo"
    )
    private YesNo withoutNotice;

    @CCD(
        label = "State",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "GeneralApplicationState"
    )
    private GeneralApplicationState state;
}
