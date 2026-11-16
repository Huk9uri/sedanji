FROM eclipse-temurin:17-jdk AS build
WORKDIR /build

COPY gradlew build.gradle settings.gradle ./
COPY gradle/wrapper/ gradle/wrapper/
COPY src/main/ src/main/
RUN sed -i 's/\r$//' gradlew \
    && sh ./gradlew --no-daemon bootJar \
    && cp build/libs/*.jar /build/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/app.jar /app/app.jar
ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_SERVLET_SESSION_COOKIE_SECURE=true
USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
