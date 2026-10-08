package uk.gov.hmcts.reform.tecpoc.ccd;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;

@Repository
@RequiredArgsConstructor
public class TecCaseRepository {

    private final NamedParameterJdbcTemplate database;

    public void create(long caseReference, TecCase tecCase) {
        database.update("""
            insert into tec_case (
                case_reference, file_identifier, batch_identifier, penalty_charge_number,
                local_authority,
                respondent_details_1, respondent_details_2, respondent_details_3,
                respondent_details_4, respondent_details_5, respondent_details_6,
                vehicle_registration_number, nature_of_offence,
                date_charge_certificate_served, amount_due
            ) values (
                :caseReference, :fileIdentifier, :batchIdentifier, :penaltyChargeNumber,
                :localAuthority,
                :respondentDetails1, :respondentDetails2, :respondentDetails3,
                :respondentDetails4, :respondentDetails5, :respondentDetails6,
                :vehicleRegistrationNumber, :natureOfOffence,
                :dateChargeCertificateServed, :amountDue
            )
            """, parameters(caseReference, tecCase));
    }

    public TecCase find(long caseReference) {
        return database.queryForObject("""
            select file_identifier, batch_identifier, batch_case_reference,
                   penalty_charge_number,
                   local_authority,
                   respondent_details_1, respondent_details_2, respondent_details_3,
                   respondent_details_4, respondent_details_5, respondent_details_6,
                   vehicle_registration_number, nature_of_offence,
                   date_charge_certificate_served, amount_due, payment_status,
                   payment_reference, closure_reason, registration_document, registration_date,
                   form_validation_result,
                   application_date_received, application_type, application_te7_submitted,
                   application_form, application_penalty_charge_number,
                   application_vehicle_registration, application_applicant,
                   application_location_of_contravention, application_date_of_contravention,
                   application_title, application_full_name, application_company_name,
                   application_address, application_postcode, application_declaration,
                   application_reasons_given, application_date_paid, application_how_paid,
                   application_paid_to,
                   time_extension_form, time_extension_penalty_charge_number,
                   time_extension_vehicle_registration, time_extension_applicant,
                   time_extension_location_of_contravention, time_extension_date_of_contravention,
                   time_extension_title, time_extension_other_title, time_extension_full_name,
                   time_extension_company_name, time_extension_address, time_extension_postcode,
                   time_extension_permission_type, time_extension_reasons_given,
                   time_extension_signed_and_dated,
                   time_extension_signed_by, time_extension_date_signed,
                   time_extension_print_full_name
              from tec_case
             where case_reference = :caseReference
            """, Map.of("caseReference", caseReference), (resultSet, rowNumber) -> {
                TecCase result = new TecCase();
                result.setFileIdentifier(resultSet.getString("file_identifier"));
                result.setBatchIdentifier(resultSet.getString("batch_identifier"));
                long batchCaseReference = resultSet.getLong("batch_case_reference");
                if (!resultSet.wasNull()) {
                    result.setBatchCase(CaseLink.builder()
                        .caseReference(Long.toString(batchCaseReference))
                        .caseType(BatchCaseConfiguration.CASE_TYPE)
                        .build());
                }
                result.setPenaltyChargeNumber(resultSet.getString("penalty_charge_number"));
                result.setLocalAuthority(LocalAuthority.valueOf(resultSet.getString("local_authority")));
                result.setRespondentDetails1(resultSet.getString("respondent_details_1"));
                result.setRespondentDetails2(resultSet.getString("respondent_details_2"));
                result.setRespondentDetails3(resultSet.getString("respondent_details_3"));
                result.setRespondentDetails4(resultSet.getString("respondent_details_4"));
                result.setRespondentDetails5(resultSet.getString("respondent_details_5"));
                result.setRespondentDetails6(resultSet.getString("respondent_details_6"));
                result.setVehicleRegistrationNumber(resultSet.getString("vehicle_registration_number"));
                result.setNatureOfOffence(resultSet.getString("nature_of_offence"));
                result.setDateChargeCertificateServed(resultSet.getString("date_charge_certificate_served"));
                result.setAmountDue(resultSet.getInt("amount_due"));
                result.setPaymentStatus(resultSet.getString("payment_status"));
                result.setPaymentReference(resultSet.getString("payment_reference"));
                result.setClosureReason(resultSet.getString("closure_reason"));
                result.setRegistrationDocument(resultSet.getString("registration_document"));
                Date registrationDate = resultSet.getDate("registration_date");
                if (registrationDate != null) {
                    result.setRegistrationDate(registrationDate.toLocalDate());
                }
                String formValidationResult = resultSet.getString("form_validation_result");
                if (formValidationResult != null) {
                    result.setFormValidationResult(FormValidationResult.valueOf(formValidationResult));
                }
                mapApplicationFields(resultSet, result);
                mapTimeExtensionFields(resultSet, result);
                return result;
            });
    }

