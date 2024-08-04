# Build native image from Spring Boot Application with GraalVM builder

## Overview

This section explains how to create a native image from a Spring Boot application using GraalVM's native image builder, and how to run this native image in a Docker container.

## Objective

In software architecture and microservice architecture design, we have to consider scalability, performance our application. Our application should start quickly scale and utilise the resource efficiently whenever number of request increase in our application. 

I am considering using Spring Boot Ahead of Time (AOT) Compilation with GraalVM to run the executable in containers, along with Java Virtual Threads (available in JDK 21 and higher).

- AOT Compilation is advantageous for scenarios where fast startup times and predictable performance are important, with the trade-off of less runtime adaptability.
- Containers are lightweight and use fewer resources compared to virtual machines (VMs) because they share the host OS kernel. Containers can start and stop much faster than VMs, enabling quicker scaling and deployment.
- Virtual threads can improve the performance of applications that handle a large number of concurrent tasks. This is especially beneficial for applications such as web servers, databases, and other I/O-bound systems. Virtual threads use fewer resources than traditional threads. They are managed by the runtime in a way that minimizes memory usage and CPU overhead.

In this architectural design decision, we gain benefits but must also consider the following implementation challenges and design considerations:

- **Virtual Threads**: We should avoid using virtual threads if our business logic is CPU-intensive, such as scenarios requiring extensive memory computation.
- **Ahead of Time (AOT) Compilation**: The AOT compiler may not properly handle reflection, proxy coding, or serialization. Additionally, GraalVM is a relatively new technology, posing challenges in creating native images from Spring Boot applications, and leading to increased build times.
- **Containers**: Containers provides numerous benefits but some of the challenges related area security, network, performance, CI/CD, etc. Some of the example are 
    - Containers can include vulnerabilities from base images or dependencies. 
    - Integrating containers into existing CI/CD pipelines can be challenging, requiring changes to build, test, and deployment processes.
    - Managing container orchestration platforms like Kubernetes can be complex and requires specialized knowledge.
    - Efficiently scaling containers up and down to handle varying loads without over-provisioning or under-provisioning resources.

**Spring Boot Application** 
To test this use case, I am building a Spring Boot application that exposes a REST endpoint at "/hello". I am using the following configuration, libraries, and tools:

- Spring Boot 3.2.8 with REST 
- Spring Boot AOT Compile
- Spring Boot GraalVM Native Image
- Maven 3.9.8 Build Tool
- Java 22

We need to add the following configuration in the POM XML file.

**Spring Boot Property configuration**
```xml
<properties>
    <java.version>22</java.version>
    <spring-native.version>0.12.1</spring-native.version>
</properties>
```
**Spring Boot AOT plugin configuration**
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
       <execution>
          <id>process-aot</id>
          <goals>
             <goal>process-aot</goal>
          </goals>
       </execution>
    </executions>
</plugin>
```

**GraalVM plugin configuration**
```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <configuration>
       <imageName>app-native-binary</imageName>
       <metadataRepository>
          <enabled>true</enabled>
       </metadataRepository>
       <buildArgs>
          <buildArg>--static --libc=musl</buildArg>
          <buildArg>-H:+ReportExceptionStackTraces</buildArg>
       </buildArgs>
       <mainClass>com.developerhelperhub.tutorial.springboot.tutorial.TutorialStartupPerformanceApplication</mainClass>
    </configuration>
    <executions>
       <execution>
          <id>add-reachability-metadata</id>
          <goals>
             <goal>add-reachability-metadata</goal>
          </goals>
       </execution>
    </executions>
</plugin>
```
- "mainClass": Configuring the mail class of spring boot application
- "imageName": Configuring the native image name 
- "buildArgs": Configuring the —libc=”msul”, We are configuring GraalVM to build the native image with the "libc musl" compatible library because we will be running this image on an Alpine Linux box. Musl is designed to be smaller and use less memory compared to other standard libraries, making it well-suited for resource-constrained environments.


## Build Binary and Create the Docker Image

We need to build the native image for the specific OS host and CPU architecture where the native image will run within the container.

We are using Alpine Linux for its small size, simplicity, and security to run our application in the container. To achieve this, we need to use the appropriate GraalVM configuration for building our application. The system requirements for Alpine are the operating system and CPU architecture.

- "Architecture": "amd64"
- "Os": "linux"
- C common library: “libc musl”

Following command we can use to inspect the “amd64/alpine” image
```shell
docker pull amd64/alpine # pull the image
    
