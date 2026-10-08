package uk.gov.hmcts.reform.tecpoc.ccd;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BatchCaseRepository {

    private final NamedParameterJdbcTemplate database;

    public void create(long caseReference, BatchCase batchCase) {
        database.update("""
            insert into tec_batch (
                case_reference, file_identifier, batch_identifier, pcn_count, operation,
                received_via, received_at, local_authority, submitter_email
            ) values (
                :caseReference, :fileIdentifier, :batchIdentifier, :pcnCount, :operation,
                :receivedVia, :receivedAt, :localAuthority, :submitterEmail
            )
            """, parameters(caseReference, batchCase));
    }

    public boolean exists(long caseReference) {
        Integer count = database.queryForObject(
            """
            select count(*)
              from tec_batch
             where case_reference = :caseReference
            """,
            Map.of("caseReference", caseReference),
            Integer.class
        );
        return count != null && count > 0;
    }

    public BatchCase find(long caseReference) {
        return database.queryForObject("""
            select file_identifier, batch_identifier, pcn_count, operation, received_via,
                   received_at, local_authority, submitter_email
              from tec_batch
             where case_reference = :caseReference
            """, Map.of("caseReference", caseReference), (resultSet, rowNumber) -> {
                BatchCase result = new BatchCase();
                result.setFileIdentifier(resultSet.getString("file_identifier"));
                result.setBatchIdentifier(resultSet.getString("batch_identifier"));
                result.setPcnCount(resultSet.getInt("pcn_count"));
                result.setOperation(BatchOperation.valueOf(resultSet.getString("operation")));
                result.setReceivedVia(BatchReceivedVia.valueOf(resultSet.getString("received_via")));
                Timestamp receivedAt = resultSet.getTimestamp("received_at");
                if (receivedAt != null) {
                    result.setReceivedAt(receivedAt.toLocalDateTime());
                }
                result.setLocalAuthority(LocalAuthority.valueOf(resultSet.getString("local_authority")));
                result.setSubmitterEmail(resultSet.getString("submitter_email"));
                return result;
            });
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
            insert into tec_batch_document (
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

    public List<BatchCaseDocument> findDocuments(long caseReference) {
        return database.query("""
            select id, category_id, document_url, document_binary_url, filename, created_at
              from tec_batch_document
             where case_reference = :caseReference
             order by created_at asc, id asc
            """, Map.of("caseReference", caseReference), (resultSet, rowNumber) -> {
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                return new BatchCaseDocument(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("category_id"),
                    resultSet.getString("document_url"),
                    resultSet.getString("document_binary_url"),
                    resultSet.getString("filename"),
                    createdAt == null ? null : createdAt.toInstant()
                );
            });
    }

    /**
     * PCNs linked to this batch via a registration row and/or {@code tec_batch_pcn_link}.
     */
    public List<Long> findLinkedPcnCaseReferences(long batchCaseReference) {
        return database.queryForList("""
            select case_reference
              from (
                select case_reference
                  from tec_case_registration
                 where batch_case_reference = :batchCaseReference
                union
                select pcn_case_reference as case_reference
                  from tec_batch_pcn_link
                 where batch_case_reference = :batchCaseReference
              ) linked
             order by case_reference asc
            """, Map.of("batchCaseReference", batchCaseReference), Long.class);
    }

    /**
     * Records membership of a PCN in a non-registration batch ({@code tec_batch_pcn_link}).
     * Idempotent for the same (batch, pcn) pair.
     */
    public void linkPcnCase(long batchCaseReference, long pcnCaseReference) {
        database.update("""
            insert into tec_batch_pcn_link (batch_case_reference, pcn_case_reference)
            values (:batchCaseReference, :pcnCaseReference)
            on conflict (batch_case_reference, pcn_case_reference) do nothing
            """, new MapSqlParameterSource()
            .addValue("batchCaseReference", batchCaseReference)
            .addValue("pcnCaseReference", pcnCaseReference));
    }

    private MapSqlParameterSource parameters(long caseReference, BatchCase batchCase) {
        return new MapSqlParameterSource()
            .addValue("caseReference", caseReference)
            .addValue("fileIdentifier", batchCase.getFileIdentifier())
            .addValue("batchIdentifier", batchCase.getBatchIdentifier())
            .addValue("pcnCount", batchCase.getPcnCount())
            .addValue("operation", batchCase.getOperation().name())
            .addValue("receivedVia", batchCase.getReceivedVia().name())
            .addValue("receivedAt", batchCase.getReceivedAt())
            .addValue("localAuthority", batchCase.getLocalAuthority().name())
            .addValue("submitterEmail", batchCase.getSubmitterEmail());
    }
}
