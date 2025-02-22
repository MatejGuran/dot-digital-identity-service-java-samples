package com.innovatrics.integrationsamples.onboarding;

import com.innovatrics.dot.integrationsamples.disapi.ApiClient;
import com.innovatrics.dot.integrationsamples.disapi.ApiException;
import com.innovatrics.dot.integrationsamples.disapi.model.*;
import com.innovatrics.integrationsamples.Configuration;
import com.innovatrics.integrationsamples.faceoperations.WearablesCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/**
 * This example demonstrates comprehensive usage of customer onboarding API. Face and document images are used to create
 * a customer entity. This example can serve as a starting point when integrating with DIS for onboarding use cases.
 */
public class CustomerOnboarding {
    private static final Logger LOG = LoggerFactory.getLogger(CustomerOnboarding.class);

    public static void main(String[] args) throws IOException, URISyntaxException {
        final Configuration configuration = new Configuration();
        final ApiClient client = new ApiClient().setBasePath(configuration.DOT_IDENTITY_SERVICE_URL);
        client.setBearerToken(configuration.DOT_AUTHENTICATION_TOKEN);
        final CustomerOnboardingApi customerOnboardingApi = new CustomerOnboardingApi(client);
        final FaceOperationsApi faceApi = new FaceOperationsApi(client);

        try {
            final CreateCustomerResponse customerResponse = customerOnboardingApi.createCustomer();
            String customerId = customerResponse.getId();
            LOG.info("Customer created with id: " + customerId);

            CreateSelfieResponse selfieResponse = customerOnboardingApi.createSelfie(customerId, new CreateSelfieRequest().image(new Image().data(getDetectionImage())));
            CreateSelfieResponse.ErrorCodeEnum selfieError = selfieResponse.getErrorCode();
            if (selfieError != null) {
                LOG.error(selfieError.getValue());
                return;
            }
            LOG.info("Face detected on selfie.");

            customerOnboardingApi.createLiveness(customerId);
            CreateCustomerLivenessSelfieResponse livenessSelfieResponse = customerOnboardingApi.createLivenessSelfie(customerId, new CreateCustomerLivenessSelfieRequest().image(new Image().data(getDetectionImage())).assertion(CreateCustomerLivenessSelfieRequest.AssertionEnum.NONE));
            if (livenessSelfieResponse.getWarnings() != null) {
                for (CreateCustomerLivenessSelfieResponse.WarningsEnum warning : livenessSelfieResponse.getWarnings()) {
                    LOG.warn("Liveness selfie warning: " + warning.getValue());
                }
                LOG.error("Liveness selfie does not meet quality required for accurate passive liveness evaluation.");
                return;
            }
            CreateCustomerLivenessSelfieResponse.ErrorCodeEnum livenessSelfieError = livenessSelfieResponse.getErrorCode();
            if (livenessSelfieError != null) {
                LOG.error(livenessSelfieError.getValue());
                return;
            }
            final EvaluateCustomerLivenessResponse passiveLivenessResponse = customerOnboardingApi.evaluateLiveness(customerId, new EvaluateCustomerLivenessRequest().type(EvaluateCustomerLivenessRequest.TypeEnum.PASSIVE_LIVENESS));
            EvaluateCustomerLivenessResponse.ErrorCodeEnum passiveLivenessError = passiveLivenessResponse.getErrorCode();
            if (passiveLivenessError != null) {
                LOG.error(passiveLivenessError.getValue());
                return;
            }
            LOG.info("Passive liveness score: " + passiveLivenessResponse.getScore());

            customerOnboardingApi.createDocument(customerId, new CreateDocumentRequest().advice(new DocumentAdvice().classification(new DocumentClassificationAdvice().addCountriesItem("INO"))));
            CreateDocumentPageResponse createDocumentResponseFront = customerOnboardingApi.createDocumentPage(customerId, new CreateDocumentPageRequest().image(new Image().data(getDocumentImage("document-front"))));
            CreateDocumentPageResponse.ErrorCodeEnum documentFrontError = createDocumentResponseFront.getErrorCode();
            if (documentFrontError != null) {
                LOG.error(documentFrontError.getValue());
                return;
            }
            LOG.info("Document classified: " + createDocumentResponseFront.getDocumentType().getType() + " page type: " + createDocumentResponseFront.getPageType());
            CreateDocumentPageResponse createDocumentResponseBack = customerOnboardingApi.createDocumentPage(customerId, new CreateDocumentPageRequest().image(new Image().data(getDocumentImage("document-back"))));
            CreateDocumentPageResponse.ErrorCodeEnum documentBackError = createDocumentResponseBack.getErrorCode();
            if (documentBackError != null) {
                LOG.error(documentBackError.getValue());
                return;
            }
            LOG.info("Document classified: " + createDocumentResponseBack.getDocumentType().getType() + " page type: " + createDocumentResponseBack.getPageType());

            Customer customer = customerOnboardingApi.getCustomer(customerId).getCustomer();
            if (customer == null || customer.getDocument() == null || customer.getDocument().getLinks().getPortrait() == null) {
                LOG.error("Face not found on document portrait");
                return;
            }

            LOG.info("Customer: " + customer);

            ImageCrop frontPage = customerOnboardingApi.documentPageCrop(customerId, "front", null, null);
            saveImage(frontPage.getData(), "document-front.png");

            ImageCrop backPage = customerOnboardingApi.documentPageCrop(customerId, "back", null, null);
            saveImage(backPage.getData(), "document-back.png");

            ImageCrop documentPortrait = customerOnboardingApi.documentPortrait(customerId, null, null);
            saveImage(documentPortrait.getData(), "portrait.png");

            //get customers age from document
            int customerAge = Integer.parseInt(customerOnboardingApi.getCustomer(customerId).getCustomer().getAge().getVisualZone()); //Need to get INT value of age from document, but not sure about syntax of this call
            
            //check if face mask
            String faceId;
            try {
                faceId = faceApi.detect(new CreateFaceRequest().image(new Image().url(configuration.EXAMPLE_IMAGE_URL))).getId();
                LOG.info("Face detected with id: " + faceId);
            } catch (ApiException exception) {
                LOG.error("Request to server failed with code: " + exception.getCode() + " and response: " + exception.getResponseBody());
                return;
            }

            boolean isWearingFaceMask = checkFaceMask(configuration, faceApi, faceId);
            

            // Check customers eligibility based on age and face mask status
            if (customerAge >= 18 && !isWearingFaceMask) {
                LOG.info("Customer is eligible");
            } else {
                LOG.info("Customer is not eligible");
            }
            
            LOG.info("Deleting customer with id: " + customerId);
            customerOnboardingApi.deleteCustomer(customerId);
        } catch (ApiException exception) {
            LOG.error("Request to server failed with code: " + exception.getCode() + " and response: " + exception.getResponseBody());
        }
    }

