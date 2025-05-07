package org.component;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dowanload Documents by API and save to a location.
 * Post https://{{apiHost}}/integrations/api/v1/service/{{tenantID}}/{{formID}}/submission/{{submissionID}}/exportDocuments
 * Headers: subAppID: {{subAppID}}, clientAppID: {{clientAppID}}, requesterID: {{requesterID}}, jwtToken: {{jwtToken}}, A3token: {{A3token}}
 * Content-Type: application/json
 **/
@UriEndpoint(
        firstVersion = "1.0.0", // The first version this component was created
        scheme = "get-doc-attach", // The scheme used in the Camel route URI
        title = "Get doc Attach", // Title of the component
        syntax = "get-doc-attach:name" // Syntax for endpoint URI
)
public class GetDocAttachEndpoint extends DefaultEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDocAttachEndpoint.class);


    public GetDocAttachEndpoint(String uri, GetDocAttachComponent getDocAttachComponent) {
        super(uri, getDocAttachComponent);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GetDocAttachProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return null;
    }


    @UriPath(description = "The name of the endpoint")
    @Metadata(required = true, defaultValue = "email-doc-attach")
    private String name;


    @UriParam(description = "API host, e.g., api.example.com")
    @Metadata(required = true, defaultValue = "api.example.com")
    private String apiHost;

    @UriParam(description = "Sub App ID")
    @Metadata(required = true, defaultValue = "subAppID")
    private String subAppID;

    @UriParam(description = "Client App ID")
    @Metadata(required = true, defaultValue = "clientAppID")
    private String clientAppID;

    @UriParam(description = "Reuester ID")
    @Metadata(required = true, defaultValue = "requesterID")
    private String requesterID;

    @UriParam(description = "JWT Token")
    @Metadata(required = true, defaultValue = "jwtToken")
    private String jwtToken;

    @UriParam(description = "A3 Token")
    @Metadata(required = true, defaultValue = "A3token")
    private String A3token;

    @UriParam(description = "Tenant Name")
    @Metadata(required = true, defaultValue = "tenantName")
    private String tenantName;

    @UriParam(description = "Form Name")
    @Metadata(required = true, defaultValue = "formName")
    private String formName;

    @UriParam(description = "Submission ID")
    @Metadata(required = true, defaultValue = "submissionId")
    private String submissionId;


    @UriParam(description = "Doc Save Path")
    @Metadata(required = true, defaultValue = "docSavePath")
    private String docSavePath;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApiHost() {
        return apiHost;
    }

    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public String getSubAppID() {
        return subAppID;
    }

    public void setSubAppID(String subAppID) {
        this.subAppID = subAppID;
    }

    public String getClientAppID() {
        return clientAppID;
    }

    public void setClientAppID(String clientAppID) {
        this.clientAppID = clientAppID;
    }

    public String getRequesterID() {
        return requesterID;
    }

    public void setRequesterID(String requesterID) {
        this.requesterID = requesterID;
    }

    public String getJwtToken() {
        return jwtToken;
    }

    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

    public String getA3token() {
        return A3token;
    }

    public void setA3token(String a3token) {
        A3token = a3token;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public String getDocSavePath() {
        return docSavePath;
    }

    public void setDocSavePath(String docSavePath) {
        this.docSavePath = docSavePath;
    }

}
