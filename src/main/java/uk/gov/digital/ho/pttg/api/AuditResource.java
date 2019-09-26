package uk.gov.digital.ho.pttg.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.digital.ho.pttg.AuditService;

import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.value;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static uk.gov.digital.ho.pttg.api.RequestData.REQUEST_DURATION_MS;
import static uk.gov.digital.ho.pttg.application.LogEvent.*;

@RestController
@Slf4j
public class AuditResource {

    private final AuditService auditService;
    private final RequestData requestData;

    public AuditResource(AuditService auditService, RequestData requestData) {
        this.auditService = auditService;
        this.requestData = requestData;
    }

    @GetMapping(value = "/audit", produces = APPLICATION_JSON_VALUE)
    public List<AuditRecord> retrieveAllAuditData(Pageable pageable) {

        log.info("Audit records requested, {}", pageable, value(EVENT, PTTG_AUDIT_RETRIEVAL_REQUEST_RECEIVED));

        List<AuditRecord> auditRecords = auditService.getAllAuditData(pageable);

        log.info("{} audit records found",
                auditRecords.size(),
                value(EVENT, PTTG_AUDIT_RETRIEVAL_RESPONSE_SUCCESS),
                value(REQUEST_DURATION_MS, requestData.calculateRequestDuration()));

        return auditRecords;
    }

    @PostMapping(value = "/audit", consumes = APPLICATION_JSON_VALUE)
    public void recordAuditEntry(@RequestBody AuditableData auditableData) {
        throw new NullPointerException();
//        log.info("Audit request {} received for correlation id {}",
//                    auditableData.getEventType(),
//                    auditableData.getCorrelationId(),
//                    value(EVENT, PTTG_AUDIT_REQUEST_RECEIVED));
//
//        auditService.add(auditableData);
//
//        log.info("Audit request {} completed for correlation id {}",
//                    auditableData.getEventType(),
//                    auditableData.getCorrelationId(),
//                    value(EVENT, PTTG_AUDIT_REQUEST_COMPLETED),
//                    value(REQUEST_DURATION_MS, requestData.calculateRequestDuration()));
    }
}
