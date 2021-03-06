/*
 * Copyright (C) 2021, Logical Clocks AB. All rights reserved
 */

package io.hops.hopsworks.common.serving.inference;

import io.hops.common.Pair;
import io.hops.hopsworks.persistence.entity.serving.Serving;
import io.hops.hopsworks.common.integrations.LocalhostStereotype;
import io.hops.hopsworks.exceptions.InferenceException;
import io.hops.hopsworks.persistence.entity.serving.ModelServer;
import io.hops.hopsworks.restutils.RESTCodes;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;

import javax.ejb.ConcurrencyManagement;
import javax.ejb.ConcurrencyManagementType;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;

import static io.hops.hopsworks.common.serving.LocalhostServingController.CID_STOPPED;

/**
 * Localhost Inference Controller
 *
 * Sends inference requests to a local serving server to get a prediction response
 */
@LocalhostStereotype
@Singleton
@TransactionAttribute(TransactionAttributeType.NEVER)
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class LocalhostInferenceController implements ServingInferenceController {
  
  @EJB
  private InferenceHttpClient inferenceHttpClient;
  @EJB
  private LocalhostTfInferenceUtils localhostTfInferenceUtils;
  @EJB
  private LocalhostSkLearnInferenceUtils localhostSkLearnInferenceUtils;
  
  /**
   * Local inference. Sends a JSON request to the REST API of a local serving server
   *
   * @param serving the serving instance to send the request to
   * @param modelVersion the version of the serving
   * @param verb the type of inference request (predict, regress, classify)
   * @param inferenceRequestJson the JSON payload of the inference request
   * @return the inference result returned by the serving server
   * @throws InferenceException
   */
  public Pair<Integer, String> infer(Serving serving, Integer modelVersion, String verb, String inferenceRequestJson)
    throws InferenceException {
  
    if (serving.getCid().equals(CID_STOPPED)) {
      throw new InferenceException(RESTCodes.InferenceErrorCode.SERVING_NOT_RUNNING, Level.FINE);
    }
    
    String path;
    if (serving.getModelServer() == ModelServer.TENSORFLOW_SERVING) {
      path = localhostTfInferenceUtils.getPath(serving.getName(), modelVersion, verb);
    } else if (serving.getModelServer() == ModelServer.FLASK) {
      path = localhostSkLearnInferenceUtils.getPath(verb);
    } else {
      throw new UnsupportedOperationException("Model server not supported as local serving");
    }
    
    // Send request
    try {
      URI uri = new URIBuilder()
        .setScheme("http")
        .setHost("localhost")
        .setPort(serving.getLocalPort())
        .setPath(path)
        .build();
      
      HttpPost request = new HttpPost(uri);
      request.addHeader("content-type", "application/json; charset=utf-8");
      request.setEntity(new StringEntity(inferenceRequestJson));
      HttpContext context = HttpClientContext.create();
      CloseableHttpResponse response = inferenceHttpClient.execute(request, context);
      return inferenceHttpClient.handleInferenceResponse(response);
    } catch (URISyntaxException e) {
      throw new InferenceException(RESTCodes.InferenceErrorCode.REQUEST_ERROR, Level.SEVERE, null, e.getMessage(), e);
    } catch (IOException e) {
      throw new InferenceException(RESTCodes.InferenceErrorCode.REQUEST_ERROR, Level.INFO, null, e.getMessage(), e);
    }
  }
}
