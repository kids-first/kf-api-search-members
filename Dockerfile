FROM openjdk:11

WORKDIR /app

COPY target/scala-2.12/kf-search-members.jar .

EXPOSE 80

CMD java -Dhttp.port=80 -jar /app/kf-search-members.jar
