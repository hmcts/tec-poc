package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.FieldType;

/**
 * Case details projection of the current PE3. Field ids are prefixed {@code pe3}.
 * CCD capitalises the first letter after that prefix, so each property is named
 * explicitly ({@code pe3Form}).
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Pe3CaseDetails {

    @CCD(label = "Form validation result", searchable = false)
    @JsonProperty("FormValidationResultDisplay")
    private String formValidationResultDisplay;

    @CCD(label = "Date received", searchable = false)
    @JsonProperty("DateReceived")
    private LocalDate dateReceived;

    @CCD(
        label = "Type",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "ApplicationTimeliness",
        searchable = false
    )
    @JsonProperty("Type")
    private ApplicationTimeliness type;

    @CCD(
        label = "TE7 submitted?",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    @JsonProperty("Te7Submitted")
    private YesNo te7Submitted;

    @CCD(
        label = "Form",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "ApplicationForm",
        searchable = false
    )
    @JsonProperty("Form")
    private ApplicationForm form;

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

    @CCD(label = "Title", searchable = false)
    @JsonProperty("Title")
    private String title;

    @CCD(label = "Full name", searchable = false)
    @JsonProperty("FullName")
    private String fullName;

    @CCD(label = "Company name", searchable = false)
    @JsonProperty("CompanyName")
    private String companyName;

    @CCD(label = "Address", searchable = false)
    @JsonProperty("Address")
    private String address;

    @CCD(label = "Postcode", searchable = false)
    @JsonProperty("Postcode")
    private String postcode;

    @CCD(
        label = "Declaration",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "ApplicationDeclaration",
        searchable = false
    )
    @JsonProperty("Declaration")
    private ApplicationDeclaration declaration;

    @CCD(
        label = "Reasons given",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    @JsonProperty("ReasonsGiven")
    private YesNo reasonsGiven;

    @CCD(label = "Date it was paid", searchable = false)
    @JsonProperty("DatePaid")
    private LocalDate datePaid;

    @CCD(label = "How it was paid", searchable = false)
    @JsonProperty("HowPaid")
    private String howPaid;

    @CCD(label = "To whom it was paid", searchable = false)
    @JsonProperty("PaidTo")
    private String paidTo;

    public void copyToApplication(TecCase target) {
        target.setApplicationDateReceived(dateReceived);
        target.setApplicationType(type);
        target.setApplicationTe7Submitted(te7Submitted);
        target.setApplicationForm(ApplicationForm.PE3);
        target.setApplicationPenaltyChargeNumber(penaltyChargeNumber);
        target.setApplicationVehicleRegistration(vehicleRegistration);
        target.setApplicationApplicant(applicant);
        target.setApplicationLocationOfContravention(locationOfContravention);
        target.setApplicationDateOfContravention(dateOfContravention);
        target.setApplicationTitle(title);
        target.setApplicationFullName(fullName);
        target.setApplicationCompanyName(companyName);
        target.setApplicationAddress(address);
        target.setApplicationPostcode(postcode);
        target.setApplicationDeclaration(declaration);
        target.setApplicationReasonsGiven(reasonsGiven);
        target.setApplicationDatePaid(datePaid);
        target.setApplicationHowPaid(howPaid);
        target.setApplicationPaidTo(paidTo);
    }
}
