package uk.gov.hmcts.reform.tecpoc.ccd;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TecCaseRepository {

    private final NamedParameterJdbcTemplate database;

    public void create(long caseReference, TecCase tecCase) {
        database.update("""
            insert into tec_case (
                case_reference, file_identifier, batch_identifier, penalty_charge_number,
                respondent_details_1, respondent_details_2, respondent_details_3,
                respondent_details_4, respondent_details_5, respondent_details_6,
                vehicle_registration_number, nature_of_offence,
                date_charge_certificate_served, amount_due
            ) values (
                :caseReference, :fileIdentifier, :batchIdentifier, :penaltyChargeNumber,
                :respondentDetails1, :respondentDetails2, :respondentDetails3,
                :respondentDetails4, :respondentDetails5, :respondentDetails6,
                :vehicleRegistrationNumber, :natureOfOffence,
                :dateChargeCertificateServed, :amountDue
            )
            """, parameters(caseReference, tecCase));
    }

    public TecCase find(long caseReference) {
        return database.queryForObject("""
            select file_identifier, batch_identifier, penalty_charge_number,
                   respondent_details_1, respondent_details_2, respondent_details_3,
                   respondent_details_4, respondent_details_5, respondent_details_6,
                   vehicle_registration_number, nature_of_offence,
                   date_charge_certificate_served, amount_due, payment_status,
                   payment_reference, closure_reason, registration_document, registration_date,
                   form_validation_result
              from tec_case
             where case_reference = :caseReference
            """, Map.of("caseReference", caseReference), (resultSet, rowNumber) -> {
                TecCase result = new TecCase();
                result.setFileIdentifier(resultSet.getString("file_identifier"));
                result.setBatchIdentifier(resultSet.getString("batch_identifier"));
                result.setPenaltyChargeNumber(resultSet.getString("penalty_charge_number"));
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
                return result;
            });
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

    private MapSqlParameterSource parameters(long caseReference, TecCase tecCase) {
        return new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("fileIdentifier", tecCase.getFileIdentifier())
            .addValue("batchIdentifier", tecCase.getBatchIdentifier())
            .addValue("penaltyChargeNumber", tecCase.getPenaltyChargeNumber())
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
}
