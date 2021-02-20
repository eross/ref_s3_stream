# Demonstration of Streaming from S3

There are three endpoints:

1. /memdownload -- brute force uses memory
1. /download -- reads 100 bytes at a time.  Needs very little memory.

Other suggestions can be found at:

[download a file from Spring boot rest service](https://stackoverflow.com/questions/35680932/download-a-file-from-spring-boot-rest-service)