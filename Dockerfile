FROM openjdk:12-jdk-alpine
VOLUME /tmp
COPY build/libs/* app.jar
EXPOSE 8010
ENTRYPOINT ["java", "-jar", "/app.jar"]