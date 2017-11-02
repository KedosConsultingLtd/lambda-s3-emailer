# lambda-s3-emailer
Java Lambda function that consumes S3 Events and emails a link to the file (an optionally attaches the file) to specified addresses via SendGrid.

# Environment Properties
The following properties can be set within the Lambda environment variables to configure the behaviour.

| Variable Name | Type | Description | Supports Variables | Default |
| ------------- | ---- | ----------- | ------------------ | ------- |
| EXPIRY_DAYS   | Integer | Number of days that the S3 link should be valid for. | No | 7 |
| MAX_ATTACHMENT_FILE_SIZE | Integer | Maximum file size that will be sent as an attachment.  If the file is larger the email will only contain a link | No | 5242880 |
| FROM_EMAIL_OVERRIDE | String | Email address to be used as the from address | No | no_reply@s3-upload.aws | 
| EMAIL_SUBJECT_OVERRIDE | String | Subject of the email.  Allows variables to be substituted in | Yes | File Uploaded to S3 - ${eventTimestamp} |
| EMAIL_OVERRIDE | String | Body of the email (HTML).  Allows variables to be substituted in | Yes | ``` <body>A new file has been uploaded to S3. <br/>Bucket: ${bucketName} <br/>Name: ${key} <br/> <a href='${url}'>Click to Download</a> <br/> The above link will be valid for ${expiryDays} days and will not work after ${expiryDate}. <br/> If the file is less than ${maxFileSize} bytes it will be attached to this email.</body> </code> ``` |
| CC_LIST | String | Comma separated list of email addresses to be CC'd in to the email | No | (blank) |

The following values can be used as variables in the properties that support it.  Parameters should be in the following style: ${paramName}

| Name | Description | Output |
| ---- | ----------- | ------ |
| eventTimestamp | Time the event occurred in S3 | 2017-11-02T12:41:52.686Z |
| bucketName | Name of the bucket in which the change occurred | test-bucket-1 |
| key | File name of the item that was changed | test.pdf |
| url | Presigned URL allowing GET access to the file for a configured amount of time | https://test-bucket-1.s3.amazonaws.com/test.pdf?x-amz-security-token=... |
| expiryDate | Date when the URL will expire | 2017-11-12T12:41:52.686Z |
| expiryDays | Number of days the link will be active for | 7 |
| maxFileSize | Size of the largest file that will be allowed as an attachment | 5242880 |
