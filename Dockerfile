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
COPY --from=build /home/service /home/service
WORKDIR /home/service/
EXPOSE 8080
ENTRYPOINT ["java", "-Dlog4j.configuration=file:/home/service/log4j.properties", "-jar", "/home/service/application.jar"]