    public void linkBatchCase(long caseReference, long batchCaseReference) {
        database.update("""
            update tec_case
               set batch_case_reference = :batchCaseReference
             where case_reference = :caseReference
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("batchCaseReference", batchCaseReference));
    }

    /**
     * Case reference for a penalty charge number.
     * Returns null when there is not exactly one matching case.
     */
    public Long findCaseReferenceByPenaltyChargeNumber(String penaltyChargeNumber) {
        List<Long> references = database.query("""
            select case_reference
              from tec_case
             where penalty_charge_number = :penaltyChargeNumber
             order by case_reference
            """, Map.of("penaltyChargeNumber", penaltyChargeNumber),
            (resultSet, rowNumber) -> resultSet.getLong("case_reference"));
        if (references.size() != 1) {
            return null;
        }
        return references.get(0);
    }

    public boolean exists(long caseReference) {
        Integer count = database.queryForObject(
            """
            select count(*)
              from tec_case
             where case_reference = :caseReference
            """,
            Map.of("caseReference", caseReference),
            Integer.class
        );
        return count != null && count > 0;
    }

    public Long findBatchCaseReference(long caseReference) {
        try {
            return database.queryForObject("""
                select batch_case_reference
                  from tec_case
                 where case_reference = :caseReference
                """, Map.of("caseReference", caseReference), Long.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void recordApplication(long caseReference, TecCase tecCase) {
        database.update("""
            update tec_case
               set application_date_received = :applicationDateReceived,
                   application_type = :applicationType,
                   application_te7_submitted = :applicationTe7Submitted,
                   application_form = :applicationForm,
                   application_penalty_charge_number = :applicationPenaltyChargeNumber,
                   application_vehicle_registration = :applicationVehicleRegistration,
                   application_applicant = :applicationApplicant,
                   application_location_of_contravention = :applicationLocationOfContravention,
                   application_date_of_contravention = :applicationDateOfContravention,
                   application_title = :applicationTitle,
                   application_full_name = :applicationFullName,
                   application_company_name = :applicationCompanyName,
                   application_address = :applicationAddress,
                   application_postcode = :applicationPostcode,
                   application_declaration = :applicationDeclaration,
                   application_reasons_given = :applicationReasonsGiven,
                   application_date_paid = :applicationDatePaid,
                   application_how_paid = :applicationHowPaid,
                   application_paid_to = :applicationPaidTo
             where case_reference = :caseReference
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("applicationDateReceived", tecCase.getApplicationDateReceived())
            .addValue(
                "applicationType",
                tecCase.getApplicationType() == null ? null : tecCase.getApplicationType().name()
            )
            .addValue(
                "applicationTe7Submitted",
                tecCase.getApplicationTe7Submitted() == null
                    ? null
                    : tecCase.getApplicationTe7Submitted().name()
            )
            .addValue(
                "applicationForm",
                tecCase.getApplicationForm() == null ? null : tecCase.getApplicationForm().name()
            )
            .addValue("applicationPenaltyChargeNumber", tecCase.getApplicationPenaltyChargeNumber())
            .addValue("applicationVehicleRegistration", tecCase.getApplicationVehicleRegistration())
            .addValue("applicationApplicant", tecCase.getApplicationApplicant())
            .addValue(
                "applicationLocationOfContravention",
                tecCase.getApplicationLocationOfContravention()
            )
            .addValue("applicationDateOfContravention", tecCase.getApplicationDateOfContravention())
            .addValue("applicationTitle", tecCase.getApplicationTitle())
            .addValue("applicationFullName", tecCase.getApplicationFullName())
            .addValue("applicationCompanyName", tecCase.getApplicationCompanyName())
            .addValue("applicationAddress", tecCase.getApplicationAddress())
            .addValue("applicationPostcode", tecCase.getApplicationPostcode())
            .addValue(
                "applicationDeclaration",
                tecCase.getApplicationDeclaration() == null
                    ? null
                    : tecCase.getApplicationDeclaration().name()
            )
            .addValue(
                "applicationReasonsGiven",
                tecCase.getApplicationReasonsGiven() == null
                    ? null
                    : tecCase.getApplicationReasonsGiven().name()
            )
            .addValue("applicationDatePaid", tecCase.getApplicationDatePaid())
            .addValue("applicationHowPaid", tecCase.getApplicationHowPaid())
            .addValue("applicationPaidTo", tecCase.getApplicationPaidTo()));
    }

