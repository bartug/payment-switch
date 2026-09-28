# Build context proje kök dizini olmalı: docker build -f routing-service/Dockerfile .
FROM amazoncorretto:21-alpine AS builder

# Apple M4'te JDK 21 SVE nedeniyle SIGILL veriyor, x86'da bu flag görmezden gelinir
ENV JAVA_TOOL_OPTIONS="-XX:+IgnoreUnrecognizedVMOptions -XX:UseSVE=0"
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
COPY common/pom.xml common/
COPY payment-api/pom.xml payment-api/
COPY routing-service/pom.xml routing-service/
COPY bank-adapter/pom.xml bank-adapter/
COPY bank-simulator/pom.xml bank-simulator/
# Sadece bağımlılıkları indir (kaynak kodu henüz kopyalanmadığı için cache'lenecek)
RUN ./mvnw -pl routing-service -am dependency:go-offline -B
COPY common/src common/src
COPY routing-service/src routing-service/src

RUN ./mvnw -pl routing-service -am clean package -DskipTests -B && \
    cd routing-service && java -Djarmode=layertools -jar target/routing-service-*.jar extract

FROM amazoncorretto:21-alpine

RUN addgroup -S psgroup && adduser -S psadmin -G psgroup
USER psadmin

WORKDIR /app
COPY --from=builder /app/routing-service/spring-boot-loader/ ./
COPY --from=builder /app/routing-service/dependencies/ ./
COPY --from=builder /app/routing-service/snapshot-dependencies/ ./
COPY --from=builder /app/routing-service/application/ ./

EXPOSE 8082
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
