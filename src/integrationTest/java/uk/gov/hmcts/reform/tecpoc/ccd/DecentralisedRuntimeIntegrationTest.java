package uk.gov.hmcts.reform.tecpoc.ccd;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.ccd.sdk.CCDDefinitionGenerator;
import uk.gov.hmcts.ccd.sdk.ResolvedConfigRegistry;
import uk.gov.hmcts.reform.tecpoc.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DecentralisedRuntimeIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startsDecentralisedRuntimeAndAppliesSdkMigrations() throws Exception {
        assertThat(applicationContext.getBean(CCDDefinitionGenerator.class)).isNotNull();

        ResolvedConfigRegistry registry = applicationContext.getBean(ResolvedConfigRegistry.class);
        assertThat(registry.getAll()).isEmpty();

        assertThat(tableExists("case_data")).isTrue();
        assertThat(tableExists("case_event")).isTrue();

        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    private boolean tableExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_schema = 'ccd' and table_name = ?",
            Integer.class,
            tableName
        );
        return tableCount != null && tableCount == 1;
    }
}
