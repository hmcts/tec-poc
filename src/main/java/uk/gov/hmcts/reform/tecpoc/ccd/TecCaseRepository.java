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
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;

@Repository
@RequiredArgsConstructor
public class TecCaseRepository {

    /**
     * Highest PCN suffix, then the latest row. Shared by Case details and edit events.
     */
    private static final String CURRENT_RECORD_ORDER =
        "right(penalty_charge_number, 1)::int desc, created_at desc, id desc";

    private final NamedParameterJdbcTemplate database;

    @Transactional
    public void create(long caseReference, TecCase tecCase) {
        PcnNumbers.requireInitialRegistration(tecCase.getPenaltyChargeNumber());
        tecCase.setPcnStem(PcnNumbers.stem(tecCase.getPenaltyChargeNumber()));
        database.update("""
            insert into tec_case (
                case_reference, pcn_stem, local_authority,
                respondent_details_1, respondent_details_2, respondent_details_3,
                vehicle_registration_number
            ) values (
                :caseReference, :pcnStem, :localAuthority,
                :respondentDetails1, :respondentDetails2, :respondentDetails3,
                :vehicleRegistrationNumber
            )
            """, caseParameters(caseReference, tecCase));
        insertRegistrationRow(caseReference, tecCase);
    }

    public TecCase find(long caseReference) {
        TecCase result = database.queryForObject("""
            select pcn_stem, local_authority,
                   respondent_details_1, respondent_details_2, respondent_details_3,
                   vehicle_registration_number, form_validation_result
              from tec_case
             where case_reference = :caseReference
            """, Map.of("caseReference", caseReference), (resultSet, rowNumber) -> {
                TecCase loaded = new TecCase();
                loaded.setPcnStem(resultSet.getString("pcn_stem"));
                loaded.setLocalAuthority(LocalAuthority.valueOf(resultSet.getString("local_authority")));
                loaded.setRespondentDetails1(resultSet.getString("respondent_details_1"));
                loaded.setRespondentDetails2(resultSet.getString("respondent_details_2"));
                loaded.setRespondentDetails3(resultSet.getString("respondent_details_3"));
                loaded.setVehicleRegistrationNumber(resultSet.getString("vehicle_registration_number"));
                String formValidationResult = resultSet.getString("form_validation_result");
                if (formValidationResult != null) {
                    loaded.setFormValidationResult(FormValidationResult.valueOf(formValidationResult));
                }
                return loaded;
            });
        applyCurrentRegistration(result, caseReference);
        result.setTe9Details(findCurrentTe9(caseReference));
        result.setPe3Details(findCurrentPe3(caseReference));
        result.setTe7Details(findCurrentTe7(caseReference));
        result.setPe2Details(findCurrentPe2(caseReference));
        return result;
    }

    /**
     * Full PCN of the registration with the highest suffix.
     */
    public String currentRegistrationPcn(long caseReference) {
        List<String> pcns = database.query("""
            select penalty_charge_number
              from tec_case_registration
             where case_reference = :caseReference
             order by %s
             limit 1
            """.formatted(CURRENT_RECORD_ORDER), Map.of("caseReference", caseReference),
            (resultSet, rowNumber) -> resultSet.getString("penalty_charge_number"));
        if (pcns.isEmpty()) {
            throw new IllegalArgumentException("No registration record on case " + caseReference);
        }
        return pcns.get(0);
    }

