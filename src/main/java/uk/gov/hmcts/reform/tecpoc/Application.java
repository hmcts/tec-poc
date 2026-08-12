package uk.gov.hmcts.reform.tecpoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import uk.gov.hmcts.reform.authorisation.ServiceAuthorisationApi;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.idam.client.IdamApi;

@SpringBootApplication(scanBasePackages = {
    "uk.gov.hmcts.reform.tecpoc",
    "uk.gov.hmcts.ccd.sdk"
})
@EnableFeignClients(clients = {IdamApi.class, CoreCaseDataApi.class, ServiceAuthorisationApi.class})
@SuppressWarnings("HideUtilityClassConstructor") // Spring needs a constructor, its not a utility class
public class Application {

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
