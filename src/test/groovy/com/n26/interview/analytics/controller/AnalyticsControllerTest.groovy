package com.n26.interview.analytics.controller

import com.n26.interview.analytics.AnalyticsApplication
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

import java.sql.Timestamp
import java.time.LocalDateTime

@ContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = [AnalyticsApplication.class])
class AnalyticsControllerTest extends Specification {

    def underTest = new AnalyticsController()
    def client = new RESTClient("http://localhost:8080/analytics/")

    def "should accept timestamp value not older than max past time"() {
        expect:
        underTest.isTimestampValid(LocalDateTime.now().minusSeconds(seconds))

        where:
        seconds | _
        AnalyticsController.MAX_SECONDS_PAST-10 | _
        AnalyticsController.MAX_SECONDS_PAST-5 | _
        AnalyticsController.MAX_SECONDS_PAST | _
    }

    def "should not accept timestamp value older than max past time"() {
        expect:
        !underTest.isTimestampValid(LocalDateTime.now().minusSeconds(seconds))

        where:
        seconds | _
        AnalyticsController.MAX_SECONDS_PAST+1 | _
        AnalyticsController.MAX_SECONDS_PAST+5 | _
        AnalyticsController.MAX_SECONDS_PAST+10 | _
    }

    def "should return error if timestamp is old"() {
        when:
        def response = client.post(path: 'transactions', requestContentType : ContentType.JSON,
                headers : ['Content-Type' : "application/json"],
                body    : [ 'amount'    :   1.2D,
                            'timestamp' :  Timestamp.valueOf(LocalDateTime.now().minusSeconds(AnalyticsController.MAX_SECONDS_PAST+1)).getTime().toString()])

        then:
        response.responseData == HttpStatus.NO_CONTENT.value()
    }

    def "test"() {
        given:
        LocalDateTime now = LocalDateTime.now()

        when:
        underTest.postTransaction(new TransactionRequest(amount: 1.1D, timestamp: Timestamp.valueOf(now.minusSeconds(2)).getTime()))
        StatisticsResponse response = underTest.getStatistics()

        then:
        response.average == 1.1D
        response.sum == 1.1D
        response.max == 1.1D
        response.min == 1.1D
        response.count == 1

        when:
        underTest.postTransaction(new TransactionRequest(amount: 3.2D, timestamp: Timestamp.valueOf(now.minusSeconds(1)).getTime()))
        response = underTest.getStatistics()

        then:
        response.average == 2.15D
        response.sum == 4.3D
        response.max == 3.2D
        response.min == 1.1D
        response.count == 2

        when:
        underTest.postTransaction(new TransactionRequest(amount: 0.7D, timestamp: Timestamp.valueOf(now).getTime()))
        response = underTest.getStatistics()

        then:
        response.average == 1.67D
        response.sum == 5.0D
        response.max == 3.2D
        response.min == 0.7D
        response.count == 3

        when:
        Thread.sleep(AnalyticsController.MAX_SECONDS_PAST*1000)
        response = underTest.getStatistics()

        then:
        response.average == 0.0D
        response.sum == 0.0D
        response.max == 0.0D
        response.min == 0.0D
        response.count == 0
    }
}
