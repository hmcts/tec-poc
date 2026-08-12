FROM eclipse-temurin:21-jre-alpine

WORKDIR /opt/app
COPY build/libs/tec-api-poc.jar ./tec-api-poc.jar

EXPOSE 8080
CMD ["java", "-jar", "tec-api-poc.jar"]
