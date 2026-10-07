package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GeneralApplicationEntryBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void feeAnswersUseTheCcdFieldIds() throws Exception {
        TecCase data = new TecCase();
        GeneralApplicationEntry entry = new GeneralApplicationEntry();
        entry.setFeeReceived(YesNo.YES);
        entry.setFeeAmountReceived(30300);
        data.setGeneralApplication(entry);

        String json = mapper.writeValueAsString(data);

        assertThat(json).contains("\"genAppFeeReceived\":\"Yes\"");
        assertThat(json).contains("\"genAppFeeAmountReceived\":\"30300\"");

        TecCase read = mapper.readValue("""
            {
              "genAppFeeReceived": "Yes",
              "genAppFeeAmountReceived": "30300",
              "genAppDocument": {
                "document_url": "http://localhost:4506/documents/9384af76-24af-495e-a8b4-cc3f79d60736",
                "document_binary_url": "http://localhost:4506/documents/9384af76-24af-495e-a8b4-cc3f79d60736/binary",
                "document_filename": "Book2.xlsx"
              }
            }
            """, TecCase.class);

        assertThat(read.getGeneralApplication()).isNotNull();
        assertThat(read.getGeneralApplication().getFeeReceived()).isEqualTo(YesNo.YES);
        assertThat(read.getGeneralApplication().getFeeAmountReceived()).isEqualTo(30300);
        assertThat(read.getGeneralApplication().getDocument()).isNotNull();
        assertThat(read.getGeneralApplication().getDocument().getUrl()).contains("9384af76");
        assertThat(read.getGeneralApplication().getDocument().getBinaryUrl()).contains("/binary");
        assertThat(read.getGeneralApplication().getDocument().getFilename()).isEqualTo("Book2.xlsx");
    }
}
