package com.k_int.web.toolkit.files

import io.minio.MinioClient
import io.minio.credentials.IamAwsProvider
import io.minio.credentials.Provider
import io.minio.credentials.StaticProvider
import spock.lang.Specification
import spock.lang.Unroll

class FileUploadServiceSpec extends Specification {

  // ---- Provider selection tests ----

  @Unroll
  void 'resolveCredentialsProvider returns #expectedType when accessKey=#accessKey, secretKey=#secretKey'() {
    when:
      Provider provider = FileUploadService.resolveCredentialsProvider(accessKey, secretKey)

    then:
      expectedType.isInstance(provider)

    where:
      accessKey        | secretKey        || expectedType
      'access'         | 'secret'         || StaticProvider      // both present → static
      null             | null             || IamAwsProvider       // both null → IAM
      null             | 'secret'         || IamAwsProvider       // access null → IAM
      'access'         | null             || IamAwsProvider       // secret null → IAM
      ''               | 'secret'         || IamAwsProvider       // access empty (Groovy falsy) → IAM
      'access'         | ''               || IamAwsProvider       // secret empty (Groovy falsy) → IAM
  }

  // ---- MinioClient construction tests ----

  void 'buildMinioClient creates a non-null client with StaticProvider'() {
    given:
      Provider provider = new StaticProvider('access', 'secret', null)

    when:
      MinioClient client = FileUploadService.buildMinioClient('http://localhost:9000', provider)

    then:
      client != null
  }

  void 'buildMinioClient creates a non-null client with IamAwsProvider'() {
    given:
      Provider provider = new IamAwsProvider(null, null)

    when:
      MinioClient client = FileUploadService.buildMinioClient('http://localhost:9000', provider)

    then:
      client != null
  }
}
