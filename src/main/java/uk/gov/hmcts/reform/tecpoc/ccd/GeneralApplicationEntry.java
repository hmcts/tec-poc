package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.Document;
import uk.gov.hmcts.ccd.sdk.type.FieldType;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

/**
 * Event-only form for {@code enterGeneralApplication}. Unwrapped onto the case
 * with the {@code genApp} prefix so page show conditions can see the answers.
 * CCD field ids capitalise the first letter after that prefix ({@code genAppFeeReceived}).
 * Each property is named explicitly. A naming strategy would also rewrite nested document
 * fields, so {@code document_url} would not bind and submit would reject the upload.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeneralApplicationEntry {

    @CCD(
        label = "Who made the application?",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "GeneralApplicationApplicant"
    )
    @JsonProperty("Applicant")
    private GeneralApplicationApplicant applicant;

    @CCD(label = "What date was the application received?", hint = "For example, 16 4 2021")
    @JsonProperty("DateReceived")
    private LocalDate dateReceived;

    @CCD(
        label = "Which type of application has the applicant made?",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "GeneralApplicationType"
    )
    @JsonProperty("ApplicationType")
    private GeneralApplicationType applicationType;

    @CCD(label = "Which categories apply?", typeOverride = FieldType.TextArea)
    @JsonProperty("SomethingElseDetails")
    private String somethingElseDetails;

    @CCD(
        label = "Is there a hearing for this case in the next 14 days?",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "YesNo"
    )
    @JsonProperty("Within14Days")
    private YesNo within14Days;

    @CCD(
        label = "Has HMCTS received the application fee?",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "YesNo"
    )
    @JsonProperty("FeeReceived")
    private YesNo feeReceived;

    @CCD(
        label = "Enter the amount received",
        typeOverride = FieldType.MoneyGBP,
        min = 0,
        max = 999999
    )
    @JsonProperty("FeeAmountReceived")
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer feeAmountReceived;

    @CCD(
        label = "Has the applicant included a Help With Fees reference number on their application?",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "YesNo"
    )
    @JsonProperty("AppliedForHwf")
    private YesNo appliedForHwf;

    @CCD(label = "Enter their Help with Fees reference number", max = 60)
    @JsonProperty("HwfReference")
    private String hwfReference;

    @CCD(
        label = "Do all parties consent to this application?",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "YesNo"
    )
    @JsonProperty("AllPartiesAgree")
    private YesNo allPartiesAgree;

    @CCD(
        label = "Has the applicant asked for this application to be made without notice?",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "YesNo"
    )
    @JsonProperty("WithoutNotice")
    private YesNo withoutNotice;

    @CCD(label = "Upload general application", searchable = false)
    @JsonProperty("Document")
    @JsonDeserialize(using = GeneralApplicationDocumentDeserializer.class)
    private Document document;

    @CCD(
        label = "Upload related evidence",
        hint = "Upload a document to the system",
        typeOverride = FieldType.Collection,
        typeParameterOverride = "Document",
        searchable = false
    )
    @JsonProperty("RelatedEvidence")
    private List<ListValue<Document>> relatedEvidence;
}
