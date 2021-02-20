package com.rossmbox.filedownload.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.internal.util.Mimetype;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.S3Response;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class DownloadController {

    @Value("${filedownload.s3bucket}")
    private String s3Bucket;

    @Value("${filedownload.s3region}")
    private String s3Region;


    Logger logger = LoggerFactory.getLogger(DownloadController.class);
    private static final String EXTENSION = ".jpg";
    private static final String SERVER_LOCATION = "/server/images";

    // This one will consume 2x the size of the file in memory.  For large files it will break the heap limit.
    // NOT RECOMMENDED
    @RequestMapping(path = "/memdownload", method = RequestMethod.GET)
    public ResponseEntity<Resource> download(@RequestParam("image") String image) throws IOException {
        File file = new ClassPathResource(SERVER_LOCATION + File.separator + image).getFile();

        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=img.jpg");
        header.add("Cache-Control", "no-cache, no-store, must-revalidate");
        header.add("Pragma", "no-cache");
        header.add("Expires", "0");

        Path path = Paths.get(file.getAbsolutePath());
        ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(path));

        return ResponseEntity.ok()
                .headers(header)
                .contentLength(file.length())
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(resource);

    }

    // This approach reads files in 100 bytes at a time.  The buffer size normally should be longer,
    // but it was kept small to make it easier to show how the data is broken up.
    @RequestMapping(path = "/download", method = RequestMethod.GET)
    public StreamingResponseBody downloadFile(HttpServletResponse response, @RequestParam("image") String image) throws IOException {
        final int BUFFER_SIZE = 100;
        File file = new ClassPathResource(SERVER_LOCATION + File.separator + image).getFile();
        Path path = file.toPath();
        String mimeType = Files.probeContentType(path);
        logger.info("mimeType for " + path + " is " + mimeType);


        response.setContentType(mimeType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + file.getName());

        return outputStream -> {
            int bytesRead;
            byte[] buffer = new byte[BUFFER_SIZE];
            InputStream inputStream = new FileInputStream(file);
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        };
    }

    @RequestMapping(path = "/s3download", method = RequestMethod.GET)
    public StreamingResponseBody s3DownloadFile(HttpServletResponse response, @RequestParam("image") String image) throws IOException {
        File file = new File(image);
        Path path = file.toPath();
        ProfileCredentialsProvider profileCredentialsProvider = ProfileCredentialsProvider.create();
        S3Client s3Client = S3Client.builder()
                .region(Region.of(s3Region))
                .credentialsProvider(profileCredentialsProvider)
                .build();
        logger.info("Retrieving s3://" + s3Bucket + "/" + file.toPath());

        logger.info(file.toPath().toString());
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Bucket)
                .key(file.toPath().toString())
                .build();

        // This code for detecting mime type will download the whole file into local file space.  Bad idea.
        //GetObjectResponse objectResponse = s3Client.getObject(getObjectRequest, file.toPath());
        //String mimeType = objectResponse.contentType();

        String mimeType = Mimetype.MIMETYPE_OCTET_STREAM;
        logger.info("mimeType for " + path + " is " + mimeType);

        ResponseInputStream<GetObjectResponse> s3InputStream = s3Client.getObject(getObjectRequest);


        // This is where the data starts to transfer.
        response.setContentType(mimeType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + file.getName());
        profileCredentialsProvider.close();

        return outputStream -> {
            final int BUFFER_SIZE=1000;
            InputStream inputStream = new BufferedInputStream(s3InputStream, BUFFER_SIZE);
            int bytesRead;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                logger.info("Outputting: "+String.valueOf(bytesRead));
                logger.info("Memory free: "+String.valueOf(Runtime.getRuntime().freeMemory()));
                System.gc();  // Be aggressive on gc to maximize free memory at all times.  Not recommended for production code.
                outputStream.write(buffer, 0, bytesRead);
            }
        };
    }
}
