package com.innovatrics.integrationsamples.onboarding.document;

import com.innovatrics.dot.integrationsamples.disapi.ApiClient;
import com.innovatrics.dot.integrationsamples.disapi.ApiException;
import com.innovatrics.dot.integrationsamples.disapi.model.*;
import com.innovatrics.integrationsamples.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;


public class DocumentOcrWithAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentOcrWithAdvice.class);
    private static final String ID_TYPE = "identity-card";
    private static final String COUNTRY = "INO";

    public static void main(String[] args) throws IOException, URISyntaxException {
        final Configuration configuration = new Configuration();
        final ApiClient client = new ApiClient().setBasePath(configuration.DOT_IDENTITY_SERVICE_URL);
        client.setBearerToken(configuration.DOT_AUTHENTICATION_TOKEN);
        final CustomerOnboardingApi customerOnboardingApi = new CustomerOnboardingApi(client);

        try {
            final CreateCustomerResponse customerResponse = customerOnboardingApi.createCustomer();
            String customerId = customerResponse.getId();
            LOG.info("Customer created with id: " + customerId);

            customerOnboardingApi.createDocument(customerId, new CreateDocumentRequest().advice(new DocumentAdvice().classification(new DocumentClassificationAdvice().addCountriesItem(COUNTRY).addTypesItem(ID_TYPE))));
            CreateDocumentPageResponse createDocumentResponse = customerOnboardingApi.createDocumentPage(customerId, new CreateDocumentPageRequest().image(new Image().data(getDocumentImage("document-front"))));
            CreateDocumentPageResponse.ErrorCodeEnum documentError = createDocumentResponse.getErrorCode();
            if (documentError != null) {
                LOG.error(documentError.getValue());
                return;
            }

            Customer customer = customerOnboardingApi.getCustomer(customerId).getCustomer();
            LOG.info("Customer: " + customer);

            LOG.info("Deleting customer with id: " + customerId);
            customerOnboardingApi.deleteCustomer(customerId);
        } catch (ApiException exception) {
            LOG.error("Request to server failed with code: " + exception.getCode() + " and response: " + exception.getResponseBody());
        }
    }

    private static byte[] getDocumentImage(String imageId) throws URISyntaxException, IOException {
        final URL resource = DocumentOcrWithAdvice.class.getClassLoader().getResource("images/documents/" + imageId + ".jpeg");
        return new FileInputStream(Path.of(resource.toURI()).toFile()).readAllBytes();
    }
}
