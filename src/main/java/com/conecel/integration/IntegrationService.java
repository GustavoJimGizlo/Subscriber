package com.conecel.integration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.claro.micro.eis.grpc.Inputs;
import com.claro.micro.eis.grpc.InvokeRequest;
import com.claro.micro.eis.grpc.InvokeResponse;
import com.claro.micro.eis.grpc.InvokeXMLServiceGrpc;
import com.conecel.claro.dto.CommonHeaderRequest;
import com.conecel.claro.dto.Identification;
import com.conecel.claro.dto.Identification.TypeEnum;
import com.conecel.claro.dto.OperationInfo;
import com.conecel.claro.dto.QuerySubscriberResponseMessage;
import com.conecel.claro.dto.Subscriber;
import com.conecel.claro.dto.Subscriber.CustomerPartyRoleEnum;
import com.conecel.claro.dto.Subscriber.SubscriberTypeEnum;
import com.conecel.claro.dto.Subscriber.SubscriptionTypeEnum;
import com.conecel.claro.exceptions.IntegrationException;
import com.conecel.claro.dto.SubscriberInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.netty.channel.ConnectTimeoutException;
import io.swagger.configuration.Constants;

@Service
public class IntegrationService {

	private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);
	
	@Qualifier("restTemplate")
	@Autowired
	RestTemplate restTemplate;

	@Value("${url.subscriberInquiryOne}")
	private String urlSubscriberInquiryOne;

	@Value("${url.subscriberInquiryHW}")
	private String urlSubscriberInquiryHW;

	@Value("${grpc.ip.microeis}")
	private String ipMicroeis;

	@Value("${grpc.port.microeis}")
	private int portMicroeis;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	public IntegrationException integrationexception;

	/**
	 * Trae un Boleano si el Subscriber pertenece a Hw One
	 * 
	 * @return vadidateResult
	 */

	public boolean validateSubscriber(SubscriberTypeEnum subscriberType, String subscriberId) throws Exception {
		boolean validateResult = false;
		SubscriberInfo info = new SubscriberInfo();
		try {

			String subsType = subscriberType.toString();
			if (subscriberType.equals(SubscriberTypeEnum.SERVICENUMBER))
				subsType = "ServiceNumber";
			String pattern = "yyyy-MM-dd'T'HH:mm:ss";
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);

			JSONObject header = new JSONObject();
			header.put("channelId", "Subscriber");
			header.put("companyId", "Claro");
			header.put("consumerId", "");
			header.put("consumerProfileId", "");
			header.put("externalOperation", "querySubscriber");
			header.put("externalTransactionDate", simpleDateFormat.format(Calendar.getInstance().getTime()));

			JSONObject root = new JSONObject();
			root.put("header", header);
			root.put("subscriberIdentifierId", subscriberId);
			root.put("subscriberIdentifierType", subsType);
			root.put("validate", "Migration");

			String respuesta = invokePost(urlSubscriberInquiryOne.toString(), root.toString());
			JsonNode rootNode = objectMapper.readTree(respuesta);
			JsonNode validate = rootNode.path("validateSubscriber");
			validateResult = validate.asBoolean();

		} catch(IntegrationException e) {
			throw IntegrationException.builder().code(e.getCode()).message(e.getMessage()).technicalMessage(e.getMessage()).build();
		}
		return validateResult;
	}

	/**
	 * MUESTRA INFORMACION VALIDADA EN ONE HW POR VALIDATE SUBSCRIBER
	 * 
	 * @return response
	 */
	public QuerySubscriberResponseMessage suscriberInquiryHW(SubscriberTypeEnum subscriberType, String subscriberId)
			throws Exception {
		QuerySubscriberResponseMessage response = new QuerySubscriberResponseMessage();
		CommonHeaderRequest commonHeaderRequest = new CommonHeaderRequest();
		Subscriber subscriber = new Subscriber();
		SubscriberInfo info = new SubscriberInfo();
		try {
			String subsType = subscriberType.toString();

			if (subscriberType.equals(SubscriberTypeEnum.SERVICENUMBER))

				subsType = "serviceNumber";
			Map<String, String> params = new HashMap<>();
			params.put(subsType, subscriberId);

			Map<String, String> cabeceras = new HashMap<>();
			cabeceras.put("X-OperatorId", "101");
			cabeceras.put("X-RegionId", "101");
			cabeceras.put("X-OriginChannel", "69");
			cabeceras.put("X-BEId", "101");

			String respuesta = invokeGet(urlSubscriberInquiryHW.toString(), params, cabeceras);
			JsonNode rootNode = objectMapper.readTree(respuesta);

			OperationInfo oper = new OperationInfo();
			String pattern = "yyyyMMddHHmmss.SSS";
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
			String pattern2 = "yyyy-MM-dd'T'HH:mm:ss.SSS";
			SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat(pattern2);
			oper.setTransactionDate(simpleDateFormat2.format(Calendar.getInstance().getTime()));
			oper.setOperationId(Constants.OPERATIONID);
			oper.setTransactionId(simpleDateFormat.format(Calendar.getInstance().getTime()));
			commonHeaderRequest.setOperationInfo(oper);

			info.setCode(Constants.CODE);
			info.setMessage(Constants.MESSAGE);

			subscriber.setName(rootNode.path("userCustomer").path("name").path("firstName").asText());
			subscriber.setLastName(rootNode.path("userCustomer").path("name").path("lastName").asText());
			subscriber.setStatus(rootNode.path("subscriber").path("subscriberInformation").path("status").asText());
			subscriber.setSubscriptionId(subscriberId);
			Identification id = new Identification();
			id.setValue(rootNode.path("userCustomer").path("id").asText());
			id.setType(TypeEnum.CUSTOMERID);
			subscriber.setIdentification(id);
			log.info(rootNode.path("subscriber").path("subscriberInformation").path("paymentType").asText());
			subscriber.setSubscriptionType(SubscriptionTypeEnum
					.fromValue(rootNode.path("subscriber").path("subscriberInformation").path("paymentType").asText()));

			if (rootNode.path("subscriberOwnership").path("ownerCustomer").path("individual") != null) {
				subscriber.setCustomerPartyRole(CustomerPartyRoleEnum.INDIVIDUAL);
			} else {
				subscriber.setCustomerPartyRole(CustomerPartyRoleEnum.ORGANIZATION);
			}
			info.setSubscriber(subscriber);
			response.setSubscriberInfo(info);

		} 
		catch (IntegrationException e) {
			log.error("Error suscriberInquiryHWr");
			info.setCode(e.getCode());
			info.setMessage(e.getMessage());
			

			response.setSubscriberInfo(info);
		}

		response.setCommonHeaderRequest(commonHeaderRequest);
		return response;
	}

	/**
	 * Consumo por Axis por medio de MicroEis
	 * 
	 * @return querySubscriberResponseMessage
	 */
	@SuppressWarnings("deprecation")
	public QuerySubscriberResponseMessage invokeGrpc(SubscriberTypeEnum subscriberType, String subscriberId)
			throws Exception {
		SubscriberInfo info = new SubscriberInfo();
		QuerySubscriberResponseMessage querySubscriberResponseMessage = new QuerySubscriberResponseMessage();
		CommonHeaderRequest commonHeaderRequest = new CommonHeaderRequest();
		OperationInfo oper = new OperationInfo();
		String pattern = "yyyyMMddHHmmss.SSS";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		String pattern2 = "yyyy-MM-dd'T'HH:mm:ss.SSS";
		SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat(pattern2);
		oper.setTransactionDate(simpleDateFormat2.format(Calendar.getInstance().getTime()));
		oper.setOperationId(Constants.OPERATIONID);
		oper.setTransactionId(simpleDateFormat.format(Calendar.getInstance().getTime()));
		commonHeaderRequest.setOperationInfo(oper);
		querySubscriberResponseMessage.setCommonHeaderRequest(commonHeaderRequest);
		try {
			ManagedChannel channel = ManagedChannelBuilder.forAddress(ipMicroeis, portMicroeis).usePlaintext(true).build();
			InvokeXMLServiceGrpc.InvokeXMLServiceBlockingStub stub = InvokeXMLServiceGrpc.newBlockingStub(channel);
			Inputs input = Inputs.newBuilder().setKey("subscriberId").setValue(subscriberId).build();	
			InvokeResponse response = stub.invokeXML(InvokeRequest.newBuilder().setInformationService("Consulta.Telefono2")
					.setSource("msaEis/axisd").addInputs(input).build());
			channel.shutdown();

		Subscriber subscriber = null;
		
			if (response != null) {
				if (response.getResponseCount() > 0 && response.getResponse(0) != null) {
					subscriber = new Subscriber();
					Identification identification = new Identification();
					subscriber.setIdentification(identification);
					String lista[] = response.getResponse(0).split(",");
					info.setCode(Constants.CODE);
					info.setMessage(Constants.MESSAGE);

					for (int i = 0; i < lista.length; i++) {
						String key = lista[i].split("=")[0];
						String value = lista[i].split("=")[1];
						subscriber.setSubscriptionId(subscriberId);
						switch (key.trim()) {
						case "{FIRSTNAME":
							subscriber.setName(value);
							break;
						case "LASTNAME":
							subscriber.setLastName(value);
							break;
						case "SUBSCRIPTIONID":
							subscriber.setSubscriptionId(value);
							break;
						case "STATUS":
							subscriber.setStatus(value);
							break;
						case "SUBSCRIPTIONTYPE":
							subscriber.setSubscriptionType(SubscriptionTypeEnum.fromValue(value));
							break;
						case "CUSTOMERPARTYROLE":
							subscriber.setCustomerPartyRole(CustomerPartyRoleEnum.fromValue(value));
							break;
						case "IDENTIFICATION":
							identification.setValue(value);
							break;
						case "IDENTIFICATIONTYPE":
							identification.setType(TypeEnum.fromValue(value));
							break;

						default:
							break;
						}

					}

				} else {
					info.setCode(HttpStatus.NOT_FOUND.value());
					info.setMessage("SUBSCRIPTOR NO EXISTE");
					log.info("SUBSCRIPTOR NO EXISTE");
				}
				
				info.setSubscriber(subscriber);
				querySubscriberResponseMessage.setSubscriberInfo(info);
				
				
			}
		}catch (Exception e) {
			int code = 500;
			String message = "Error al conectar con el Servidor (StatusRuntimeException)";
			log.info("Error al conectar con el Servidor con GRPC");
			throw IntegrationException.builder().code(code).message(message).technicalMessage(e.getMessage()).build();
		}
			
		return querySubscriberResponseMessage;
		}
		
		
	/**
	 * Escribe por medio de un Post en ValidateSubscriber en HW en Json para validar
	 * si existe el Subscriber
	 * 
	 * @return respuesta
	 */
	private String invokePost(String url, String request) throws ConnectTimeoutException, Exception {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);

			HttpEntity<String> requestEntity = new HttpEntity<>(request, headers);
			ResponseEntity<String> respuesta = restTemplate.postForEntity(url, requestEntity, String.class);
			return respuesta.getBody();

		} catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
			String message;
			int code;
			if (e instanceof HttpClientErrorException) {
				code = 11;
				message = "Error al consumir el servicio Post (HttpClientErrorException)";
				log.error("Error al consumir el servicio Post (HttpClientErrorException)");
				
			} else if (e instanceof HttpServerErrorException) {
				code = 12;
				message = "Error al consumir el servicio Post (HttpServerErrorException)";
				log.error("Error al consumir el servicio Post (HttpServerErrorException)");
				
			} else if (e instanceof ResourceAccessException) {
				code = 13;
				message = "Error al consumir el servicio Post (ResourceAccessException)";
				log.error("Error al consumir el servicio Post (ResourceAccessException)");
				
			} else {
				code = 10;
				message = "Error al consumir el servicio Post (ResourceAccessException)";
				log.error("Error al consumir el servicio Post (ResourceAccessException)");
			}
			throw IntegrationException.builder().code(code).message(message).technicalMessage(e.getMessage()).build();
		}

	}

	/**
	 * Validado por medio de un Boleano en el invokePost, trae los datos del
	 * Subscriber al body
	 * 
	 * @return respuesta
	 */
	private String invokeGet(String url, Map<String, String> params, Map<String, String> cabeceras) throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		try {

			for (String key : cabeceras.keySet()) {
				String value = cabeceras.get(key);
				headers.set(key, value);
			}
			UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
			for (String key : params.keySet()) {
				String value = params.get(key);
				builder.queryParam(key, value);
			}
			HttpEntity<String> requestEntity = new HttpEntity<>(headers);
			ResponseEntity<String> respuesta = restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.GET,
					requestEntity, String.class);

			return respuesta.getBody();

		} catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
			String message;
			int code;
			if (e instanceof HttpClientErrorException) {
				code = 11;
				message = "Error al consumir el servicio Get (HttpClientErrorException)";
				log.error("Error al consumir el servicio Get (HttpClientErrorException)");
				
			} else if (e instanceof HttpServerErrorException) {
				code = 12;
				message = "Error al consumir el servicio Get (HttpServerErrorException)";
				log.error("Error al consumir el servicio Get (HttpServerErrorException)");
				
			} else if (e instanceof ResourceAccessException) {
				code = 13;
				message = "Error al consumir el servicio Get (ResourceAccessException)";
				log.error("Error al consumir el servicio Get (ResourceAccessException)");
				
			} else {
				code = 10;
				message = "Error al consumir el servicio Get (ResourceAccessException)";
				log.error("Error al consumir el servicio Get (ResourceAccessException)");
			}
			throw IntegrationException.builder().code(code).message(message).technicalMessage(e.getMessage()).build();
		}

	}

}