    /**
     * Clerk edit of TE9 fields. Does not change form type or penalty charge number.
     */
    public void editTe9Application(long caseReference, TecCase tecCase) {
        database.update("""
            update tec_case
               set application_date_received = :applicationDateReceived,
                   application_type = :applicationType,
                   application_te7_submitted = :applicationTe7Submitted,
                   application_vehicle_registration = :applicationVehicleRegistration,
                   application_applicant = :applicationApplicant,
                   application_location_of_contravention = :applicationLocationOfContravention,
                   application_date_of_contravention = :applicationDateOfContravention,
                   application_title = :applicationTitle,
                   application_full_name = :applicationFullName,
                   application_company_name = :applicationCompanyName,
                   application_address = :applicationAddress,
                   application_postcode = :applicationPostcode,
                   application_declaration = :applicationDeclaration,
                   application_date_paid = :applicationDatePaid,
                   application_how_paid = :applicationHowPaid,
                   application_paid_to = :applicationPaidTo
             where case_reference = :caseReference
            """, applicationEditParams(caseReference, tecCase));
    }

    /**
     * Clerk edit of PE3 fields. Does not change form type or penalty charge number.
     */
    public void editPe3Application(long caseReference, TecCase tecCase) {
        database.update("""
            update tec_case
               set application_date_received = :applicationDateReceived,
                   application_type = :applicationType,
                   application_te7_submitted = :applicationTe7Submitted,
                   application_vehicle_registration = :applicationVehicleRegistration,
                   application_applicant = :applicationApplicant,
                   application_location_of_contravention = :applicationLocationOfContravention,
                   application_date_of_contravention = :applicationDateOfContravention,
                   application_title = :applicationTitle,
                   application_full_name = :applicationFullName,
                   application_company_name = :applicationCompanyName,
                   application_address = :applicationAddress,
                   application_postcode = :applicationPostcode,
                   application_declaration = :applicationDeclaration,
                   application_reasons_given = :applicationReasonsGiven,
                   application_date_paid = :applicationDatePaid,
                   application_how_paid = :applicationHowPaid,
                   application_paid_to = :applicationPaidTo
             where case_reference = :caseReference
            """, applicationEditParams(caseReference, tecCase)
            .addValue(
                "applicationReasonsGiven",
                tecCase.getApplicationReasonsGiven() == null
                    ? null
                    : tecCase.getApplicationReasonsGiven().name()
            ));
    }

    private static MapSqlParameterSource applicationEditParams(long caseReference, TecCase tecCase) {
        return new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("applicationDateReceived", tecCase.getApplicationDateReceived())
            .addValue(
                "applicationType",
                tecCase.getApplicationType() == null ? null : tecCase.getApplicationType().name()
            )
            .addValue(
                "applicationTe7Submitted",
                tecCase.getApplicationTe7Submitted() == null
                    ? null
                    : tecCase.getApplicationTe7Submitted().name()
            )
            .addValue("applicationVehicleRegistration", tecCase.getApplicationVehicleRegistration())
            .addValue("applicationApplicant", tecCase.getApplicationApplicant())
            .addValue(
                "applicationLocationOfContravention",
                tecCase.getApplicationLocationOfContravention()
            )
            .addValue("applicationDateOfContravention", tecCase.getApplicationDateOfContravention())
            .addValue("applicationTitle", tecCase.getApplicationTitle())
            .addValue("applicationFullName", tecCase.getApplicationFullName())
            .addValue("applicationCompanyName", tecCase.getApplicationCompanyName())
            .addValue("applicationAddress", tecCase.getApplicationAddress())
            .addValue("applicationPostcode", tecCase.getApplicationPostcode())
            .addValue(
                "applicationDeclaration",
                tecCase.getApplicationDeclaration() == null
                    ? null
                    : tecCase.getApplicationDeclaration().name()
            )
            .addValue("applicationDatePaid", tecCase.getApplicationDatePaid())
            .addValue("applicationHowPaid", tecCase.getApplicationHowPaid())
            .addValue("applicationPaidTo", tecCase.getApplicationPaidTo());
    }

