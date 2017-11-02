package uk.co.kedos.aws.s3.email;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.sendgrid.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.StrSubstitutor;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class EmailService implements RequestHandler<S3EventNotification, Context> {

    public static final String DEFAULT_BODY = "<body>A new file has been uploaded to S3. <br/>Bucket: ${bucketName} <br/>Name: ${key} <br/>" +
            "<a href='${url}'>Click to Download</a> <br/>" +
            "The above link will be valid for ${expiryDays} days and will not work after ${expiryDate}. <br/>" +
            "If the file is less than ${maxFileSize} bytes it will be attached to this email.</body>";

    public static final String DEFAULT_SUBJECT = "File Uploaded to S3 - ${eventTimestamp}";
    public static final int DEFAULT_MAX_BYTES = 5242880;
    public static final int DEFAULT_EXPIRY_DAYS = 7;


    private LambdaLogger logger = s -> System.out.println(s);

    public Context handleRequest(S3EventNotification s3EventNotification, Context context) {
        logger = context.getLogger();

        logger.log("Received an event...");

        s3EventNotification.getRecords().forEach(record -> {
            sendEmail(record.getS3().getBucket().getName(), record.getS3().getObject().getKey(), record.getEventTime());
        });

        return context;
    }

    public void sendEmail(String bucketName, String key, DateTime eventTime) {
        final int expiryDays = NumberUtils.toInt(System.getenv("EXPIRY_DAYS"), DEFAULT_EXPIRY_DAYS);
        final int maxFileSize = NumberUtils.toInt(System.getenv("MAX_ATTACHMENT_FILE_SIZE"), DEFAULT_MAX_BYTES);

        //Get File from S3
        AmazonS3 s3client = new AmazonS3Client(new DefaultAWSCredentialsProviderChain());
        final S3Object file = s3client.getObject(new GetObjectRequest(bucketName, key));

        Email from = new Email(StringUtils.defaultIfBlank(System.getenv("FROM_EMAIL_OVERRIDE"), "no_reply@s3-upload.aws"));

        DateTime expiryDate = DateTime.now().plusDays(expiryDays);

        Map<String, String> substitutionParams = new HashMap<>();
        substitutionParams.put("eventTimestamp",  eventTime.toString());
        substitutionParams.put("bucketName", bucketName);
        substitutionParams.put("key", key);
        substitutionParams.put("url", s3client.generatePresignedUrl(bucketName, key, expiryDate.toDate()).toExternalForm());
        substitutionParams.put("expiryDate", expiryDate.toString());
        substitutionParams.put("expiryDays", String.valueOf(expiryDays));
        substitutionParams.put("maxFileSize", String.valueOf(maxFileSize));
        logger.log("Values: " + substitutionParams);

        String subject = StrSubstitutor.replace(StringUtils.defaultIfBlank(System.getenv("EMAIL_SUBJECT_OVERRIDE"), DEFAULT_SUBJECT), substitutionParams);

        Personalization addressee = new Personalization();

        addressee.addTo(new Email(StringUtils.defaultIfBlank(System.getenv("EMAIL_OVERRIDE"),  "test@test.com")));
        //add CCs
        String ccList = System.getenv("CC_LIST");
        if (StringUtils.isNotBlank(ccList)) {
            //convert to list
            Stream.of(StringUtils.split(ccList, ","))
                    .map(address -> new Email(address))
                    .forEach(email -> addressee.addCc(email));
        }

        Content content = new Content("text/html",
                StrSubstitutor.replace(StringUtils.defaultIfBlank(System.getenv("EMAIL_BODY"), DEFAULT_BODY), substitutionParams));

        Mail mail = new Mail();
        mail.setFrom(from);
        mail.setSubject(subject);
        mail.addContent(content);
        mail.addPersonalization(addressee);

        //add attachment
        if (file.getObjectMetadata().getContentLength() <= maxFileSize) {
            final Attachments.Builder builder = new Attachments.Builder(key, file.getObjectContent());
            mail.addAttachments(builder.build());
            logger.log("Attachment added");
        }

        SendGrid sg = new SendGrid(System.getenv("SENDGRID_API_KEY"));

        try {
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sg.api(request);

            logger.log(String.valueOf(response.getStatusCode()));
            logger.log(response.getBody());
            logger.log(String.valueOf(response.getHeaders()));
        } catch (IOException ex) {
            logger.log("IO Exception thrown. " + ex.getLocalizedMessage());
        }
    }
}
