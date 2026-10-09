package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.FieldType;

/**
 * Case details projection of the current PE2. Field ids are prefixed {@code pe2}.
 * CCD capitalises the first letter after that prefix, so each property is named
 * explicitly ({@code pe2Form}).
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Pe2CaseDetails {

    @CCD(label = "Form validation result", searchable = false)
    @JsonProperty("FormValidationResultDisplay")
    private String formValidationResultDisplay;

    @CCD(
        label = "Form",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionForm",
        searchable = false
    )
    @JsonProperty("Form")
    private TimeExtensionForm form;

    @CCD(label = "Penalty Charge Number", searchable = false)
    @JsonProperty("PenaltyChargeNumber")
    private String penaltyChargeNumber;

    @CCD(label = "Vehicle reg", searchable = false)
    @JsonProperty("VehicleRegistration")
    private String vehicleRegistration;

    @CCD(label = "Applicant", searchable = false)
    @JsonProperty("Applicant")
    private String applicant;

    @CCD(label = "Location of contravention", searchable = false)
    @JsonProperty("LocationOfContravention")
    private String locationOfContravention;

    @CCD(label = "Date of contravention", searchable = false)
    @JsonProperty("DateOfContravention")
    private LocalDate dateOfContravention;

    @CCD(label = "Full name", searchable = false)
    @JsonProperty("FullName")
    private String fullName;

    @CCD(label = "Address", searchable = false)
    @JsonProperty("Address")
    private String address;

    @CCD(label = "Postcode", searchable = false)
    @JsonProperty("Postcode")
    private String postcode;

    @CCD(
        label = "Reasons given",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    @JsonProperty("ReasonsGiven")
    private YesNo reasonsGiven;

    @CCD(
        label = "Signed and dated",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    @JsonProperty("SignedAndDated")
    private YesNo signedAndDated;

    @CCD(label = "Date signed", searchable = false)
    @JsonProperty("DateSigned")
    private LocalDate dateSigned;

    public void copyToTimeExtension(TecCase target) {
        target.setTimeExtensionForm(TimeExtensionForm.PE2);
        target.setTimeExtensionPenaltyChargeNumber(penaltyChargeNumber);
        target.setTimeExtensionVehicleRegistration(vehicleRegistration);
        target.setTimeExtensionApplicant(applicant);
        target.setTimeExtensionLocationOfContravention(locationOfContravention);
        target.setTimeExtensionDateOfContravention(dateOfContravention);
        target.setTimeExtensionFullName(fullName);
        target.setTimeExtensionAddress(address);
        target.setTimeExtensionPostcode(postcode);
        target.setTimeExtensionReasonsGiven(reasonsGiven);
        target.setTimeExtensionSignedAndDated(signedAndDated);
        target.setTimeExtensionDateSigned(dateSigned);
    }
}
