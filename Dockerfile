FROM openjdk:11

WORKDIR /app

COPY target/scala-2.12/kf-search-members.jar .
COPY scripts/start-up.sh /

EXPOSE 80

ENTRYPOINT /start-up.sh

