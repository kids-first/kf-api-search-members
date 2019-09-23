FROM openjdk:11

WORKDIR /app

COPY target/scala-2.12/kf-search-members.jar .

ENTRYPOINT java -Dconfig.resource=dev.conf -Dplay.http.secret.key=ad31779d4ee49d5ad5162bf1429c32e2e9933f3b -jar kf-search-members.jar

