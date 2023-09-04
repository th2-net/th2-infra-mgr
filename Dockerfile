FROM gradle:7.6-jdk17 AS build
ARG app_version=0.0.0
COPY ./ .
RUN mkdir -p /home/service/repository /home/service/keys
RUN gradle build -Prelease_version=${app_version} && \
    cp ./build/libs/*.jar /home/service/application.jar

FROM eclipse-temurin:17-alpine
#TODO: git app is installed for test
RUN apk add --no-cache git

WORKDIR /home/service/
COPY --from=build /home/service .
RUN chgrp -R 0 /home/service && \
    chmod -R g=u /home/service

EXPOSE 8080
ENTRYPOINT ["java" \
    , "-Dlog4j2.configurationFile=file:/home/service/config/log4j2.properties" \
    , "-Dinframgr.config.dir=config" \
    , "-jar" \
    , "/home/service/application.jar"]