    public void recordTimeExtension(long caseReference, TecCase tecCase) {
        database.update("""
            update tec_case
               set form_validation_result = coalesce(:formValidationResult, form_validation_result),
                   time_extension_form = :timeExtensionForm,
                   time_extension_penalty_charge_number = :timeExtensionPenaltyChargeNumber,
                   time_extension_vehicle_registration = :timeExtensionVehicleRegistration,
                   time_extension_applicant = :timeExtensionApplicant,
                   time_extension_location_of_contravention = :timeExtensionLocationOfContravention,
                   time_extension_date_of_contravention = :timeExtensionDateOfContravention,
                   time_extension_title = :timeExtensionTitle,
                   time_extension_other_title = :timeExtensionOtherTitle,
                   time_extension_full_name = :timeExtensionFullName,
                   time_extension_company_name = :timeExtensionCompanyName,
                   time_extension_address = :timeExtensionAddress,
                   time_extension_postcode = :timeExtensionPostcode,
                   time_extension_permission_type = :timeExtensionPermissionType,
                   time_extension_reasons_given = :timeExtensionReasonsGiven,
                   time_extension_signed_and_dated = :timeExtensionSignedAndDated,
                   time_extension_signed_by = :timeExtensionSignedBy,
                   time_extension_date_signed = :timeExtensionDateSigned,
                   time_extension_print_full_name = :timeExtensionPrintFullName
             where case_reference = :caseReference
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue(
                "formValidationResult",
                tecCase.getFormValidationResult() == null
                    ? null
                    : tecCase.getFormValidationResult().name()
            )
            .addValue(
                "timeExtensionForm",
                tecCase.getTimeExtensionForm() == null ? null : tecCase.getTimeExtensionForm().name()
            )
            .addValue("timeExtensionPenaltyChargeNumber", tecCase.getTimeExtensionPenaltyChargeNumber())
            .addValue("timeExtensionVehicleRegistration", tecCase.getTimeExtensionVehicleRegistration())
            .addValue("timeExtensionApplicant", tecCase.getTimeExtensionApplicant())
            .addValue(
                "timeExtensionLocationOfContravention",
                tecCase.getTimeExtensionLocationOfContravention()
            )
            .addValue("timeExtensionDateOfContravention", tecCase.getTimeExtensionDateOfContravention())
            .addValue("timeExtensionTitle", tecCase.getTimeExtensionTitle())
            .addValue("timeExtensionOtherTitle", tecCase.getTimeExtensionOtherTitle())
            .addValue("timeExtensionFullName", tecCase.getTimeExtensionFullName())
            .addValue("timeExtensionCompanyName", tecCase.getTimeExtensionCompanyName())
            .addValue("timeExtensionAddress", tecCase.getTimeExtensionAddress())
            .addValue("timeExtensionPostcode", tecCase.getTimeExtensionPostcode())
            .addValue(
                "timeExtensionPermissionType",
                tecCase.getTimeExtensionPermissionType() == null
                    ? null
                    : tecCase.getTimeExtensionPermissionType().name()
            )
            .addValue(
                "timeExtensionReasonsGiven",
                tecCase.getTimeExtensionReasonsGiven() == null
                    ? null
                    : tecCase.getTimeExtensionReasonsGiven().name()
            )
            .addValue(
                "timeExtensionSignedAndDated",
                tecCase.getTimeExtensionSignedAndDated() == null
                    ? null
                    : tecCase.getTimeExtensionSignedAndDated().name()
            )
            .addValue(
                "timeExtensionSignedBy",
                tecCase.getTimeExtensionSignedBy() == null
                    ? null
                    : tecCase.getTimeExtensionSignedBy().name()
            )
            .addValue("timeExtensionDateSigned", tecCase.getTimeExtensionDateSigned())
            .addValue("timeExtensionPrintFullName", tecCase.getTimeExtensionPrintFullName()));
    }

    /**
     * Clerk edit of TE7 fields. Does not change form type or penalty charge number.
     */
    public void editTe7Application(long caseReference, TecCase tecCase) {
        database.update("""
            update tec_case
               set time_extension_vehicle_registration = :timeExtensionVehicleRegistration,
                   time_extension_title = :timeExtensionTitle,
                   time_extension_other_title = :timeExtensionOtherTitle,
                   time_extension_full_name = :timeExtensionFullName,
                   time_extension_company_name = :timeExtensionCompanyName,
                   time_extension_address = :timeExtensionAddress,
                   time_extension_postcode = :timeExtensionPostcode,
                   time_extension_permission_type = :timeExtensionPermissionType,
                   time_extension_reasons_given = :timeExtensionReasonsGiven,
                   time_extension_signed_and_dated = :timeExtensionSignedAndDated,
                   time_extension_signed_by = :timeExtensionSignedBy,
                   time_extension_date_signed = :timeExtensionDateSigned,
                   time_extension_print_full_name = :timeExtensionPrintFullName
             where case_reference = :caseReference
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("timeExtensionVehicleRegistration", tecCase.getTimeExtensionVehicleRegistration())
            .addValue("timeExtensionTitle", tecCase.getTimeExtensionTitle())
            .addValue("timeExtensionOtherTitle", tecCase.getTimeExtensionOtherTitle())
            .addValue("timeExtensionFullName", tecCase.getTimeExtensionFullName())
            .addValue("timeExtensionCompanyName", tecCase.getTimeExtensionCompanyName())
            .addValue("timeExtensionAddress", tecCase.getTimeExtensionAddress())
            .addValue("timeExtensionPostcode", tecCase.getTimeExtensionPostcode())
            .addValue(
                "timeExtensionPermissionType",
                tecCase.getTimeExtensionPermissionType() == null
                    ? null
                    : tecCase.getTimeExtensionPermissionType().name()
            )
            .addValue(
                "timeExtensionReasonsGiven",
                tecCase.getTimeExtensionReasonsGiven() == null
                    ? null
                    : tecCase.getTimeExtensionReasonsGiven().name()
            )
            .addValue(
                "timeExtensionSignedAndDated",
                tecCase.getTimeExtensionSignedAndDated() == null
                    ? null
                    : tecCase.getTimeExtensionSignedAndDated().name()
            )
            .addValue(
                "timeExtensionSignedBy",
                tecCase.getTimeExtensionSignedBy() == null
                    ? null
                    : tecCase.getTimeExtensionSignedBy().name()
            )
            .addValue("timeExtensionDateSigned", tecCase.getTimeExtensionDateSigned())
            .addValue("timeExtensionPrintFullName", tecCase.getTimeExtensionPrintFullName()));
    }