    public void linkBatchCase(long caseReference, long batchCaseReference) {
        int updated = database.update("""
            update tec_case_registration
               set batch_case_reference = :batchCaseReference
             where id = (
                select id
                  from tec_case_registration
                 where case_reference = :caseReference
                 order by %s
                 limit 1
             )
            """.formatted(CURRENT_RECORD_ORDER), new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("batchCaseReference", batchCaseReference));
        if (updated == 0) {
            throw new IllegalArgumentException("No registration record on case " + caseReference);
        }
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

    /**
     * Batch linked to the current registration, or null when that registration has no batch yet.
     */
    public Long findBatchCaseReference(long caseReference) {
        List<Long> batches = database.query("""
            select batch_case_reference
              from tec_case_registration
             where case_reference = :caseReference
             order by %s
             limit 1
            """.formatted(CURRENT_RECORD_ORDER), Map.of("caseReference", caseReference),
            (resultSet, rowNumber) -> {
                long batchCaseReference = resultSet.getLong("batch_case_reference");
                return resultSet.wasNull() ? null : batchCaseReference;
            });
        if (batches.isEmpty()) {
            return null;
        }
        return batches.get(0);
    }

    public void recordApplication(long caseReference, TecCase tecCase) {
        requireFormPcn(caseReference, tecCase.getApplicationPenaltyChargeNumber());
        if (tecCase.getApplicationForm() == ApplicationForm.TE9) {
            insertTe9(caseReference, tecCase);
            return;
        }
        if (tecCase.getApplicationForm() == ApplicationForm.PE3) {
            insertPe3(caseReference, tecCase);
            return;
        }
        throw new IllegalArgumentException("applicationForm must be TE9 or PE3");
    }

    /**
     * Clerk edit of TE9 fields. Does not change form type or penalty charge number.
     */
    public void editTe9Application(long caseReference, TecCase tecCase) {
        updateCurrent("""
            update tec_case_te9
               set date_received = :applicationDateReceived,
                   type = :applicationType,
                   te7_submitted = :applicationTe7Submitted,
                   vehicle_registration = :applicationVehicleRegistration,
                   applicant = :applicationApplicant,
                   location_of_contravention = :applicationLocationOfContravention,
                   date_of_contravention = :applicationDateOfContravention,
                   title = :applicationTitle,
                   full_name = :applicationFullName,
                   company_name = :applicationCompanyName,
                   address = :applicationAddress,
                   postcode = :applicationPostcode,
                   declaration = :applicationDeclaration,
                   date_paid = :applicationDatePaid,
                   how_paid = :applicationHowPaid,
                   paid_to = :applicationPaidTo
             where id = (
                select id from tec_case_te9
                 where case_reference = :caseReference
                 order by %s
                 limit 1
             )
            """, "No TE9 record on case " + caseReference, applicationEditParams(caseReference, tecCase));
    }

    /**
     * Clerk edit of PE3 fields. Does not change form type or penalty charge number.
     */
    public void editPe3Application(long caseReference, TecCase tecCase) {
        updateCurrent("""
            update tec_case_pe3
               set date_received = :applicationDateReceived,
                   type = :applicationType,
                   te7_submitted = :applicationTe7Submitted,
                   vehicle_registration = :applicationVehicleRegistration,
                   applicant = :applicationApplicant,
                   location_of_contravention = :applicationLocationOfContravention,
                   date_of_contravention = :applicationDateOfContravention,
                   title = :applicationTitle,
                   full_name = :applicationFullName,
                   company_name = :applicationCompanyName,
                   address = :applicationAddress,
                   postcode = :applicationPostcode,
                   declaration = :applicationDeclaration,
                   reasons_given = :applicationReasonsGiven,
                   date_paid = :applicationDatePaid,
                   how_paid = :applicationHowPaid,
                   paid_to = :applicationPaidTo
             where id = (
                select id from tec_case_pe3
                 where case_reference = :caseReference
                 order by %s
                 limit 1
             )
            """, "No PE3 record on case " + caseReference, applicationEditParams(caseReference, tecCase)
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
               set form_validation_result = coalesce(:formValidationResult, form_validation_result)
             where case_reference = :caseReference
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue(
                "formValidationResult",
                tecCase.getFormValidationResult() == null
                    ? null
                    : tecCase.getFormValidationResult().name()
            ));
        requireFormPcn(caseReference, tecCase.getTimeExtensionPenaltyChargeNumber());
        if (tecCase.getTimeExtensionForm() == TimeExtensionForm.TE7) {
            insertTe7(caseReference, tecCase);
            return;
        }
        if (tecCase.getTimeExtensionForm() == TimeExtensionForm.PE2) {
            insertPe2(caseReference, tecCase);
            return;
        }
        throw new IllegalArgumentException("timeExtensionForm must be TE7 or PE2");
    }

    /**
     * Clerk edit of TE7 fields. Does not change form type or penalty charge number.
     */
    public void editTe7Application(long caseReference, TecCase tecCase) {
        updateCurrent("""
            update tec_case_te7
               set vehicle_registration = :timeExtensionVehicleRegistration,
                   title = :timeExtensionTitle,
                   other_title = :timeExtensionOtherTitle,
                   full_name = :timeExtensionFullName,
                   company_name = :timeExtensionCompanyName,
                   address = :timeExtensionAddress,
                   postcode = :timeExtensionPostcode,
                   permission_type = :timeExtensionPermissionType,
                   reasons_given = :timeExtensionReasonsGiven,
                   signed_and_dated = :timeExtensionSignedAndDated,
                   signed_by = :timeExtensionSignedBy,
                   date_signed = :timeExtensionDateSigned,
                   print_full_name = :timeExtensionPrintFullName
             where id = (
                select id from tec_case_te7
                 where case_reference = :caseReference
                 order by %s
                 limit 1
             )
            """, "No TE7 record on case " + caseReference, new MapSqlParameterSource()
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
        updateCurrent("""
            update tec_case_pe2
               set vehicle_registration = :timeExtensionVehicleRegistration,
                   applicant = :timeExtensionApplicant,
                   location_of_contravention = :timeExtensionLocationOfContravention,
                   date_of_contravention = :timeExtensionDateOfContravention,
                   full_name = :timeExtensionFullName,
                   address = :timeExtensionAddress,
                   postcode = :timeExtensionPostcode,
                   reasons_given = :timeExtensionReasonsGiven,
                   signed_and_dated = :timeExtensionSignedAndDated,
                   date_signed = :timeExtensionDateSigned
             where id = (
                select id from tec_case_pe2
                 where case_reference = :caseReference
                 order by %s
                 limit 1
             )
            """, "No PE2 record on case " + caseReference, new MapSqlParameterSource()
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
        updateCurrent("""
            update tec_case_registration
               set payment_status = :status,
                   payment_reference = :reference,
                   closure_reason = :closureReason
             where id = (
                select id from tec_case_registration
                 where case_reference = :caseReference
                 order by %s
                 limit 1
             )
            """, "No registration record on case " + caseReference, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("status", status)
            .addValue("reference", reference)
            .addValue("closureReason", closureReason));
    }

    public void recordRegistration(long caseReference, String document, LocalDate registrationDate) {
        updateCurrent("""
            update tec_case_registration
               set registration_document = :document,
                   registration_date = :registrationDate
             where id = (
                select id from tec_case_registration
                 where case_reference = :caseReference
                 order by %s
                 limit 1
             )
            """, "No registration record on case " + caseReference, new MapSqlParameterSource()
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

    private MapSqlParameterSource caseParameters(long caseReference, TecCase tecCase) {
        return new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("pcnStem", tecCase.getPcnStem())
            .addValue(
                "localAuthority",
                tecCase.getLocalAuthority() == null ? null : tecCase.getLocalAuthority().name()
            )
            .addValue("respondentDetails1", tecCase.getRespondentDetails1())
            .addValue("respondentDetails2", tecCase.getRespondentDetails2())
            .addValue("respondentDetails3", tecCase.getRespondentDetails3())
            .addValue("vehicleRegistrationNumber", tecCase.getVehicleRegistrationNumber());
    }

    /**
     * Adds a registration without changing the case-level list fields.
     * The caller supplies the next PCN suffix.
     */
    public void insertRegistration(long caseReference, TecCase tecCase) {
        insertRegistrationRow(caseReference, tecCase);
    }

    private void insertRegistrationRow(long caseReference, TecCase tecCase) {
        database.update("""
            insert into tec_case_registration (
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
            """, registrationParameters(caseReference, tecCase));
    }

    private MapSqlParameterSource registrationParameters(long caseReference, TecCase tecCase) {
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

    private void applyCurrentRegistration(TecCase result, long caseReference) {
        List<TecCase> rows = database.query("""
            select file_identifier, batch_identifier, batch_case_reference, penalty_charge_number,
                   respondent_details_4, respondent_details_5, respondent_details_6,
                   nature_of_offence, date_charge_certificate_served, amount_due,
                   payment_status, payment_reference, closure_reason,
                   registration_document, registration_date
              from tec_case_registration
             where case_reference = :caseReference
             order by %s
             limit 1
            """.formatted(CURRENT_RECORD_ORDER), Map.of("caseReference", caseReference),
            (resultSet, rowNumber) -> {
                TecCase registration = new TecCase();
                registration.setFileIdentifier(resultSet.getString("file_identifier"));
                registration.setBatchIdentifier(resultSet.getString("batch_identifier"));
                long batchCaseReference = resultSet.getLong("batch_case_reference");
                if (!resultSet.wasNull()) {
                    registration.setBatchCase(CaseLink.builder()
                        .caseReference(Long.toString(batchCaseReference))
                        .caseType(BatchCaseConfiguration.CASE_TYPE)
                        .build());
                }
                registration.setPenaltyChargeNumber(resultSet.getString("penalty_charge_number"));
                registration.setRespondentDetails4(resultSet.getString("respondent_details_4"));
                registration.setRespondentDetails5(resultSet.getString("respondent_details_5"));
                registration.setRespondentDetails6(resultSet.getString("respondent_details_6"));
                registration.setNatureOfOffence(resultSet.getString("nature_of_offence"));
                registration.setDateChargeCertificateServed(
                    resultSet.getString("date_charge_certificate_served")
                );
                registration.setAmountDue(resultSet.getInt("amount_due"));
                registration.setPaymentStatus(resultSet.getString("payment_status"));
                registration.setPaymentReference(resultSet.getString("payment_reference"));
                registration.setClosureReason(resultSet.getString("closure_reason"));
                registration.setRegistrationDocument(resultSet.getString("registration_document"));
                Date registrationDate = resultSet.getDate("registration_date");
                if (registrationDate != null) {
                    registration.setRegistrationDate(registrationDate.toLocalDate());
                }
                return registration;
            });
        if (rows.isEmpty()) {
            return;
        }
        TecCase registration = rows.get(0);
        result.setFileIdentifier(registration.getFileIdentifier());
        result.setBatchIdentifier(registration.getBatchIdentifier());
        result.setBatchCase(registration.getBatchCase());
        result.setPenaltyChargeNumber(registration.getPenaltyChargeNumber());
        result.setRespondentDetails4(registration.getRespondentDetails4());
        result.setRespondentDetails5(registration.getRespondentDetails5());
        result.setRespondentDetails6(registration.getRespondentDetails6());
        result.setNatureOfOffence(registration.getNatureOfOffence());
        result.setDateChargeCertificateServed(registration.getDateChargeCertificateServed());
        result.setAmountDue(registration.getAmountDue());
        result.setPaymentStatus(registration.getPaymentStatus());
        result.setPaymentReference(registration.getPaymentReference());
        result.setClosureReason(registration.getClosureReason());
        result.setRegistrationDocument(registration.getRegistrationDocument());
        result.setRegistrationDate(registration.getRegistrationDate());
    }

    private void requireFormPcn(long caseReference, String penaltyChargeNumber) {
        String current = currentRegistrationPcn(caseReference);
        if (!current.equals(penaltyChargeNumber)) {
            throw new IllegalArgumentException(
                "Form PCN must match the current registration PCN " + current
            );
        }
    }

    private void updateCurrent(String sql, String missingMessage, MapSqlParameterSource parameters) {
        int updated = database.update(sql.formatted(CURRENT_RECORD_ORDER), parameters);
        if (updated == 0) {
            throw new IllegalArgumentException(missingMessage);
        }
    }

    private void insertTe9(long caseReference, TecCase tecCase) {
        database.update("""
            insert into tec_case_te9 (
                case_reference, penalty_charge_number, date_received, type, te7_submitted,
                vehicle_registration, applicant, location_of_contravention, date_of_contravention,
                title, full_name, company_name, address, postcode, declaration,
                date_paid, how_paid, paid_to
            ) values (
                :caseReference, :penaltyChargeNumber, :dateReceived, :type, :te7Submitted,
                :vehicleRegistration, :applicant, :locationOfContravention, :dateOfContravention,
                :title, :fullName, :companyName, :address, :postcode, :declaration,
                :datePaid, :howPaid, :paidTo
            )
            """, applicationInsertParameters(caseReference, tecCase));
    }

    private void insertPe3(long caseReference, TecCase tecCase) {
        database.update("""
            insert into tec_case_pe3 (
                case_reference, penalty_charge_number, date_received, type, te7_submitted,
                vehicle_registration, applicant, location_of_contravention, date_of_contravention,
                title, full_name, company_name, address, postcode, declaration, reasons_given,
                date_paid, how_paid, paid_to
            ) values (
                :caseReference, :penaltyChargeNumber, :dateReceived, :type, :te7Submitted,
                :vehicleRegistration, :applicant, :locationOfContravention, :dateOfContravention,
                :title, :fullName, :companyName, :address, :postcode, :declaration, :reasonsGiven,
                :datePaid, :howPaid, :paidTo
            )
            """, applicationInsertParameters(caseReference, tecCase)
            .addValue(
                "reasonsGiven",
                tecCase.getApplicationReasonsGiven() == null
                    ? null
                    : tecCase.getApplicationReasonsGiven().name()
            ));
    }

    private MapSqlParameterSource applicationInsertParameters(long caseReference, TecCase tecCase) {
        return new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("penaltyChargeNumber", tecCase.getApplicationPenaltyChargeNumber())
            .addValue("dateReceived", tecCase.getApplicationDateReceived())
            .addValue(
                "type",
                tecCase.getApplicationType() == null ? null : tecCase.getApplicationType().name()
            )
            .addValue("te7Submitted", yesNoName(tecCase.getApplicationTe7Submitted()))
            .addValue("vehicleRegistration", tecCase.getApplicationVehicleRegistration())
            .addValue("applicant", tecCase.getApplicationApplicant())
            .addValue("locationOfContravention", tecCase.getApplicationLocationOfContravention())
            .addValue("dateOfContravention", tecCase.getApplicationDateOfContravention())
            .addValue("title", tecCase.getApplicationTitle())
            .addValue("fullName", tecCase.getApplicationFullName())
            .addValue("companyName", tecCase.getApplicationCompanyName())
            .addValue("address", tecCase.getApplicationAddress())
            .addValue("postcode", tecCase.getApplicationPostcode())
            .addValue(
                "declaration",
                tecCase.getApplicationDeclaration() == null
                    ? null
                    : tecCase.getApplicationDeclaration().name()
            )
            .addValue("datePaid", tecCase.getApplicationDatePaid())
            .addValue("howPaid", tecCase.getApplicationHowPaid())
            .addValue("paidTo", tecCase.getApplicationPaidTo());
    }

    private void insertTe7(long caseReference, TecCase tecCase) {
        database.update("""
            insert into tec_case_te7 (
                case_reference, penalty_charge_number, vehicle_registration, title, other_title,
                full_name, company_name, address, postcode, permission_type, reasons_given,
                signed_and_dated, signed_by, date_signed, print_full_name
            ) values (
                :caseReference, :penaltyChargeNumber, :vehicleRegistration, :title, :otherTitle,
                :fullName, :companyName, :address, :postcode, :permissionType, :reasonsGiven,
                :signedAndDated, :signedBy, :dateSigned, :printFullName
            )
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("penaltyChargeNumber", tecCase.getTimeExtensionPenaltyChargeNumber())
            .addValue("vehicleRegistration", tecCase.getTimeExtensionVehicleRegistration())
            .addValue("title", tecCase.getTimeExtensionTitle())
            .addValue("otherTitle", tecCase.getTimeExtensionOtherTitle())
            .addValue("fullName", tecCase.getTimeExtensionFullName())
            .addValue("companyName", tecCase.getTimeExtensionCompanyName())
            .addValue("address", tecCase.getTimeExtensionAddress())
            .addValue("postcode", tecCase.getTimeExtensionPostcode())
            .addValue(
                "permissionType",
                tecCase.getTimeExtensionPermissionType() == null
                    ? null
                    : tecCase.getTimeExtensionPermissionType().name()
            )
            .addValue("reasonsGiven", yesNoName(tecCase.getTimeExtensionReasonsGiven()))
            .addValue("signedAndDated", yesNoName(tecCase.getTimeExtensionSignedAndDated()))
            .addValue(
                "signedBy",
                tecCase.getTimeExtensionSignedBy() == null
                    ? null
                    : tecCase.getTimeExtensionSignedBy().name()
            )
            .addValue("dateSigned", tecCase.getTimeExtensionDateSigned())
            .addValue("printFullName", tecCase.getTimeExtensionPrintFullName()));
    }

    private void insertPe2(long caseReference, TecCase tecCase) {
        database.update("""
            insert into tec_case_pe2 (
                case_reference, penalty_charge_number, vehicle_registration, applicant,
                location_of_contravention, date_of_contravention, full_name, address, postcode,
                reasons_given, signed_and_dated, date_signed
            ) values (
                :caseReference, :penaltyChargeNumber, :vehicleRegistration, :applicant,
                :locationOfContravention, :dateOfContravention, :fullName, :address, :postcode,
                :reasonsGiven, :signedAndDated, :dateSigned
            )
            """, new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("penaltyChargeNumber", tecCase.getTimeExtensionPenaltyChargeNumber())
            .addValue("vehicleRegistration", tecCase.getTimeExtensionVehicleRegistration())
            .addValue("applicant", tecCase.getTimeExtensionApplicant())
            .addValue("locationOfContravention", tecCase.getTimeExtensionLocationOfContravention())
            .addValue("dateOfContravention", tecCase.getTimeExtensionDateOfContravention())
            .addValue("fullName", tecCase.getTimeExtensionFullName())
            .addValue("address", tecCase.getTimeExtensionAddress())
            .addValue("postcode", tecCase.getTimeExtensionPostcode())
            .addValue("reasonsGiven", yesNoName(tecCase.getTimeExtensionReasonsGiven()))
            .addValue("signedAndDated", yesNoName(tecCase.getTimeExtensionSignedAndDated()))
            .addValue("dateSigned", tecCase.getTimeExtensionDateSigned()));
    }

    private Te9CaseDetails findCurrentTe9(long caseReference) {
        List<Te9CaseDetails> rows = database.query("""
            select penalty_charge_number, date_received, type, te7_submitted, vehicle_registration,
                   applicant, location_of_contravention, date_of_contravention, title, full_name,
                   company_name, address, postcode, declaration, date_paid, how_paid, paid_to
              from tec_case_te9
             where case_reference = :caseReference
             order by %s
             limit 1
            """.formatted(CURRENT_RECORD_ORDER), Map.of("caseReference", caseReference),
            (resultSet, rowNumber) -> {
                Te9CaseDetails details = new Te9CaseDetails();
                details.setForm(ApplicationForm.TE9);
                details.setPenaltyChargeNumber(resultSet.getString("penalty_charge_number"));
                details.setDateReceived(localDate(resultSet, "date_received"));
                details.setType(enumValue(ApplicationTimeliness.class, resultSet.getString("type")));
                details.setTe7Submitted(yesNo(resultSet.getString("te7_submitted")));
                details.setVehicleRegistration(resultSet.getString("vehicle_registration"));
                details.setApplicant(resultSet.getString("applicant"));
                details.setLocationOfContravention(resultSet.getString("location_of_contravention"));
                details.setDateOfContravention(localDate(resultSet, "date_of_contravention"));
                details.setTitle(resultSet.getString("title"));
                details.setFullName(resultSet.getString("full_name"));
                details.setCompanyName(resultSet.getString("company_name"));
                details.setAddress(resultSet.getString("address"));
                details.setPostcode(resultSet.getString("postcode"));
                details.setDeclaration(
                    enumValue(ApplicationDeclaration.class, resultSet.getString("declaration"))
                );
                details.setDatePaid(localDate(resultSet, "date_paid"));
                details.setHowPaid(resultSet.getString("how_paid"));
                details.setPaidTo(resultSet.getString("paid_to"));
                return details;
            });
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Pe3CaseDetails findCurrentPe3(long caseReference) {
        List<Pe3CaseDetails> rows = database.query("""
            select penalty_charge_number, date_received, type, te7_submitted, vehicle_registration,
                   applicant, location_of_contravention, date_of_contravention, title, full_name,
                   company_name, address, postcode, declaration, reasons_given,
                   date_paid, how_paid, paid_to
              from tec_case_pe3
             where case_reference = :caseReference
             order by %s
             limit 1
            """.formatted(CURRENT_RECORD_ORDER), Map.of("caseReference", caseReference),
            (resultSet, rowNumber) -> {
                Pe3CaseDetails details = new Pe3CaseDetails();
                details.setForm(ApplicationForm.PE3);
                details.setPenaltyChargeNumber(resultSet.getString("penalty_charge_number"));
                details.setDateReceived(localDate(resultSet, "date_received"));
                details.setType(enumValue(ApplicationTimeliness.class, resultSet.getString("type")));
                details.setTe7Submitted(yesNo(resultSet.getString("te7_submitted")));
                details.setVehicleRegistration(resultSet.getString("vehicle_registration"));
                details.setApplicant(resultSet.getString("applicant"));
                details.setLocationOfContravention(resultSet.getString("location_of_contravention"));
                details.setDateOfContravention(localDate(resultSet, "date_of_contravention"));
                details.setTitle(resultSet.getString("title"));
                details.setFullName(resultSet.getString("full_name"));
                details.setCompanyName(resultSet.getString("company_name"));
                details.setAddress(resultSet.getString("address"));
                details.setPostcode(resultSet.getString("postcode"));
                details.setDeclaration(
                    enumValue(ApplicationDeclaration.class, resultSet.getString("declaration"))
                );
                details.setReasonsGiven(yesNo(resultSet.getString("reasons_given")));
                details.setDatePaid(localDate(resultSet, "date_paid"));
                details.setHowPaid(resultSet.getString("how_paid"));
                details.setPaidTo(resultSet.getString("paid_to"));
                return details;
            });
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Te7CaseDetails findCurrentTe7(long caseReference) {
        List<Te7CaseDetails> rows = database.query("""
            select penalty_charge_number, vehicle_registration, title, other_title, full_name,
                   company_name, address, postcode, permission_type, reasons_given,
                   signed_and_dated, signed_by, date_signed, print_full_name
              from tec_case_te7
             where case_reference = :caseReference
             order by %s
             limit 1
            """.formatted(CURRENT_RECORD_ORDER), Map.of("caseReference", caseReference),
            (resultSet, rowNumber) -> {
                Te7CaseDetails details = new Te7CaseDetails();
                details.setForm(TimeExtensionForm.TE7);
                details.setPenaltyChargeNumber(resultSet.getString("penalty_charge_number"));
                details.setVehicleRegistration(resultSet.getString("vehicle_registration"));
                details.setTitle(resultSet.getString("title"));
                details.setOtherTitle(resultSet.getString("other_title"));
                details.setFullName(resultSet.getString("full_name"));
                details.setCompanyName(resultSet.getString("company_name"));
                details.setAddress(resultSet.getString("address"));
                details.setPostcode(resultSet.getString("postcode"));
                details.setPermissionType(enumValue(
                    TimeExtensionPermissionType.class,
                    resultSet.getString("permission_type")
                ));
                details.setReasonsGiven(yesNo(resultSet.getString("reasons_given")));
                details.setSignedAndDated(yesNo(resultSet.getString("signed_and_dated")));
                details.setSignedBy(
                    enumValue(TimeExtensionSignedBy.class, resultSet.getString("signed_by"))
                );
                details.setDateSigned(localDate(resultSet, "date_signed"));
                details.setPrintFullName(resultSet.getString("print_full_name"));
                return details;
            });
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Pe2CaseDetails findCurrentPe2(long caseReference) {
        List<Pe2CaseDetails> rows = database.query("""
            select penalty_charge_number, vehicle_registration, applicant,
                   location_of_contravention, date_of_contravention, full_name, address, postcode,
                   reasons_given, signed_and_dated, date_signed
              from tec_case_pe2
             where case_reference = :caseReference
             order by %s
             limit 1
            """.formatted(CURRENT_RECORD_ORDER), Map.of("caseReference", caseReference),
            (resultSet, rowNumber) -> {
                Pe2CaseDetails details = new Pe2CaseDetails();
                details.setForm(TimeExtensionForm.PE2);
                details.setPenaltyChargeNumber(resultSet.getString("penalty_charge_number"));
                details.setVehicleRegistration(resultSet.getString("vehicle_registration"));
                details.setApplicant(resultSet.getString("applicant"));
                details.setLocationOfContravention(resultSet.getString("location_of_contravention"));
                details.setDateOfContravention(localDate(resultSet, "date_of_contravention"));
                details.setFullName(resultSet.getString("full_name"));
                details.setAddress(resultSet.getString("address"));
                details.setPostcode(resultSet.getString("postcode"));
                details.setReasonsGiven(yesNo(resultSet.getString("reasons_given")));
                details.setSignedAndDated(yesNo(resultSet.getString("signed_and_dated")));
                details.setDateSigned(localDate(resultSet, "date_signed"));
                return details;
            });
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static LocalDate localDate(ResultSet resultSet, String column) throws SQLException {
        Date value = resultSet.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
