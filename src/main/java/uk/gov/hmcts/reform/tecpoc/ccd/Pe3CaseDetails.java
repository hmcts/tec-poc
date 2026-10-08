package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.FieldType;

/**
 * Case details projection of the current PE3. Field ids are prefixed {@code pe3}.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Pe3CaseDetails {

    @CCD(label = "Form validation result", searchable = false)
    private String formValidationResultDisplay;

    @CCD(label = "Date received", searchable = false)
    private LocalDate dateReceived;

    @CCD(
        label = "Type",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "ApplicationTimeliness",
        searchable = false
    )
    private ApplicationTimeliness type;

    @CCD(
        label = "TE7 submitted?",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    private YesNo te7Submitted;

    @CCD(
        label = "Form",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "ApplicationForm",
        searchable = false
    )
    private ApplicationForm form;

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

    @CCD(label = "Title", searchable = false)
    private String title;

    @CCD(label = "Full name", searchable = false)
    private String fullName;

    @CCD(label = "Company name", searchable = false)
    private String companyName;

    @CCD(label = "Address", searchable = false)
    private String address;

    @CCD(label = "Postcode", searchable = false)
    private String postcode;

    @CCD(
        label = "Declaration",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "ApplicationDeclaration",
        searchable = false
    )
    private ApplicationDeclaration declaration;

    @CCD(
        label = "Reasons given",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    private YesNo reasonsGiven;

    @CCD(label = "Date it was paid", searchable = false)
    private LocalDate datePaid;

    @CCD(label = "How it was paid", searchable = false)
    private String howPaid;

    @CCD(label = "To whom it was paid", searchable = false)
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
