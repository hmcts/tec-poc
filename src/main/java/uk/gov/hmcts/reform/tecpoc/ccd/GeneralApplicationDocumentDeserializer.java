package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import uk.gov.hmcts.ccd.sdk.type.Document;

/**
 * The unwrapped {@code genApp} prefix is applied to this document's own fields on
 * the way in, so Jackson looks for {@code genAppdocument_url} instead of
 * {@code document_url}. Read the CCD document shape directly.
 */
final class GeneralApplicationDocumentDeserializer extends JsonDeserializer<Document> {

    @Override
    public Document deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }
        String url = text(node, "document_url");
        String filename = text(node, "document_filename");
        String binaryUrl = text(node, "document_binary_url");
        if (url == null && filename == null && binaryUrl == null) {
            return null;
        }
        return Document.builder()
            .url(url)
            .filename(filename)
            .binaryUrl(binaryUrl)
            .categoryId(text(node, "category_id"))
            .build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
