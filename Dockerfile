FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY ./build/libs/api-gateway-1.0.0.jar ./api-gateway-1.0.0.jar
EXPOSE 9020
ENTRYPOINT ["java","-jar","api-gateway-1.0.0.jar"]