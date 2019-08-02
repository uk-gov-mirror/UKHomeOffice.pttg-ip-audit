package uk.gov.digital.ho.pttg;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.digital.ho.pttg.alert.CountByUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.digital.ho.pttg.AuditEventType.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class AuditEntryJpaRepositoryTest {

    private static final String SESSION_ID = "sessionID";
    private static final String DEPLOYMENT = "deployment";
    private static final String USER_ID = "me";
    private static final String UUID = "uuid";
    private static final String NAMESPACE = "env";
    private static final String DETAIL = "{}";
    private static final LocalDateTime NOW = LocalDateTime.of(2017, 12, 8, 12, 0);
    private static final LocalDateTime NOW_MINUS_60_MINS = NOW.minusMinutes(60);
    private static final LocalDateTime NOW_PLUS_60_MINS = NOW.plusMinutes(60);
    private static final LocalDateTime TWO_DAYS_AGO = NOW.minusDays(2);
    private static final LocalDateTime YESTERDAY = NOW.minusDays(1);
    private static final LocalDateTime TOMORROW = NOW.plusDays(1);
    private static final LocalDateTime DAY_AFTER_TOMORROW = NOW.plusDays(2);

    @Autowired
    private AuditEntryJpaRepository repository;

    @Before
    public void setup() {

        List<AuditEntry> all = repository.findAllIpsStatistics();
        System.out.println(String.format("Found %d ips records already in database", all.size()));
        all.stream().forEach(audit -> System.out.println(String.format("Found %s", audit.getUuid())));


        System.out.println();
        repository.save(createAudit(TWO_DAYS_AGO));
        repository.save(createAudit(YESTERDAY));
        repository.save(createAudit(NOW_MINUS_60_MINS));
        repository.save(createAudit(NOW));
        repository.save(createAudit(NOW_PLUS_60_MINS));
        repository.save(createAudit(TOMORROW));
        repository.save(createAudit(DAY_AFTER_TOMORROW));
    }

    @After
    public void tearDown() {
        repository.deleteAll();
    }

    @Test
    public void shouldRetrieveAllAuditData() {

        final Iterable<AuditEntry> all = repository.findAll();
        assertThat(all).size().isEqualTo(7);
    }

    @Test
    public void shouldGetCountOfEntriesBetweenDates() {

        Long numberOfEntries = repository.countEntriesBetweenDates(YESTERDAY.withHour(0).withMinute(0),
                                                                    TOMORROW.withHour(23).withMinute(59),
                                                                    INCOME_PROVING_FINANCIAL_STATUS_RESPONSE,
                                                                    NAMESPACE);

        assertThat(numberOfEntries).isEqualTo(5);
    }

    @Test
    public void shouldGetEntriesBetweenDates() {

        repository.save(createAudit(NOW.minusMinutes(10))); // out of sequence addition to the table

        List<AuditEntry> auditEntries = repository.getEntriesBetweenDates(YESTERDAY.withHour(0).withMinute(0),
                                                                            TOMORROW.withHour(23).withMinute(59),
                                                                            INCOME_PROVING_FINANCIAL_STATUS_RESPONSE,
                                                                            NAMESPACE);

        assertThat(auditEntries)
                .extracting("timestamp")
                .containsExactly(
                        YESTERDAY,
                        NOW_MINUS_60_MINS,
                        NOW.minusMinutes(10),
                        NOW,
                        NOW_PLUS_60_MINS,
                        TOMORROW
                        );
    }

    @Test
    public void shouldGetEntriesBetweenDatesGroupedByUser() {

        String anotherUserId = "another user id";

        repository.save(createAudit(TWO_DAYS_AGO, anotherUserId));
        repository.save(createAudit(NOW, anotherUserId));
        repository.save(createAudit(DAY_AFTER_TOMORROW, anotherUserId));

        List<CountByUser> auditEntries = repository.countEntriesBetweenDatesGroupedByUser(YESTERDAY.withHour(0).withMinute(0),
                                                                                            TOMORROW.withHour(23).withMinute(59),
                                                                                            INCOME_PROVING_FINANCIAL_STATUS_RESPONSE,
                                                                                            NAMESPACE);

        assertThat(auditEntries).hasSize(2);

        assertThat(auditEntries)
                .extracting("userId")
                .containsExactly("another user id", "me");

        assertThat(auditEntries)
                .extracting("count")
                .containsExactly(1L, 5L);
    }

    @Test
    public void shouldRetrieveAllAuditDataLimitedByPagination() {

        Pageable pagination = PageRequest.of(0, 1);

        final Iterable<AuditEntry> all = repository.findAllByOrderByTimestampDesc(pagination);

        assertThat(all).size().isEqualTo(1);
        assertThat(all)
                .extracting("timestamp")
                .containsExactly(DAY_AFTER_TOMORROW);
    }

    @Test
    public void shouldRetrieveAllAuditOrderedByTimestampDesc() {

        final Iterable<AuditEntry> all = repository.findAllByOrderByTimestampDesc(null);
        assertThat(all).size().isEqualTo(7);
        assertThat(all)
                .extracting("timestamp")
                .containsExactly(
                        DAY_AFTER_TOMORROW,
                        TOMORROW,
                                    NOW_PLUS_60_MINS,
                                    NOW,
                                    NOW_MINUS_60_MINS,
                                    YESTERDAY,
                                    TWO_DAYS_AGO);
    }

    @Test
    public void findAuditHistory_filtersByDate() {
        List<AuditEventType> eventTypes = Arrays.asList(INCOME_PROVING_FINANCIAL_STATUS_RESPONSE);
        final Iterable<AuditEntry> all = repository.findAuditHistory(YESTERDAY, eventTypes, Pageable.unpaged());

        assertThat(all)
                .extracting("timestamp")
                .containsExactly(TWO_DAYS_AGO, YESTERDAY);

    }

    @Test
    public void findAuditHistory_ordersByDateAscending() {
        repository.save(createAudit(TWO_DAYS_AGO));
        repository.save(createAudit(NOW));
        repository.save(createAudit(TWO_DAYS_AGO));

        List<AuditEventType> eventTypes = Arrays.asList(INCOME_PROVING_FINANCIAL_STATUS_RESPONSE);
        final Iterable<AuditEntry> all = repository.findAuditHistory(YESTERDAY, eventTypes, Pageable.unpaged());

        assertThat(all)
                .extracting("timestamp")
                .containsExactly(TWO_DAYS_AGO, TWO_DAYS_AGO, TWO_DAYS_AGO, YESTERDAY);
    }

    @Test
    public void findAuditHistory_filtersByEventType() {
        List<AuditEventType> eventTypes = Arrays.asList(INCOME_PROVING_FINANCIAL_STATUS_REQUEST);
        final Iterable<AuditEntry> all = repository.findAuditHistory(YESTERDAY, eventTypes, Pageable.unpaged());

        assertThat(all).size().isEqualTo(0);
    }

    @Test
    public void findAuditHistory_pageable_isPaged() {
        List<AuditEventType> eventTypes = singletonList(INCOME_PROVING_FINANCIAL_STATUS_RESPONSE);
        Pageable pagination = PageRequest.of(0, 2);

        Iterable<AuditEntry> firstPage = repository.findAuditHistory(DAY_AFTER_TOMORROW, eventTypes, pagination);

        assertThat(firstPage)
                .extracting("timestamp")
                .containsExactly(TWO_DAYS_AGO, YESTERDAY);

        pagination = pagination.next();
        Iterable<AuditEntry> secondPage = repository.findAuditHistory(DAY_AFTER_TOMORROW, eventTypes, pagination);
        assertThat(secondPage)
                .extracting("timestamp")
                .containsExactly(NOW_MINUS_60_MINS, NOW);

        pagination = pagination.next();
        Iterable<AuditEntry> thirdPage = repository.findAuditHistory(DAY_AFTER_TOMORROW, eventTypes, pagination);
        assertThat(thirdPage)
                .extracting("timestamp")
                .containsExactly(NOW_PLUS_60_MINS, TOMORROW);

        pagination = pagination.next();
        Iterable<AuditEntry> fourthPage = repository.findAuditHistory(DAY_AFTER_TOMORROW, eventTypes, pagination);
        assertThat(fourthPage)
                .extracting("timestamp")
                .containsExactly(DAY_AFTER_TOMORROW);

        assertThat(repository.findAuditHistory(DAY_AFTER_TOMORROW, eventTypes, pagination.next()))
                .isEmpty();
    }

    @Test
    public void findAuditHistoryPageable_filtersByEventType() {
        List<AuditEventType> eventTypes = singletonList(INCOME_PROVING_FINANCIAL_STATUS_REQUEST);
        final Iterable<AuditEntry> all = repository.findAuditHistory(DAY_AFTER_TOMORROW, eventTypes,
                PageRequest.of(0, Integer.MAX_VALUE));

        assertThat(all).size().isEqualTo(0);
    }

    @Test
    public void shouldDeleteSingleCorrelationId() {
        repository.deleteAllCorrelationIds(Arrays.asList(UUID));
        final Iterable<AuditEntry> all = repository.findAll();
        assertThat(all).size().isEqualTo(0);
    }

    @Test
    public void shouldDeleteMultipleCorrelationId() {
        repository.save(createAuditWithCorrelationId(LocalDateTime.now(), "some_user", "corr id 2"));
        repository.deleteAllCorrelationIds(Arrays.asList(UUID, "corr id 2"));
        final Iterable<AuditEntry> all = repository.findAll();
        assertThat(all).size().isEqualTo(0);
    }

    @Test
    public void shouldNotDeleteMissingCorrelationId() {
        repository.deleteAllCorrelationIds(Arrays.asList("does_not_exist"));
        final Iterable<AuditEntry> all = repository.findAll();
        assertThat(all).size().isEqualTo(7);
    }

    @Test
    public void findArchivedResults_noResults_nothing() {
        List<AuditEntry> results = repository.findArchivedResults(LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());

        assertThat(results).isEmpty();
    }

    @Test
    public void findArchivedResults_singleResult_isReturned() {
        AuditEntry archivedResult = createArchivedResult(LocalDate.now());
        repository.save(archivedResult);

        List<AuditEntry> results = repository.findArchivedResults(LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());

        assertThat(results).containsExactly(archivedResult);
    }

    @Test
    public void findArchivedResults_multipleResults_correctDatesReturned() {
        AuditEntry archivedResult1 = createArchivedResult(LocalDate.now());
        AuditEntry archivedResult2 = createArchivedResult(LocalDate.now().minusDays(1));
        AuditEntry archivedResult3 = createArchivedResult(LocalDate.now().minusDays(2));
        repository.save(archivedResult1);
        repository.save(archivedResult2);
        repository.save(archivedResult3);

        List<AuditEntry> results = repository.findArchivedResults(LocalDate.now().minusDays(1).atStartOfDay(), LocalDate.now().atStartOfDay());

        assertThat(results).containsExactly(archivedResult2);
    }

    @Test
    public void getAllCorrelationIds_givenEventType_returnCorrelationIds() {
        String correlationId = "some correlation id";
        repository.save(createAudit(correlationId, INCOME_PROVING_FINANCIAL_STATUS_RESPONSE));

        List<String> correlationIds = repository.getAllCorrelationIds(singletonList(INCOME_PROVING_FINANCIAL_STATUS_RESPONSE));
        assertThat(correlationIds)
                .contains(correlationId);
    }

    @Test
    public void getAllCorrelationIds_givenEventType_returnOnlyCorrelationIdsForEventType() {
        String correlationId1 = "some correlation id";
        String correlationId2 = "some other correlation id";
        String correlationId3 = "yet some other correlation id";
        repository.save(createAudit(correlationId1, INCOME_PROVING_INCOME_CHECK_REQUEST));
        repository.save(createAudit(correlationId2, INCOME_PROVING_INCOME_CHECK_REQUEST));
        repository.save(createAudit(correlationId3, INCOME_PROVING_FINANCIAL_STATUS_RESPONSE));

        List<String> correlationIds = repository.getAllCorrelationIds(singletonList(INCOME_PROVING_INCOME_CHECK_REQUEST));
        assertThat(correlationIds)
                .containsOnly(correlationId1, correlationId2);
    }

    @Test
    public void getAllCorrelationIds_multipleEventTypes_returnCorrelationIdsForEventTypes() {
        String correlationId1 = "some correlation id";
        String correlationId2 = "some other correlation id";
        String correlationId3 = "yet some other correlation id";
        repository.save(createAudit(correlationId1, INCOME_PROVING_INCOME_CHECK_REQUEST));
        repository.save(createAudit(correlationId2, DWP_BENEFIT_REQUEST));
        repository.save(createAudit(correlationId3, INCOME_PROVING_FINANCIAL_STATUS_RESPONSE));

        List<String> correlationIds = repository.getAllCorrelationIds(Arrays.asList(INCOME_PROVING_INCOME_CHECK_REQUEST, DWP_BENEFIT_REQUEST));
        assertThat(correlationIds)
                .containsOnly(correlationId1, correlationId2);
    }

    @Test
    public void getAllCorrelationIds_multipleEntriesPerCorrelationId_returnDistinctIds() {
        String correlationId = "some correlation id";
        repository.save(createAudit(correlationId, INCOME_PROVING_INCOME_CHECK_REQUEST));
        repository.save(createAudit(correlationId, DWP_BENEFIT_REQUEST));

        assertThat(repository.getAllCorrelationIds(Arrays.asList(INCOME_PROVING_INCOME_CHECK_REQUEST, DWP_BENEFIT_REQUEST)))
                .hasSize(1)
                .contains(correlationId);
    }

    @Test
    public void getAllCorrelationIds_withToDate_returnNotAfterDate() {
        LocalDateTime someDateTime = LocalDateTime.of(2019, Month.AUGUST, 14, 10, 55);
        LocalDateTime beforeCutOff = someDateTime.minusSeconds(1);
        LocalDateTime afterCutOff = someDateTime.plusSeconds(1);

        repository.save(createAudit(someDateTime, "some expected correlation id", INCOME_PROVING_INCOME_CHECK_REQUEST));
        repository.save(createAudit(beforeCutOff, "some other expected correlation id", INCOME_PROVING_INCOME_CHECK_REQUEST));
        repository.save(createAudit(afterCutOff, "unexpected correlation id", INCOME_PROVING_INCOME_CHECK_REQUEST));

        List<String> returnedCorrelationIds = repository.getAllCorrelationIds(singletonList(INCOME_PROVING_INCOME_CHECK_REQUEST), someDateTime);
        assertThat(returnedCorrelationIds).containsExactlyInAnyOrder("some expected correlation id", "some other expected correlation id");
    }

    @Test
    public void getAllCorrelationIds_withToDate_noDuplicates() {
        LocalDateTime someDateTime = LocalDateTime.of(2019, Month.AUGUST, 14, 10, 55);
        LocalDateTime beforeCutOff = someDateTime.minusSeconds(1);
        LocalDateTime beforeCutOff2 = someDateTime.minusSeconds(2);

        repository.save(createAudit(beforeCutOff, "some correlation id", INCOME_PROVING_INCOME_CHECK_REQUEST));
        repository.save(createAudit(beforeCutOff2, "some correlation id", INCOME_PROVING_INCOME_CHECK_REQUEST));

        assertThat(repository.getAllCorrelationIds(singletonList(INCOME_PROVING_INCOME_CHECK_REQUEST), someDateTime))
                .containsExactly("some correlation id");
    }

    @Test
    public void findEntriesByCorrelationId_noEntriesForCorrelationId_emptyList() {
        List<AuditEventType> eventTypes = Arrays.asList(INCOME_PROVING_INCOME_CHECK_REQUEST, INCOME_PROVING_INCOME_CHECK_RESPONSE);
        assertThat(repository.findEntriesByCorrelationId("does not exist in Database", eventTypes))
                .isEmpty();
    }

    @Test
    public void findEntriesByCorrelationId_entriesExistForCorrelationId_returnAll() {
        List<AuditEventType> eventTypes = Arrays.asList(INCOME_PROVING_INCOME_CHECK_REQUEST, INCOME_PROVING_INCOME_CHECK_RESPONSE);
        String someCorrelationId = "some correlation id";
        List<AuditEntry> expectedEntries = Arrays.asList(
                createAudit(someCorrelationId, INCOME_PROVING_INCOME_CHECK_REQUEST),
                createAudit(someCorrelationId, INCOME_PROVING_INCOME_CHECK_RESPONSE)
        );

        expectedEntries.forEach(expectedEntry -> repository.save(expectedEntry));

        List<AuditEntry> actualEntries = repository.findEntriesByCorrelationId(someCorrelationId, eventTypes);

        assertThat(actualEntries).isEqualTo(expectedEntries);
    }

    @Test
    public void findEntriesByCorrelationId_entriesDifferentEventType_notReturned() {
        List<AuditEventType> eventTypes = Arrays.asList(INCOME_PROVING_INCOME_CHECK_REQUEST, INCOME_PROVING_INCOME_CHECK_RESPONSE);
        String someCorrelationId = "some correlation id";
        List<AuditEntry> expectedEntries = Arrays.asList(
                createAudit(someCorrelationId, DWP_BENEFIT_REQUEST),
                createAudit(someCorrelationId, HMRC_ACCESS_CODE_REQUEST)
        );

        expectedEntries.forEach(expectedEntry -> repository.save(expectedEntry));

        List<AuditEntry> actualEntries = repository.findEntriesByCorrelationId(someCorrelationId, eventTypes);

        assertThat(actualEntries).isEmpty();
    }

    @Test
    public void findAllIpsStatistics_noEntries_emptyList() {
        assertThat(repository.findAllIpsStatistics()).isEmpty();
    }

    @Test
    public void findAllIpsStatistics_ipsStatisticsEntries_allAsList() {
        List<AuditEntry> expectedEntries = Arrays.asList(
                createAudit("any correlation id", IPS_STATISTICS),
                createAudit("any correlation other id", IPS_STATISTICS));
        expectedEntries.forEach(expectedEntry-> repository.save(expectedEntry));

        List<AuditEntry> actualEntries = repository.findAllIpsStatistics();

        assertThat(actualEntries).isEqualTo(expectedEntries);
    }

    private AuditEntry createAudit(LocalDateTime timestamp) {
        return createAudit(timestamp, USER_ID);
    }

    private AuditEntry createAudit(LocalDateTime someDateTime, String correlationId, AuditEventType eventType) {
        return new AuditEntry(
                randomUUID().toString(),
                someDateTime,
                SESSION_ID,
                correlationId,
                USER_ID,
                DEPLOYMENT,
                NAMESPACE,
                eventType,
                DETAIL
        );
    }

    private AuditEntry createAudit(LocalDateTime timestamp, String userId) {
        return createAudit(timestamp, userId, DETAIL);
    }

    private AuditEntry createAudit(String correlationId, AuditEventType eventType) {
        return new AuditEntry(
                randomUUID().toString(),
                NOW,
                SESSION_ID,
                correlationId,
                USER_ID,
                DEPLOYMENT,
                NAMESPACE,
                eventType,
                DETAIL
        );
    }

    private AuditEntry createAudit(LocalDateTime timestamp, String userId, String detail) {
        return new AuditEntry(
                randomUUID().toString(),
                timestamp,
                SESSION_ID,
                UUID,
                userId,
                DEPLOYMENT,
                NAMESPACE,
                INCOME_PROVING_FINANCIAL_STATUS_RESPONSE,
                detail
        );
    }

    private AuditEntry createAuditWithCorrelationId(LocalDateTime timestamp, String userId, String correlationId) {
        return new AuditEntry(
                randomUUID().toString(),
                timestamp,
                SESSION_ID,
                correlationId,
                userId,
                DEPLOYMENT,
                NAMESPACE,
                INCOME_PROVING_FINANCIAL_STATUS_RESPONSE,
                DETAIL
        );
    }

    private AuditEntry createArchivedResult(LocalDate date) {
        return new AuditEntry(
                randomUUID().toString(),
                date.atStartOfDay(),
                "",
                randomUUID().toString(),
                "Audit Service",
                "",
                "",
                ARCHIVED_RESULTS,
                "{\"results\": {\"PASS\": 1}}"
        );
    }

}
