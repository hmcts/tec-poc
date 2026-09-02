package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.ComponentLauncher;
import uk.gov.hmcts.ccd.sdk.type.FieldType;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TecCase {

    @CCD(label = "File identifier")
    private String fileIdentifier;

    @CCD(label = "Batch identifier")
    private String batchIdentifier;

    @CCD(label = "Penalty charge number")
    private String penaltyChargeNumber;

    @CCD(label = "Respondent details 1")
    private String respondentDetails1;

    @CCD(label = "Respondent details 2")
    private String respondentDetails2;

    @CCD(label = "Respondent details 3")
    private String respondentDetails3;

    @CCD(label = "Respondent details 4")
    private String respondentDetails4;

    @CCD(label = "Respondent details 5")
    private String respondentDetails5;

    @CCD(label = "Respondent details 6")
    private String respondentDetails6;

    @CCD(label = "Vehicle registration number")
    private String vehicleRegistrationNumber;

    @CCD(label = "Nature of offence")
    private String natureOfOffence;

    @CCD(label = "Date charge certificate served")
    private String dateChargeCertificateServed;

    @CCD(label = "Amount due", typeOverride = FieldType.MoneyGBP, min = 0, max = 999999)
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer amountDue;

    @CCD(label = "Payment status")
    private String paymentStatus;

    @CCD(label = "Payment reference")
    private String paymentReference;

    @CCD(label = "Closure reason")
    private String closureReason;

    @CCD(label = "Registration authorisation document")
    private String registrationDocument;

    @CCD(label = "Registration date")
    private LocalDate registrationDate;

    @CCD(
        label = "Form validation result",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "FormValidationResult"
    )
    private FormValidationResult formValidationResult;

    /**
     * Case-view display for form validation. Always populated so ExUI shows the row even when
     * {@link #formValidationResult} is unset ({@code @JsonInclude(NON_NULL)} would otherwise omit it).
     */
    @CCD(label = "Form validation result")
    private String formValidationResultDisplay;

    @CCD(label = "Case file view")
    private ComponentLauncher caseFileView;

    @CCD(label = "Tasks", searchable = false)
    private String tasksMarkdown;
}
