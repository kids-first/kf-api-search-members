FROM openjdk:11

WORKDIR /app

COPY target/scala-2.12/kf-search-members.jar .

EXPOSE 80

ENTRYPOINT java -Dhttp.port=80 -jar kf-search-members.jar

