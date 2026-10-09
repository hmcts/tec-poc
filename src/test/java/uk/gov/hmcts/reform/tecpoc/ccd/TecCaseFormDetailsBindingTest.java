package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TecCaseFormDetailsBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void witnessStatementAndTimeExtensionUseTheCaseDetailsFieldIds() throws Exception {
        Te9CaseDetails te9 = new Te9CaseDetails();
        te9.setForm(ApplicationForm.TE9);
        te9.setType(ApplicationTimeliness.OUT_OF_TIME);
        te9.setPenaltyChargeNumber("WE181512770");
        Te7CaseDetails te7 = new Te7CaseDetails();
        te7.setForm(TimeExtensionForm.TE7);
        te7.setPermissionType(TimeExtensionPermissionType.FOR_MORE_TIME);
        te7.setPenaltyChargeNumber("WE181512770");
        TecCase data = new TecCase();
        data.setTe9Details(te9);
        data.setTe7Details(te7);

        String json = mapper.writeValueAsString(data);

        assertThat(json).contains("\"te9Form\":\"TE9\"");
        assertThat(json).contains("\"te9Type\":\"outOfTime\"");
        assertThat(json).contains("\"te9PenaltyChargeNumber\":\"WE181512770\"");
        assertThat(json).contains("\"te7Form\":\"TE7\"");
        assertThat(json).contains("\"te7PermissionType\":\"forMoreTime\"");
        assertThat(json).contains("\"te7PenaltyChargeNumber\":\"WE181512770\"");
        assertThat(json).doesNotContain("\"te9form\"");
        assertThat(json).doesNotContain("\"te7form\"");
    }
}