    /**
     * Clerk edit of PE2 fields. Does not change form type or penalty charge number.
     */
    public void editPe2Application(long caseReference, TecCase tecCase) {
        database.update("""
            update tec_case
               set time_extension_vehicle_registration = :timeExtensionVehicleRegistration,
                   time_extension_applicant = :timeExtensionApplicant,
                   time_extension_location_of_contravention = :timeExtensionLocationOfContravention,
                   time_extension_date_of_contravention = :timeExtensionDateOfContravention,
                   time_extension_full_name = :timeExtensionFullName,
                   time_extension_address = :timeExtensionAddress,
                   time_extension_postcode = :timeExtensionPostcode,
                   time_extension_reasons_given = :timeExtensionReasonsGiven,
                   time_extension_signed_and_dated = :timeExtensionSignedAndDated,
                   time_extension_date_signed = :timeExtensionDateSigned
             where case_reference = :caseReference
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("timeExtensionVehicleRegistration", tecCase.getTimeExtensionVehicleRegistration())
            .addValue("timeExtensionApplicant", tecCase.getTimeExtensionApplicant())
            .addValue(
                "timeExtensionLocationOfContravention",
                tecCase.getTimeExtensionLocationOfContravention()
            )
            .addValue("timeExtensionDateOfContravention", tecCase.getTimeExtensionDateOfContravention())
            .addValue("timeExtensionFullName", tecCase.getTimeExtensionFullName())
            .addValue("timeExtensionAddress", tecCase.getTimeExtensionAddress())
            .addValue("timeExtensionPostcode", tecCase.getTimeExtensionPostcode())
            .addValue(
                "timeExtensionReasonsGiven",
                tecCase.getTimeExtensionReasonsGiven() == null
                    ? null
                    : tecCase.getTimeExtensionReasonsGiven().name()
            )
            .addValue(
                "timeExtensionSignedAndDated",
                tecCase.getTimeExtensionSignedAndDated() == null
                    ? null
                    : tecCase.getTimeExtensionSignedAndDated().name()
            )
            .addValue("timeExtensionDateSigned", tecCase.getTimeExtensionDateSigned()));
    }

    public void recordPayment(long caseReference, String status, String reference, String closureReason) {
        database.update("""
            update tec_case
               set payment_status = :status,
                   payment_reference = :reference,
                   closure_reason = :closureReason
             where case_reference = :caseReference
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("status", status)
            .addValue("reference", reference)
            .addValue("closureReason", closureReason));
    }

