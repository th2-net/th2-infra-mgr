FROM openjdk:12-alpine
WORKDIR /home
RUN mkdir th2-schema-editor-be
WORKDIR /home/th2-schema-editor-be
RUN mkdir config
COPY *.jar ./application.jar
EXPOSE 8080
WORKDIR /home/th2-schema-editor-be/config
ENTRYPOINT ["java", "-jar", "/home/th2-schema-editor-be/application.jar"]
