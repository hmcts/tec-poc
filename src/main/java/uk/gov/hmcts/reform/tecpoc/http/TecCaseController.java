package uk.gov.hmcts.reform.tecpoc.http;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.tecpoc.service.TecCaseCreationService;

import static org.springframework.http.HttpStatus.CREATED;

@RestController
@RequestMapping("/pcn-cases")
@RequiredArgsConstructor
public class TecCaseController {

    private final TecCaseCreationService creationService;

    @PostMapping
    public ResponseEntity<CreateTecCaseResponse> create(
        @Valid @RequestBody CreateTecCaseRequest request,
        @RequestHeader("Authorization") String authorisation
    ) {
        return ResponseEntity.status(CREATED).body(creationService.create(request, authorisation));
    }
}