docker image inspect amd64/alpine # inspect the image
```

We can use docker container to build the native image instead of setup the GraalVM and Java related configuration in our locally. I am using **“ghcr.io/graalvm/native-image-community:22-muslib”** docker image to build the native. 

Following command we can use to inspect the “**ghcr.io/graalvm/native-image-community:22-muslib**” image
```shell
docker pull ghcr.io/graalvm/native-image-community:22-muslib # pull the image

docker image inspect ghcr.io/graalvm/native-image-community:22-muslib # inspect the image
```

I am creating a build image to test and debug the container, ensuring that all configurations and services are installed correctly. This approach will help us quickly identify and resolve any issues.

Following steps are added in the docker file, the file name “DockerfileBuild” 
```shell
FROM ghcr.io/graalvm/native-image-community:22-muslib as build

# Install necessary tools
RUN microdnf install wget 
RUN microdnf install xz

# Install maven for build the spring boot application
RUN wget https://dlcdn.apache.org/maven/maven-3/3.9.8/binaries/apache-maven-3.9.8-bin.tar.gz
RUN tar xvf apache-maven-3.9.8-bin.tar.gz

# Set up the environment variables needed to run the Maven command.
ENV M2_HOME=/app/apache-maven-3.9.8
ENV M2=$M2_HOME/bin
ENV PATH=$M2:$PATH

# Install UPX (Ultimate Packer for eXecutables) to compress the executable binary and reduce its size.
RUN wget https://github.com/upx/upx/releases/download/v4.2.4/upx-4.2.4-amd64_linux.tar.xz
RUN tar xvf upx-4.2.4-amd64_linux.tar.xz

# Set up the environment variables required to run the UPX command.
ENV UPX_HOME=/app/upx-4.2.4-amd64_linux
ENV PATH=$UPX_HOME:$PATH

#Copy the spring boot source code into container
RUN mkdir -p /app/spring-boot-rest-api-app
COPY spring-boot-rest-api-app /app/spring-boot-rest-api-app

#Compile the native image
RUN cd /app/spring-boot-rest-api-app && mvn -Pnative native:compile

#Compressed binary file
RUN upx -7 -k /app/spring-boot-rest-api-app/target/app-native-binary
WORKDIR /app
ENTRYPOINT ["/bin/bash"]
```

I am using the UPX compression tool in the build process to reduce the image size, UPX will typically reduce the file size of programs and DLLs by around 50%-70%, thus reducing disk space, network load times, download times and other distribution and storage costs.

Use the following command to build the Docker image.
```shell
docker build --no-cache -f DockerfileBuild -t alpine-graalvm-build .
```

After the build is complete, the image size will be 1.85 GB.
```shell
REPOSITORY                               TAG         IMAGE ID       CREATED          SIZE
alpine-graalvm-build                     latest      81d23bc1bc99   36 seconds ago   1.85GB
```
We can verify the configuration and installation within the container before creating a smaller container inside the Alpine Linux box. The following command will allow us to enter the container:
```shell
docker run --rm -it --entrypoint /bin/bash alpine-graalvm-build

java --version #verify the java version
mvn --version #verify the maven version
upx --version #verify the upx version

ls /app/spring-boot-rest-api-app/target/app-native-binary #verify the binary available

/app/spring-boot-rest-api-app/target/app-native-binary #run the executable
```
We know that this native image includes all the dependencies necessary to run the binary standalone, without requiring any build-related tools such as GraalVM, Maven, UPX, or source code. We can use a Docker multi-stage build approach to copy the build file into our application image. By using multiple stages, you can separate the build environment from the runtime environment. This means only the necessary artifacts are included in the final image, significantly reducing its size.

Following steps are added in the docker file, the file name “DockerfileBuildAndCreateAlpineContainer” 
```shell
FROM ghcr.io/graalvm/native-image-community:22-muslib as build

# Install necessary tools
RUN microdnf install wget 
RUN microdnf install xz

