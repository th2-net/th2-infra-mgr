FROM gradle:6.6-jdk11 AS build
ARG app_version=0.0.0
COPY ./ .
RUN gradle build -Prelease_version=${app_version}

RUN mkdir /home/service
RUN mkdir /home/service/repository
RUN mkdir /home/service/keys
RUN cp ./build/libs/*.jar /home/service/application.jar
RUN cp ./build/resources/main/config.yml /home/service/

FROM openjdk:12-alpine
COPY --from=gradle-image /home/service /home/service
WORKDIR /home/service/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/home/service/application.jar"]
