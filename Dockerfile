FROM alpine:latest as builder
RUN apk update \
  && apk add --no-cache \
    maven \
    openjdk17
COPY . /build/
WORKDIR /build/
RUN mvn clean package

FROM alpine:latest
RUN apk update \
  && apk add --no-cache \
    openjdk17-jre \
    curl \
  && echo "config-server:x:1000:1000:,,,:/home/config-server:/bin/sh" >>/etc/passwd \
  && echo "config-server:x:1000:" >>/etc/group \
  && mkdir -p /home/config-server \
  && chown 1000:1000 /home/config-server \
  && rm -rf /tmp/* /var/cache/* \
  && mkdir -p /var/cache/apk

LABEL maintainer="sa4zet <light.config.server@sa4zet.win>"
EXPOSE 5454
WORKDIR /app/

HEALTHCHECK \
  --interval=3m \
  --retries=2 \
  --timeout=2s \
  CMD if [ "ok" == $(curl --silent --show-error --max-time 3 "http://127.0.0.1:5454/health_check") ]; then exit 0; else exit 1; fi;

COPY --from=builder /build/target/*-jar-with-dependencies.jar /app/light.config.server.jar
USER config-server
ENTRYPOINT [ "java", "-jar", "/app/light.config.server.jar" ]