# Install maven for build the spring boot application
RUN wget https://dlcdn.apache.org/maven/maven-3/3.9.8/binaries/apache-maven-3.9.8-bin.tar.gz
RUN tar xvf apache-maven-3.9.8-bin.tar.gz

# Set up the environment variables needed to run the Maven command.
ENV M2_HOME=/app/apache-maven-3.9.8
ENV M2=$M2_HOME/bin
ENV PATH=$M2:$PATH

# Install UPX (Ultimate Packer for eXecutables) to compress the executable binary and reduce its size.
RUN wget https://github.com/upx/upx/releases/download/v4.2.4/upx-4.2.4-amd64_linux.tar.xz
RUN tar xvf upx-4.2.4-amd64_linux.tar.xz

# Set up the environment variables required to run the UPX command.
ENV UPX_HOME=/app/upx-4.2.4-amd64_linux
ENV PATH=$UPX_HOME:$PATH

#Copy the spring boot source code into container
RUN mkdir -p /app/spring-boot-rest-api-app
COPY spring-boot-rest-api-app /app/spring-boot-rest-api-app

#Compile the native image
RUN cd /app/spring-boot-rest-api-app && mvn -Pnative native:compile

#Compressed binary file
RUN upx -7 -k /app/spring-boot-rest-api-app/target/app-native-binary
WORKDIR /app

#Second stage: Create the runtime image
FROM amd64/alpine

#Set the working directory
WORKDIR /app

#Copy the built application from the first stage
COPY --from=build /app/spring-boot-rest-api-app/target/app-native-binary .

#Expose port which our spring boot application is running
EXPOSE 8080 

#Command to run the application
ENTRYPOINT ["/app/app-native-binary"]
```

Use the following command to build the Docker image.
```shell
docker build -f DockerfileBuildAndCreateAlpineContainer -t alpine-graalvm .
```

After the build is complete, the image size of container will be 32.8MB.
```shell
REPOSITORY                               TAG         IMAGE ID       CREATED          SIZE
alpine-graalvm                           latest      79676c696920   11 seconds ago      32.8MB
```

We can verify the container.
```shell
docker run --rm -it --entrypoint sh alpine-graalvm

ls /app #verify the binary available

/app/app-native-binary #run the executable
```

The application startup time is just 0.074 seconds, whereas a typical Spring Boot application running on the JVM has a startup time of approximately 1.665 seconds. 
```shell
Started TutorialStartupPerformanceApplication in 0.074 seconds (process running for 0.075)
```

Following command can be use to run the docker container for running the application
```shell
docker run -d --name test-app -p 8080:8080 alpine-graalvm #run the container

curl http://localhost:8080/hello # checking the endpoints
```

## Spring boot and GraalVM references
- [Spring Boot Introduction GraalVM Native Images](http://https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html)
- [GraalVM documentation build Spring Boot Native Executable](https://www.graalvm.org/22.2/reference-manual/native-image/guides/build-spring-boot-app-into-native-executable/)
- [GraalVM Maven Plugin Documentation](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html)
- [Sample Spring Boot Application Docker Image Setup with GraalVM](http://ttps://www.baeldung.com/java-graalvm-docker-image)
- [Sample Native Images with Spring Boot and GraalVM](https://www.baeldung.com/spring-native-intro)
- [Spring Boot 3.2.8 GraalVM Native Images Documentation](https://docs.spring.io/spring-boot/docs/3.2.8-SNAPSHOT/reference/html/native-image.html#native-image.introducing-graalvm-native-images)
- [Spring Boot GraalVM UPX Tutorial Video](https://www.youtube.com/watch?v=x1nVchtOn90) 
- [Spring Boot Alpine Linux Docker Native Image Example](http://https://www.justinpolidori.it/posts/20230599_spring_native_static_image/)
## Docker and GraalVM References
- [GraalVM Containers Images](https://www.graalvm.org/latest/docs/getting-started/container-images/)
- [Docker Environment Variables](https://phoenixnap.com/kb/docker-environment-variables)
- [Maven Download](http://ttps://maven.apache.org/download.cgi) 
- [UPX Documentation](https://upx.github.io/)
- [UPX Releases](https://github.com/upx/upx/releases)
- [Docker Stop Container](https://www.cherryservers.com/blog/docker-stop-container)



