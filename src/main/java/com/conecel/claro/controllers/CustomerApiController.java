package com.conecel.claro.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;

import com.conecel.claro.dto.QuerySubscriberResponseMessage;
import com.conecel.claro.dto.Subscriber.SubscriberTypeEnum;
import com.conecel.claro.dto.SubscriberInfo;
import com.conecel.claro.exceptions.IntegrationException;
import com.conecel.claro.service.SubscriberService;

import io.swagger.annotations.ApiParam;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2019-03-22T12:39:21.861-05:00")
@Controller
public class CustomerApiController implements CustomerApi {
	
	@Autowired
	private SubscriberService subscriberService;

	public ResponseEntity<QuerySubscriberResponseMessage> querySubscriber(
			@ApiParam(value = "Indica el tipo de identificaciÃ³n del subscriptor que se estÃ¡ suministrando para consultar la informaciÃ³n del mismo. EJEMPLO: SERVICENUMBER, IMSI, IMEI, ICCID", 
				required = true) 
			@PathVariable("subscriberType") String subscriberType,
			@ApiParam(value = "IdentificaciÃ³n unÃ­voca del subscriptor que se desea consultar a travÃ©s de Ã©sta operaciÃ³n.", 
				required = true) 
			@PathVariable("subscriberId") String subscriberId) {
		SubscriberInfo info = new SubscriberInfo();
		QuerySubscriberResponseMessage response = new QuerySubscriberResponseMessage();
		try {
			SubscriberTypeEnum subscriberTypeEnum = SubscriberTypeEnum.fromValue(subscriberType);
			return new ResponseEntity<QuerySubscriberResponseMessage>(
					this.subscriberService.querySubscriberOrchestation(subscriberTypeEnum.toString(), subscriberId),
					HttpStatus.OK);
		} catch (IntegrationException e) {
			info.setCode(e.getCode());
			info.setMessage(e.getMessage());
			response.setSubscriberInfo(info);
			return new ResponseEntity<QuerySubscriberResponseMessage>(response, HttpStatus.INTERNAL_SERVER_ERROR);
			
		} catch (Exception e) {
			e.printStackTrace();
			info.setCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
			info.setMessage(String.format("Ocurrio un error general: %s", e.getMessage()));
			response.setSubscriberInfo(info);
			return new ResponseEntity<QuerySubscriberResponseMessage>(response, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

}
