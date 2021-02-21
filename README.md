# Demonstration of Streaming from S3
##Endpoints
There are three endpoints:

1. /memdownload?image=file -- brute force uses memory
1. /download?image=file -- reads 100 bytes at a time.  Needs very little memory.
1. /s3download?image=file -- Downloads files from s3 bucket defined in
   [application.properties](src/main/resources/application.properties)

Note that s3download is very poor performance.  It is using small buffer sizes and logging after each buffer.
It is also urging the jvm to do a garbage collection after every buffer.  Needless to say, delete the logging and the gc while
increasing the buffer size and performance should be much better.  Not that will logging the download the free memory does not
change much.  This can download any file as long as the timeout for downloads is not exceeded.  Again see
[application.properties](src/main/resources/application.properties) for the tuning parameter.
## References


* [download a file from Spring boot rest service](https://stackoverflow.com/questions/35680932/download-a-file-from-spring-boot-rest-service)
* [Async task executor](https://medium.com/swlh/streaming-data-with-spring-boot-restful-web-service-87522511c071)  
* [AWS S3 Java SDK Examples](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-objects.html)