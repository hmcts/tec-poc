package uk.gov.hmcts.reform.tecpoc.ccd;

import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.CaseView;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;
import uk.gov.hmcts.ccd.sdk.type.Document;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

@Component
@RequiredArgsConstructor
public class TecCaseView implements CaseView<TecCase, CaseState> {

    private final TecCaseRepository repository;

    private static final String FORM_VALIDATION_NOT_RECORDED = "Not validated";

    @Override
    public TecCase getCase(CaseViewRequest<CaseState> request) {
        TecCase tecCase = repository.find(request.caseRef());
        tecCase.setTasksMarkdown(
            TecPrototypeTasks.markdownFor(request.caseRef(), request.state(), tecCase)
        );
        FormValidationResult validationResult = tecCase.getFormValidationResult();
        tecCase.setFormValidationResultDisplay(
            validationResult == null ? FORM_VALIDATION_NOT_RECORDED : validationResult.getLabel()
        );
        tecCase.setAllDocuments(toAllDocuments(repository.findDocuments(request.caseRef())));
        return tecCase;
    }

    static List<ListValue<Document>> toAllDocuments(List<TecCaseDocument> documents) {
        return documents.stream()
            .map(TecCaseView::toListValue)
            .toList();
    }

    private static ListValue<Document> toListValue(TecCaseDocument document) {
        Document ccdDocument = Document.builder()
            .url(document.documentUrl())
            .binaryUrl(document.documentBinaryUrl())
            .filename(document.filename())
            .categoryId(document.categoryId())
            .uploadTimestamp(
                document.createdAt() == null
                    ? null
                    : document.createdAt().atZone(ZoneOffset.UTC).toLocalDateTime()
            )
            .build();
        return ListValue.<Document>builder()
            .id(document.id().toString())
            .value(ccdDocument)
            .build();
    }
}
