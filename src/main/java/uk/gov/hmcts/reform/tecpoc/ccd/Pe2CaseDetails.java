package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.FieldType;

/**
 * Case details projection of the current PE2. Field ids are prefixed {@code pe2}.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Pe2CaseDetails {

    @CCD(label = "Form validation result", searchable = false)
    private String formValidationResultDisplay;

    @CCD(
        label = "Form",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionForm",
        searchable = false
    )
    private TimeExtensionForm form;

    @CCD(label = "Penalty Charge Number", searchable = false)
    private String penaltyChargeNumber;

    @CCD(label = "Vehicle reg", searchable = false)
    private String vehicleRegistration;

    @CCD(label = "Applicant", searchable = false)
    private String applicant;

    @CCD(label = "Location of contravention", searchable = false)
    private String locationOfContravention;

    @CCD(label = "Date of contravention", searchable = false)
    private LocalDate dateOfContravention;

    @CCD(label = "Full name", searchable = false)
    private String fullName;

    @CCD(label = "Address", searchable = false)
    private String address;

    @CCD(label = "Postcode", searchable = false)
    private String postcode;

    @CCD(
        label = "Reasons given",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    private YesNo reasonsGiven;

    @CCD(
        label = "Signed and dated",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    private YesNo signedAndDated;

    @CCD(label = "Date signed", searchable = false)
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
