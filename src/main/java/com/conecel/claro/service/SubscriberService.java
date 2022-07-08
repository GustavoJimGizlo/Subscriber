package com.conecel.claro.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.conecel.claro.dto.QuerySubscriberResponseMessage;
import com.conecel.claro.dto.Subscriber.SubscriberTypeEnum;
import com.conecel.claro.dto.SubscriberInfo;
import com.conecel.claro.exceptions.IntegrationException;
import com.conecel.integration.IntegrationService;

import io.grpc.StatusRuntimeException;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class SubscriberService {

	@Autowired
	private IntegrationService integrationService;

	@Cacheable(value = "querysubscriber" , key ="{#p0 , #p1}" , cacheManager = "cacheManager" , unless ="#result == null")    
	public QuerySubscriberResponseMessage querySubscriberOrchestation(String subscriberType, String subscriberId)
			throws Exception {

		QuerySubscriberResponseMessage querySubscriberResponseMessage = new QuerySubscriberResponseMessage();
		try {

		 boolean validateSubscriber = integrationService.validateSubscriber(SubscriberTypeEnum.valueOf(subscriberType), subscriberId);
			log.debug("VALIDATE SUBSCRIBER - " + validateSubscriber);
			log.info("VALIDATE SUBSCRIBER" + subscriberId);
			if (validateSubscriber != false) {
				try {
					log.debug("FUE A CONSULTAR EL QUERY EN HW ");
					querySubscriberResponseMessage = integrationService
							.suscriberInquiryHW(SubscriberTypeEnum.valueOf(subscriberType), subscriberId);

				} catch (IntegrationException e) {
					throw IntegrationException.builder().code(e.getCode()).message(e.getMessage()).technicalMessage(e.getMessage()).build();
				}

			} else {

					log.debug("FUE A CONSULTAR EL QUERY EN LEGACY ");
					querySubscriberResponseMessage = integrationService
							.invokeGrpc(SubscriberTypeEnum.valueOf(subscriberType), subscriberId);
			}

		} catch (IntegrationException e) {
			throw IntegrationException.builder().code(e.getCode()).message(e.getMessage()).technicalMessage(e.getMessage()).build();
		}

		return querySubscriberResponseMessage;

	}
}