    private static byte[] getDocumentImage(String imageId) throws URISyntaxException, IOException {
        final URL resource = CustomerOnboarding.class.getClassLoader().getResource("images/documents/" + imageId + ".jpeg");
        return new FileInputStream(Path.of(resource.toURI()).toFile()).readAllBytes();
    }

    private static byte[] getDetectionImage() throws URISyntaxException, IOException {
        final URL resource = CustomerOnboarding.class.getClassLoader().getResource("images/faces/face.jpeg");
        return new FileInputStream(Path.of(resource.toURI()).toFile()).readAllBytes();
    }

    private static void saveImage(byte[] image, String fileName) throws IOException {
        prepareOutputDirectory();

        ByteArrayInputStream bis = new ByteArrayInputStream(image);
        BufferedImage bImage2 = ImageIO.read(bis);
        ImageIO.write(bImage2, "png", new File("onboardingImages/" + fileName));
    }

    private static void prepareOutputDirectory() {
        File resultDirectory = new File("onboardingImages");
        if (!(resultDirectory.exists() && resultDirectory.isDirectory())) {
            resultDirectory.mkdir();
        }
    }  

        private static boolean checkFaceMask(Configuration configuration, FaceOperationsApi faceApi, String faceId) throws ApiException {
        try {
            FaceMaskResponse faceMaskResponse = faceApi.checkFaceMask(faceId);
            boolean maskDetected = faceMaskResponse.getScore() > configuration.WEARABLES_FACE_MASK_THRESHOLD;
            LOG.info("Face mask detected on face image: " + maskDetected);
            return maskDetected;  //not sure about syntax, but need to return this boolean value
        } catch (ApiException exception) {
            LOG.error("Mask detection call failed. Make sure balanced or accurate detection mode is enabled");
            throw exception;
        }
    }
}
