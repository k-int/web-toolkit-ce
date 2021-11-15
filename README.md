# Web Toolkit Community Edition
Toolkit and value-add module for grails 4.x.x

Copyright (C) 2015-2020 Knowledge Integration

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# History

This project is the community edition of our grails app web-toolkit, which was created
to help support applications built on the grails platform. It is published here under an 
APL license so it can be reused in other open source projects.

# Installation

```
  compile "com.k_int.grails:web-toolkit-ce:5.0.0"
```
_NOTE:_ You may need to substitute the version number with the latest release.

# Other Notes

## Testing

A root level docker-compose file is provided that provisions the components needed for the integration tests to run. Test with

    docker-compose down -v   # To clear any previous data
    docker-compose up
    ./gradlew clean build

## MINIO/S3 File Storage

    MINIO File storage has been added. The following AppSetting entries control file uploads now
      * fileStorage.S3Endpoint  - The S3 endpoint - e.g. http://localhost:9000 for MINIO in the test system
      * fileStorage.S3AccessKey - S3 Access Key
      * fileStorage.S3SecretKey - S3 Secret Key
      * fileStorage.S3BucketName - S3 Bucket to use
      * fileStorage.S3ObjectPrefix - The path prefix this service should use - which will allow different contexts to share a single bucket if that is wanted

