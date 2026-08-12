package uk.gov.hmcts.reform.tecpoc.cftlib;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.CCDDefinitionGenerator;
import uk.gov.hmcts.reform.tecpoc.ccd.CaseState;
import uk.gov.hmcts.reform.tecpoc.ccd.TecCaseConfiguration;
import uk.gov.hmcts.reform.tecpoc.ccd.UserRole;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLib;
import uk.gov.hmcts.rse.ccd.lib.api.CFTLibConfigurer;

import java.io.File;

@Component
public class TecCftLibConfiguration implements CFTLibConfigurer {

    private static final String CASEWORKER_GENERIC_ROLE = "caseworker";

    private static final String SYSTEM_USER = "tec-system@test.com";
    private static final String DEMO_USER = "tec-demo@test.com";

    private static final String TEC_JURISDICTION = "TEC";

    @Autowired
    @Lazy
    private CCDDefinitionGenerator definitionGenerator;

    @Override
    public void configure(CFTLib lib) throws Exception {
        lib.createRoles(
            CASEWORKER_GENERIC_ROLE,
            UserRole.SYSTEM.getRole(),
            UserRole.CLERK.getRole()
        );

        lib.createIdamUser(
            SYSTEM_USER,
            CASEWORKER_GENERIC_ROLE,
            UserRole.CLERK.getRole(),
            UserRole.SYSTEM.getRole()
        );
        lib.createIdamUser(
            DEMO_USER,
            CASEWORKER_GENERIC_ROLE,
            UserRole.CLERK.getRole()
        );

        definitionGenerator.generateAllCaseTypesToJSON(new File("build/ccd-definition"));
        lib.importJsonDefinition(new File("build/ccd-definition/" + TecCaseConfiguration.CASE_TYPE));

        lib.createProfile(
            DEMO_USER,
            TEC_JURISDICTION,
            TecCaseConfiguration.CASE_TYPE,
            CaseState.AWAITING_RESPONDENT_RESPONSE.name()
        );
    }
}