    public void recordRegistration(long caseReference, String document, LocalDate registrationDate) {
        database.update("""
            update tec_case
               set registration_document = :document,
                   registration_date = :registrationDate
             where case_reference = :caseReference
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("document", document)
            .addValue("registrationDate", registrationDate));
    }

    public void recordFormValidation(long caseReference, FormValidationResult result) {
        database.update("""
            update tec_case
               set form_validation_result = :result
             where case_reference = :caseReference
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("result", result.name()));
    }

    public UUID insertDocument(
        long caseReference,
        String categoryId,
        String documentUrl,
        String documentBinaryUrl,
        String filename
    ) {
        UUID id = UUID.randomUUID();
        database.update("""
            insert into tec_case_document (
                id, case_reference, category_id, document_url, document_binary_url, filename
            ) values (
                :id, :caseReference, :categoryId, :documentUrl, :documentBinaryUrl, :filename
            )
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("caseReference", caseReference)
            .addValue("categoryId", categoryId)
            .addValue("documentUrl", documentUrl)
            .addValue("documentBinaryUrl", documentBinaryUrl)
            .addValue("filename", filename));
        return id;
    }

    public List<TecCaseDocument> findDocuments(long caseReference) {
        return database.query("""
            select id, category_id, document_url, document_binary_url, filename, created_at
              from tec_case_document
             where case_reference = :caseReference
             order by created_at asc, id asc
            """, Map.of("caseReference", caseReference), (resultSet, rowNumber) -> {
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                return new TecCaseDocument(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("category_id"),
                    resultSet.getString("document_url"),
                    resultSet.getString("document_binary_url"),
                    resultSet.getString("filename"),
                    createdAt == null ? null : createdAt.toInstant()
                );
            });
    }

    public UUID insertWarrantAuthorisation(long caseReference, WarrantAuthorisation authorisation) {
        UUID id = UUID.randomUUID();
        database.update("""
            insert into tec_case_warrant_authorisation (
                id, case_reference, date_of_issue, date_of_expiry, status
            ) values (
                :id, :caseReference, :dateOfIssue, :dateOfExpiry, :status
            )
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("caseReference", caseReference)
            .addValue("dateOfIssue", authorisation.getDateOfIssue())
            .addValue("dateOfExpiry", authorisation.getDateOfExpiry())
            .addValue("status", authorisation.getStatus().name()));
        return id;
    }

