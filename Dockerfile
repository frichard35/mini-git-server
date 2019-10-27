FROM maven:3.6-jdk-8-alpine AS builder

RUN apk add --update --no-cache git

WORKDIR /app
COPY pom.xml .
RUN mvn -e -B dependency:resolve
COPY src ./src
RUN mvn -e -B package

FROM openjdk:8-jre-slim
USER 1035:1035
COPY --chown=1035:1035 --from=builder /app/target/mini-git-server-jar-with-dependencies.jar /app/mini-git-server.jar
RUN mkdir /app/config && mkdir /app/repos
ENTRYPOINT ["java","-Xmx32m","-Djava.security.egd=file:/dev/./urandom","-jar","/app/mini-git-server.jar"]
WORKDIR /app
VOLUME /tmp
VOLUME /app/config
VOLUME /app/repos
EXPOSE 8080