    public UUID insertGeneralApplication(long caseReference, GeneralApplicationEntry entry) {
        UUID id = UUID.randomUUID();
        database.update("""
            insert into tec_case_general_application (
                id, case_reference, rank, applicant, date_received, application_type,
                something_else_details, within_14_days, fee_amount_received, applied_for_hwf,
                hwf_reference, all_parties_agree, without_notice, state
            ) values (
                :id, :caseReference,
                (
                    select coalesce(max(rank), 0) + 1
                      from tec_case_general_application
                     where case_reference = :caseReference
                ),
                :applicant, :dateReceived, :applicationType,
                :somethingElseDetails, :within14Days, :feeAmountReceived, :appliedForHwf,
                :hwfReference, :allPartiesAgree, :withoutNotice, :state
            )
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("caseReference", caseReference)
            .addValue("applicant", entry.getApplicant().name())
            .addValue("dateReceived", Date.valueOf(entry.getDateReceived()))
            .addValue("applicationType", entry.getApplicationType().name())
            .addValue("somethingElseDetails", entry.getSomethingElseDetails())
            .addValue("within14Days", yesNoName(entry.getWithin14Days()))
            .addValue("feeAmountReceived", entry.getFeeAmountReceived())
            .addValue("appliedForHwf", entry.getAppliedForHwf().name())
            .addValue("hwfReference", entry.getHwfReference())
            .addValue("allPartiesAgree", entry.getAllPartiesAgree().name())
            .addValue("withoutNotice", yesNoName(entry.getWithoutNotice()))
            .addValue("state", GeneralApplicationState.GEN_APP_ISSUED.name()));
        return id;
    }

    public List<TecCaseGeneralApplication> findGeneralApplications(long caseReference) {
        return database.query("""
            select id, rank, applicant, date_received, application_type, something_else_details,
                   within_14_days, fee_amount_received, applied_for_hwf, hwf_reference,
                   all_parties_agree, without_notice, state
              from tec_case_general_application
             where case_reference = :caseReference
             order by rank asc, created_at asc, id asc
            """, Map.of("caseReference", caseReference), (resultSet, rowNumber) -> {
                Date dateReceived = resultSet.getDate("date_received");
                return new TecCaseGeneralApplication(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getInt("rank"),
                    GeneralApplicationApplicant.valueOf(resultSet.getString("applicant")),
                    dateReceived == null ? null : dateReceived.toLocalDate(),
                    GeneralApplicationType.valueOf(resultSet.getString("application_type")),
                    resultSet.getString("something_else_details"),
                    yesNo(resultSet.getString("within_14_days")),
                    resultSet.getInt("fee_amount_received"),
                    YesNo.valueOf(resultSet.getString("applied_for_hwf")),
                    resultSet.getString("hwf_reference"),
                    YesNo.valueOf(resultSet.getString("all_parties_agree")),
                    yesNo(resultSet.getString("without_notice")),
                    GeneralApplicationState.valueOf(resultSet.getString("state"))
                );
            });
    }

    public List<TecCaseWarrantAuthorisation> findWarrantAuthorisations(long caseReference) {
        return database.query("""
            select id, date_of_issue, date_of_expiry, status
              from tec_case_warrant_authorisation
             where case_reference = :caseReference
             order by date_of_issue asc, created_at asc, id asc
            """, Map.of("caseReference", caseReference), (resultSet, rowNumber) -> {
                Date dateOfIssue = resultSet.getDate("date_of_issue");
                Date dateOfExpiry = resultSet.getDate("date_of_expiry");
                return new TecCaseWarrantAuthorisation(
                    resultSet.getObject("id", UUID.class),
                    dateOfIssue == null ? null : dateOfIssue.toLocalDate(),
                    dateOfExpiry == null ? null : dateOfExpiry.toLocalDate(),
                    WarrantAuthorisationStatus.valueOf(resultSet.getString("status"))
                );
            });
    }

    private static String yesNoName(YesNo value) {
        return value == null ? null : value.name();
    }

    private static YesNo yesNo(String value) {
        return value == null ? null : YesNo.valueOf(value);
    }

    private MapSqlParameterSource parameters(long caseReference, TecCase tecCase) {
        return new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("fileIdentifier", tecCase.getFileIdentifier())
            .addValue("batchIdentifier", tecCase.getBatchIdentifier())
            .addValue("penaltyChargeNumber", tecCase.getPenaltyChargeNumber())
            .addValue(
                "localAuthority",
                tecCase.getLocalAuthority() == null ? null : tecCase.getLocalAuthority().name()
            )
            .addValue("respondentDetails1", tecCase.getRespondentDetails1())
            .addValue("respondentDetails2", tecCase.getRespondentDetails2())
            .addValue("respondentDetails3", tecCase.getRespondentDetails3())
            .addValue("respondentDetails4", tecCase.getRespondentDetails4())
            .addValue("respondentDetails5", tecCase.getRespondentDetails5())
            .addValue("respondentDetails6", tecCase.getRespondentDetails6())
            .addValue("vehicleRegistrationNumber", tecCase.getVehicleRegistrationNumber())
            .addValue("natureOfOffence", tecCase.getNatureOfOffence())
            .addValue("dateChargeCertificateServed", tecCase.getDateChargeCertificateServed())
            .addValue("amountDue", tecCase.getAmountDue());
    }

    private static void mapApplicationFields(ResultSet resultSet, TecCase result) throws SQLException {
        Date applicationDateReceived = resultSet.getDate("application_date_received");
        if (applicationDateReceived != null) {
            result.setApplicationDateReceived(applicationDateReceived.toLocalDate());
        }
        String applicationType = resultSet.getString("application_type");
        if (applicationType != null) {
            result.setApplicationType(ApplicationTimeliness.valueOf(applicationType));
        }
        String applicationTe7Submitted = resultSet.getString("application_te7_submitted");
        if (applicationTe7Submitted != null) {
            result.setApplicationTe7Submitted(YesNo.valueOf(applicationTe7Submitted));
        }
        String applicationForm = resultSet.getString("application_form");
        if (applicationForm != null) {
            result.setApplicationForm(ApplicationForm.valueOf(applicationForm));
        }
        result.setApplicationPenaltyChargeNumber(
            resultSet.getString("application_penalty_charge_number")
        );
        result.setApplicationVehicleRegistration(
            resultSet.getString("application_vehicle_registration")
        );
        result.setApplicationApplicant(resultSet.getString("application_applicant"));
        result.setApplicationLocationOfContravention(
            resultSet.getString("application_location_of_contravention")
        );
        Date applicationDateOfContravention = resultSet.getDate("application_date_of_contravention");
        if (applicationDateOfContravention != null) {
            result.setApplicationDateOfContravention(applicationDateOfContravention.toLocalDate());
        }
        result.setApplicationTitle(resultSet.getString("application_title"));
        result.setApplicationFullName(resultSet.getString("application_full_name"));
        result.setApplicationCompanyName(resultSet.getString("application_company_name"));
        result.setApplicationAddress(resultSet.getString("application_address"));
        result.setApplicationPostcode(resultSet.getString("application_postcode"));
        String applicationDeclaration = resultSet.getString("application_declaration");
        if (applicationDeclaration != null) {
            result.setApplicationDeclaration(ApplicationDeclaration.valueOf(applicationDeclaration));
        }
        String applicationReasonsGiven = resultSet.getString("application_reasons_given");
        if (applicationReasonsGiven != null) {
            result.setApplicationReasonsGiven(YesNo.valueOf(applicationReasonsGiven));
        }
        Date applicationDatePaid = resultSet.getDate("application_date_paid");
        if (applicationDatePaid != null) {
            result.setApplicationDatePaid(applicationDatePaid.toLocalDate());
        }
        result.setApplicationHowPaid(resultSet.getString("application_how_paid"));
        result.setApplicationPaidTo(resultSet.getString("application_paid_to"));
    }

    private static void mapTimeExtensionFields(ResultSet resultSet, TecCase result) throws SQLException {
        String timeExtensionForm = resultSet.getString("time_extension_form");
        if (timeExtensionForm != null) {
            result.setTimeExtensionForm(TimeExtensionForm.valueOf(timeExtensionForm));
        }
        result.setTimeExtensionPenaltyChargeNumber(
            resultSet.getString("time_extension_penalty_charge_number")
        );
        result.setTimeExtensionVehicleRegistration(
            resultSet.getString("time_extension_vehicle_registration")
        );
        result.setTimeExtensionApplicant(resultSet.getString("time_extension_applicant"));
        result.setTimeExtensionLocationOfContravention(
            resultSet.getString("time_extension_location_of_contravention")
        );
        Date timeExtensionDateOfContravention = resultSet.getDate("time_extension_date_of_contravention");
        if (timeExtensionDateOfContravention != null) {
            result.setTimeExtensionDateOfContravention(timeExtensionDateOfContravention.toLocalDate());
        }
        result.setTimeExtensionTitle(resultSet.getString("time_extension_title"));
        result.setTimeExtensionOtherTitle(resultSet.getString("time_extension_other_title"));
        result.setTimeExtensionFullName(resultSet.getString("time_extension_full_name"));
        result.setTimeExtensionCompanyName(resultSet.getString("time_extension_company_name"));
        result.setTimeExtensionAddress(resultSet.getString("time_extension_address"));
        result.setTimeExtensionPostcode(resultSet.getString("time_extension_postcode"));
        String permissionType = resultSet.getString("time_extension_permission_type");
        if (permissionType != null) {
            result.setTimeExtensionPermissionType(TimeExtensionPermissionType.valueOf(permissionType));
        }
        String reasonsGiven = resultSet.getString("time_extension_reasons_given");
        if (reasonsGiven != null) {
            result.setTimeExtensionReasonsGiven(YesNo.valueOf(reasonsGiven));
        }
        String signedAndDated = resultSet.getString("time_extension_signed_and_dated");
        if (signedAndDated != null) {
            result.setTimeExtensionSignedAndDated(YesNo.valueOf(signedAndDated));
        }
        String signedBy = resultSet.getString("time_extension_signed_by");
        if (signedBy != null) {
            result.setTimeExtensionSignedBy(TimeExtensionSignedBy.valueOf(signedBy));
        }
        Date dateSigned = resultSet.getDate("time_extension_date_signed");
        if (dateSigned != null) {
            result.setTimeExtensionDateSigned(dateSigned.toLocalDate());
        }
        result.setTimeExtensionPrintFullName(resultSet.getString("time_extension_print_full_name"));
    }
}
